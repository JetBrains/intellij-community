// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.tree;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class ExpandAll extends DumbAwareAction {

  public ExpandAll() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    JTree tree = ComponentUtil.getParentOfType((Class<? extends JTree>)JTree.class, e.getData(PlatformDataKeys.CONTEXT_COMPONENT));
    if (tree != null) {
      TreeUtil.expandAll(tree);
    }
  }
}
