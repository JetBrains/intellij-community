package org.jetbrains.debugger;

import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;

interface ValueGroupFactory<T> {
  XValueGroup create(@NotNull T data, int start, int end, @NotNull VariableContext context);
}