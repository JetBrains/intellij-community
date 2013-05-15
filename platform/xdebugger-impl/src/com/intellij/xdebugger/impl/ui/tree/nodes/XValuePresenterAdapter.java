package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.NotNullFunction;
import com.intellij.xdebugger.frame.XValuePresenter;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;

public class XValuePresenterAdapter implements XValuePresenter {
  private final NotNullFunction<String, String> valuePresenter;

  public XValuePresenterAdapter(NotNullFunction<String, String> valuePresenter) {
    this.valuePresenter = valuePresenter;
  }

  @Override
  public void append(String value, SimpleColoredText text, boolean changed) {
    text.append(valuePresenter.fun(value), changed ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}