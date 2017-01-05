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
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.border.CustomLineBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class EditorHeaderComponent extends JPanel implements UISettingsListener {
  public EditorHeaderComponent() {
    super(new BorderLayout(0, 0));
    uiSettingsChanged(UISettings.getInstance());
  }

  @Override
  public void paint(@NotNull Graphics g) {
    UISettings.setupAntialiasing(g);
    super.paint(g);
  }

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    boolean topBorderRequired = uiSettings.EDITOR_TAB_PLACEMENT != SwingConstants.TOP &&
                                (uiSettings.SHOW_NAVIGATION_BAR || uiSettings.SHOW_MAIN_TOOLBAR);
    setBorder(new CustomLineBorder(JBColor.border(), topBorderRequired ? 1 : 0, 0, 1, 0));
  }
}
