// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class TreeActionsToolbarPanel extends JPanel {
  public TreeActionsToolbarPanel(@NotNull ActionToolbar toolbar, @NotNull ChangesTree tree) {
    this(toolbar.getComponent(), tree);
    toolbar.setTargetComponent(tree);
  }

  public TreeActionsToolbarPanel(@NotNull ActionToolbar toolbar, @NotNull ActionGroup group, @Nullable JComponent targetComponent) {
    this(toolbar.getComponent(), group, targetComponent);
    if (targetComponent != null) toolbar.setTargetComponent(targetComponent);
  }

  public TreeActionsToolbarPanel(@NotNull Component toolbarComponent, @NotNull ChangesTree tree) {
    this(toolbarComponent, new DefaultActionGroup(createTreeActions(tree)), tree);
  }

  public TreeActionsToolbarPanel(@NotNull Component toolbarComponent, @NotNull ActionGroup group, @Nullable JComponent targetComponent) {
    super(new BorderLayout());

    ActionToolbar additionalToolbar = ActionManager.getInstance().createActionToolbar("TreeActionsToolbar", group, true);
    additionalToolbar.setTargetComponent(ObjectUtils.notNull(targetComponent, this));
    additionalToolbar.setReservePlaceAutoPopupIcon(false);

    add(toolbarComponent, BorderLayout.CENTER);
    add(additionalToolbar.getComponent(), BorderLayout.EAST);
  }

  @NotNull
  public static List<AnAction> createTreeActions(@NotNull ChangesTree tree) {
    return Arrays.asList(tree.createExpandAllAction(true),
                         tree.createCollapseAllAction(true));
  }
}
