package org.jetbrains.concurrency;

import com.intellij.openapi.util.Getter;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

class DonePromise<T> extends Promise<T> implements Getter<T> {
  private final T result;

  public DonePromise(T result) {
    this.result = result;
  }

  @NotNull
  @Override
  public Promise<T> done(@NotNull Consumer<T> done) {
    if (!AsyncPromise.isObsolete(done)) {
      done.consume(result);
    }
    return this;
  }

  @NotNull
  @Override
  public Promise<T> processed(@NotNull AsyncPromise<T> fulfilled) {
    fulfilled.setResult(result);
    return this;
  }

  @Override
  public void processed(@NotNull Consumer<T> processed) {
    done(processed);
  }

  @NotNull
  @Override
  public Promise<T> rejected(@NotNull Consumer<Throwable> rejected) {
    return this;
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<T, SUB_RESULT> done) {
    if (done instanceof Obsolescent && ((Obsolescent)done).isObsolete()) {
      return Promise.reject("obsolete");
    }
    else {
      return Promise.resolve(done.fun(result));
    }
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull AsyncFunction<T, SUB_RESULT> done) {
    return done.fun(result);
  }

  @NotNull
  @Override
  public State getState() {
    return State.FULFILLED;
  }

  @Override
  public T get() {
    return result;
  }

  @Override
  void notify(@NotNull AsyncPromise<T> child) {
    child.setResult(result);
  }
}