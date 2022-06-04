// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class EditorHeaderComponent extends JPanel implements UISettingsListener {
  public EditorHeaderComponent() {
    super(new BorderLayout(0, 0));
    setBorder(new CustomLineBorder(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 0, 1, 0));
  }

  @Override
  public void paint(@NotNull Graphics g) {
    UISettings.setupAntialiasing(g);
    super.paint(g);
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
  }
}
