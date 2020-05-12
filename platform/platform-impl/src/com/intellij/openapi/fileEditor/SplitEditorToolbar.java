// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SplitEditorToolbar extends EditorHeaderComponent {

  private final ActionToolbar myRightToolbar;

  public SplitEditorToolbar(@Nullable ActionToolbar leftToolbar, @NotNull ActionToolbar rightToolbar) {
    super();
    setLayout(new GridBagLayout());
    myRightToolbar = rightToolbar;

    if (leftToolbar != null) {
      add(leftToolbar.getComponent());
    }

    final JPanel centerPanel = new JPanel(new BorderLayout());
    add(centerPanel, new GridBagConstraints(2, 0, 1, 1, 1.0, 1.0,
                                            GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));

    add(myRightToolbar.getComponent());

    if (leftToolbar != null) leftToolbar.updateActionsImmediately();
    rightToolbar.updateActionsImmediately();
  }

  public void refresh() {
    myRightToolbar.updateActionsImmediately();
  }
}
