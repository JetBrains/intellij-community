/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework.utils.parameterInfo;

import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Maxim.Mossienko
 */
public class MockParameterInfoUIContext<T extends PsiElement> implements ParameterInfoUIContext {
  private boolean isUIComponentEnabled;
  private boolean isTextRaw;
  private String text;
  private int highlightStart;
  private int highlightEnd;
  private boolean isTextDisabled;
  private boolean isTextStrikeout;
  private boolean isTextDisabledBeforeHighlight;
  @Nullable
  private Color textBackground;
  private final T myFunction;
  private int parameterIndex;

  public MockParameterInfoUIContext(final T function) {
    myFunction = function;
  }

  @Override
  public String setupUIComponentPresentation(final String _text, final int highlightStartOffset, final int highlightEndOffset,
                                             final boolean isDisabled, final boolean strikeout, final boolean isDisabledBeforeHighlight,
                                             final Color background) {
    isTextRaw = false;
    text = _text;
    highlightStart = highlightStartOffset;
    highlightEnd = highlightEndOffset;
    isTextDisabled = isDisabled;
    isTextStrikeout = strikeout;
    isTextDisabledBeforeHighlight = isDisabledBeforeHighlight;
    textBackground = background;
    return _text;
  }

  @Override
  public void setupRawUIComponentPresentation(String htmlText) {
    isTextRaw = true;
    text = htmlText;
  }

  @Override
  public boolean isUIComponentEnabled() {
    return isUIComponentEnabled;
  }

  @Override
  public void setUIComponentEnabled(final boolean _enabled) {
    isUIComponentEnabled = _enabled;
  }

  @Override
  public int getCurrentParameterIndex() {
    return parameterIndex;
  }

  public void setCurrentParameterIndex(final int parameterIndex) {
    this.parameterIndex = parameterIndex;
  }

  @Override
  public PsiElement getParameterOwner() {
    return myFunction;
  }

  @Override
  public boolean isSingleOverload() {
    return false;
  }

  @Override
  public boolean isSingleParameterInfo() {
    return false;
  }

  @Override
  public Color getDefaultParameterColor() {
    return null;
  }

  public String getText() {
    return text;
  }

  public int getHighlightStart() {
    return highlightStart;
  }

  public int getHighlightEnd() {
    return highlightEnd;
  }

  public boolean isTextDisabled() {
    return isTextDisabled;
  }

  public boolean isTextRaw() {
    return isTextRaw;
  }

  public boolean isTextStrikeout() {
    return isTextStrikeout;
  }

  public boolean isTextDisabledBeforeHighlight() {
    return isTextDisabledBeforeHighlight;
  }

  @Nullable
  public Color getTextBackground() {
    return textBackground;
  }
}
