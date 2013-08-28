package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.StringValuePresenter;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import org.jetbrains.annotations.NotNull;

public final class XVariableValuePresenter extends StringValuePresenter {
  private final String separator;

  public XVariableValuePresenter(@NotNull String separator) {
    super(-1, null);

    this.separator = separator;
  }

  @Override
  public void appendSeparator(@NotNull ColoredTextContainer text) {
    text.append(separator, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  @Override
  public void append(@NotNull String value, @NotNull ColoredTextContainer text, boolean changed) {
    doAppend(value, text, changed ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}