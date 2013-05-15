package com.intellij.xdebugger.frame;

import com.intellij.ui.SimpleColoredText;

public interface XValuePresenter {
  void append(String value, SimpleColoredText text, boolean changed);
}