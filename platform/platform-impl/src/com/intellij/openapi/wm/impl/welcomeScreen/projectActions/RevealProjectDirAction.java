// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem;
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTreeItem;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public final class RevealProjectDirAction extends DumbAwareAction implements LightEditCompatible {
  public RevealProjectDirAction() {
    super(RevealFileAction.getActionName());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RecentProjectItem item = (RecentProjectItem)RecentProjectsWelcomeScreenActionBase.Companion.getSelectedItem$intellij_platform_ide_impl(e);
    assert item != null;
    String path = item.getProjectPath();
    RevealFileAction.selectDirectory(new File(path));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    RecentProjectTreeItem item = RecentProjectsWelcomeScreenActionBase.Companion.getSelectedItem$intellij_platform_ide_impl(e);
    e.getPresentation().setEnabledAndVisible(item instanceof RecentProjectItem);
  }
}