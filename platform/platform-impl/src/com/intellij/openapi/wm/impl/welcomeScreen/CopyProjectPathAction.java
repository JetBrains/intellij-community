// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * @author gregsh
 */
public class CopyProjectPathAction extends RecentProjectsWelcomeScreenActionBase {

  @Override
  public void update(@NotNull AnActionEvent e) {
    int count = getSelectedElements(e).size();
    boolean enabled = count > 0 && !hasGroupSelected(e);
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setText(count > 1 ? "Copy Paths" : "Copy Path");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<AnAction> elements = getSelectedElements(e);
    StringBuilder sb = new StringBuilder(elements.size() * 64);
    for (AnAction action : elements) {
      if (sb.length() > 0) sb.append('\n');
      sb.append(FileUtil.toSystemDependentName(((ReopenProjectAction)action).getProjectPath()));
    }
    CopyPasteManager.getInstance().setContents(new StringSelection(sb.toString()));
  }
}
