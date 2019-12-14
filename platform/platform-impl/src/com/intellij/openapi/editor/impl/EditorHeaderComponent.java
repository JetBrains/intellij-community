// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    boolean topBorderRequired = uiSettings.getShowNavigationBar() || uiSettings.getShowMainToolbar();
    topBorderRequired = uiSettings.getEditorTabPlacement() == 0 && topBorderRequired;
    setBorder(new CustomLineBorder(JBColor.border(), topBorderRequired ? 1 : 0, 0, 1, 0));
  }
}
