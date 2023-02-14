// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class OpenAlienProjectAction extends AnAction {

  private final ProjectDetector myDetector;
  private List<String> myProjectPaths;

  public OpenAlienProjectAction(ProjectDetector detector) {
    myDetector = detector;
  }

  public void scheduleUpdate(JComponent toUpdate) {
    toUpdate.setVisible(false);
    myDetector.detectProjects(projects -> toUpdate.setVisible(!(myProjectPaths = projects).isEmpty()));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(ActionPlaces.WELCOME_SCREEN.equals(e.getPlace()));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myProjectPaths == null) return;
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (@NlsSafe String path : myProjectPaths) {
      actionGroup.add(new AnAction(path) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ProjectUtil.openOrImport(Path.of(path));
          projectOpened();
        }
      });
    }
    JBPopupFactory.getInstance()
      .createActionGroupPopup(IdeBundle.message("popup.title.open.project"), actionGroup, e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false).showUnderneathOf(Objects.requireNonNull(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)));
  }

  protected void projectOpened() {}
}
