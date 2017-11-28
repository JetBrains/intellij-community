// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.tree.TreeUtil;

/**
 * @author Max Medvedev
 */
public class ExpandAllAction extends AnAction {

  public ExpandAllAction() {
    super("Expand all", "Collapse all", AllIcons.Actions.Expandall);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    final TreeTableTree tree = DynamicToolWindowWrapper.getInstance(project).getTreeTable().getTree();
    TreeUtil.expandAll(tree);
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(DynamicToolWindowWrapper.getInstance(project).getTreeTable().getRowCount() > 0);
  }
}
