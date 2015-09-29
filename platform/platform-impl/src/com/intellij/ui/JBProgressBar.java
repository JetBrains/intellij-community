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
package com.intellij.ui;

import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ProgressBarUI;
import java.awt.*;

public class JBProgressBar extends JProgressBar {
  private static final int NATIVE_LAF_HEIGHT = 12;

  @Override
  public void setUI(ProgressBarUI ui) {
    boolean nativeLaf = UIUtil.isUnderWindowsLookAndFeel() || SystemInfo.isMac || UIUtil.isUnderGTKLookAndFeel();
    if (nativeLaf) {
      ui = new DarculaProgressBarUI();
      if (UIUtil.isUnderGTKLookAndFeel()) {
        setBorder(JBUI.Borders.empty());
      }
    }
    super.setUI(ui);
    if (nativeLaf) {
      setPreferredSize(new Dimension(getPreferredSize().width, JBUI.scale(NATIVE_LAF_HEIGHT)));
    }
  }
}
