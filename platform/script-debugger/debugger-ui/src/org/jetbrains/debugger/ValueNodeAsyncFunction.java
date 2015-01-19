package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncFunction;

public abstract class ValueNodeAsyncFunction<PARAM, RESULT> implements AsyncFunction<PARAM, RESULT>, org.jetbrains.concurrency.Obsolescent {
  private final com.intellij.xdebugger.Obsolescent node;

  protected ValueNodeAsyncFunction(@NotNull com.intellij.xdebugger.Obsolescent node) {
    this.node = node;
  }

  @Override
  public final boolean isObsolete() {
    return node.isObsolete();
  }
}
