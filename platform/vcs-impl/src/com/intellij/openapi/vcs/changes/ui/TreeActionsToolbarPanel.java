// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class TreeActionsToolbarPanel extends JPanel {
  public TreeActionsToolbarPanel(@NotNull ActionToolbar toolbar, @NotNull ChangesTree tree) {
    this(toolbar.getComponent(), tree);
  }

  public TreeActionsToolbarPanel(@NotNull Component toolbarComponent, @NotNull ChangesTree tree) {
    super(new BorderLayout());

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(tree.createExpandAllAction(true));
    group.add(tree.createCollapseAllAction(true));

    ActionToolbar additionalToolbar = ActionManager.getInstance().createActionToolbar("TreeActionsToolbar", group, true);
    additionalToolbar.setTargetComponent(this);
    additionalToolbar.setReservePlaceAutoPopupIcon(false);

    add(toolbarComponent, BorderLayout.CENTER);
    add(additionalToolbar.getComponent(), BorderLayout.EAST);
  }
}
