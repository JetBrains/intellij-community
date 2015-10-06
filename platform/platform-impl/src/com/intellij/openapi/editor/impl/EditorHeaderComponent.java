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
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class EditorHeaderComponent extends JPanel {
  public EditorHeaderComponent() {
    super(new BorderLayout(0, 0));
    boolean topBorderRequired = !SystemInfo.isMac && UISettings.getInstance().EDITOR_TAB_PLACEMENT != SwingConstants.TOP &&
                                !(UISettings.getInstance().SHOW_MAIN_TOOLBAR && UISettings.getInstance().SHOW_NAVIGATION_BAR);
    setBorder(new CustomLineBorder(JBColor.border(), topBorderRequired ? 1 : 0, 0, 1, 0));
  }

  @Override
  public void paint(@NotNull Graphics g) {
    UISettings.setupAntialiasing(g);
    super.paint(g);
  }
}
