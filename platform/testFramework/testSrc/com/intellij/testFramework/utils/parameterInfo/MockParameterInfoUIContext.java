package com.intellij.testFramework.utils.parameterInfo;

import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.psi.PsiElement;

import java.awt.*;

/**
* Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: 01.10.2009
* Time: 21:43:55
* To change this template use File | Settings | File Templates.
*/
public class MockParameterInfoUIContext<T extends PsiElement> implements ParameterInfoUIContext {
  private boolean enabled;
  private String text;
  private int highlightStart;
  private final T myFunction;
  private int parameterIndex;

  public MockParameterInfoUIContext(final T function) {
    myFunction = function;
  }

  public void setupUIComponentPresentation(final String _text, final int highlightStartOffset, final int highlightEndOffset,
                                           final boolean isDisabled, final boolean strikeout, final boolean isDisabledBeforeHighlight,
                                           final Color background) {
    text = _text;
    highlightStart = highlightStartOffset;
  }

  public boolean isUIComponentEnabled() {
    return enabled;
  }

  public void setUIComponentEnabled(final boolean _enabled) {
    enabled = _enabled;
  }

  public int getCurrentParameterIndex() {
    return parameterIndex;
  }

  public void setCurrentParameterIndex(final int parameterIndex) {
    this.parameterIndex = parameterIndex;
  }

  public PsiElement getParameterOwner() {
    return myFunction;
  }

  public Color getDefaultParameterColor() {
    return null;
  }

  public String getText() {
    return text;
  }

  public int getHighlightStart() {
    return highlightStart;
  }
}
