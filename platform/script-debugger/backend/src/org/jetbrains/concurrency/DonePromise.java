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
    done.consume(result);
    return this;
  }

  @NotNull
  @Override
  public Promise<T> rejected(@NotNull Consumer<String> rejected) {
    return this;
  }

  @Override
  public void processed(@NotNull Consumer<T> processed) {
    processed.consume(result);
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull Function<T, SUB_RESULT> done) {
    //noinspection unchecked
    return (Promise<SUB_RESULT>)this;
  }

  @NotNull
  @Override
  public <SUB_RESULT> Promise<SUB_RESULT> then(@NotNull AsyncFunction<T, SUB_RESULT> done) {
    //noinspection unchecked
    return (Promise<SUB_RESULT>)this;
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
}