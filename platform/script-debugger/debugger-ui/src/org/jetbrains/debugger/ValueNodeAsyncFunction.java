package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncFunction;
import org.jetbrains.concurrency.Obsolescent;

public abstract class ValueNodeAsyncFunction<PARAM, RESULT> implements AsyncFunction<PARAM, RESULT>, org.jetbrains.concurrency.Obsolescent {
  private final Obsolescent node;

  protected ValueNodeAsyncFunction(@NotNull Obsolescent node) {
    this.node = node;
  }

  @Override
  public final boolean isObsolete() {
    return node.isObsolete();
  }
}
