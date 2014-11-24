package org.jetbrains.concurrency;

import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

class RejectedPromise<T> extends Promise<T> {
  private final String error;

  public RejectedPromise(String error) {
    this.error = error;
  }

  @NotNull
  @Override
  public Promise<T> done(@NotNull Consumer<T> done) {
    return this;
  }

  @NotNull
  @Override
  public Promise<T> rejected(@NotNull Consumer<String> rejected) {
    rejected.consume(error);
    return this;
  }

  @Override
  public void processed(@NotNull Consumer<T> processed) {
    processed.consume(null);
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
    return State.REJECTED;
  }
}