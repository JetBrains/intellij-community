// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.tree.TreePath;

/**
 * @author Max Medvedev
 */
public class RemoveDynamicAction extends AnAction {

  public RemoveDynamicAction() {
    super(
      GroovyBundle.message("action.remove.dynamic.member.text"),
      GroovyBundle.message("action.remove.dynamic.member.description"),
      AllIcons.General.Remove
    );
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DynamicToolWindowWrapper toolWindow = DynamicToolWindowWrapper.getInstance(e.getProject());

    toolWindow.deleteRow();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final TreePath[] paths = DynamicToolWindowWrapper.getInstance(project).getTreeTable().getTree().getSelectionPaths();
    e.getPresentation().setEnabled(paths != null);
  }
}
