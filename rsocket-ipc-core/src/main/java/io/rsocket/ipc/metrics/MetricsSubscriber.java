/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.ipc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

public class MetricsSubscriber<T> extends AtomicBoolean implements Subscription, CoreSubscriber<T> {
  private final CoreSubscriber<? super T> actual;
  private final Counter next, complete, error, cancelled;
  private final Timer timer;

  private Subscription s;
  private long start;

  MetricsSubscriber(
      CoreSubscriber<? super T> actual,
      Counter next,
      Counter complete,
      Counter error,
      Counter cancelled,
      Timer timer) {
    this.actual = actual;
    this.next = next;
    this.complete = complete;
    this.error = error;
    this.cancelled = cancelled;
    this.timer = timer;
  }

  @Override
  public void onSubscribe(Subscription s) {
    if (Operators.validate(this.s, s)) {
      this.s = s;
      this.start = System.nanoTime();

      actual.onSubscribe(this);
    }
  }

  @Override
  public void onNext(T t) {
    next.increment();
    actual.onNext(t);
  }

  @Override
  public void onError(Throwable t) {
    if (compareAndSet(false, true)) {
      error.increment();
      timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
    actual.onError(t);
  }

  @Override
  public void onComplete() {
    if (compareAndSet(false, true)) {
      complete.increment();
      timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
    actual.onComplete();
  }

  @Override
  public void request(long n) {
    s.request(n);
  }

  @Override
  public void cancel() {
    if (compareAndSet(false, true)) {
      cancelled.increment();
      timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
    s.cancel();
  }

  @Override
  public Context currentContext() {
    return actual.currentContext();
  }
}
