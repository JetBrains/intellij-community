/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.stripe;

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.ButtonlessScrollBarUI.ThumbPainter;
import com.intellij.util.ui.UIUtil;

import javax.swing.JScrollBar;

/**
 * @author Sergey.Malenkov
 */
final class TranslucencyThumbPainter extends ThumbPainter implements Disposable {
  private final ErrorStripePainter myPainter;
  private final JScrollBar myScrollBar;

  TranslucencyThumbPainter(ErrorStripePainter painter, JScrollBar bar) {
    super(bar);
    myPainter = painter;
    myScrollBar = bar;
    UIUtil.putClientProperty(myScrollBar, ButtonlessScrollBarUI.MAXI_THUMB, this);
  }

  @Override
  public void dispose() {
    UIUtil.putClientProperty(myScrollBar, ButtonlessScrollBarUI.MAXI_THUMB, null);
  }

  protected float getAlpha() {
    return .6f;
  }

  protected int getColorOffset(Integer value) {
    return 11 + super.getColorOffset(value);
  }

  @Override
  protected int getBorderOffset() {
    if (myPainter instanceof ExtraErrorStripePainter) {
      ExtraErrorStripePainter painter = (ExtraErrorStripePainter)myPainter;
      return painter.getMinimalThickness();
    }
    return super.getBorderOffset();
  }
}
