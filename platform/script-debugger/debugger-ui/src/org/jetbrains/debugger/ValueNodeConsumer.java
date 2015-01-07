package org.jetbrains.debugger;

import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.ObsolescentConsumer;

public abstract class ValueNodeConsumer<T> implements ObsolescentConsumer<T> {
  private final Obsolescent node;

  protected ValueNodeConsumer(@NotNull Obsolescent node) {
    this.node = node;
  }

  @Override
  public final boolean isObsolete() {
    return node.isObsolete();
  }
}
