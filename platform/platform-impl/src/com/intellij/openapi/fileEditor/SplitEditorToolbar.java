// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SplitEditorToolbar extends JPanel {

  private final ActionToolbar myRightToolbar;

  public SplitEditorToolbar(@Nullable ActionToolbar leftToolbar, @NotNull ActionToolbar rightToolbar) {
    super(new GridBagLayout());
    myRightToolbar = rightToolbar;

    if (leftToolbar != null) {
      add(leftToolbar.getComponent());
    }

    final JPanel centerPanel = new JPanel(new BorderLayout());
    add(centerPanel, new GridBagConstraints(2, 0, 1, 1, 1.0, 1.0,
                                            GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));

    add(myRightToolbar.getComponent());

    setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIUtil.CONTRAST_BORDER_COLOR));

    if (leftToolbar != null) leftToolbar.updateActionsImmediately();
    rightToolbar.updateActionsImmediately();
  }

  public void refresh() {
    myRightToolbar.updateActionsImmediately();
  }
}
