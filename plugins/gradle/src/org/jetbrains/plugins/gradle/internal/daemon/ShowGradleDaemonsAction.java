// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.statistics.GradleActionsUsagesCollector;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class ShowGradleDaemonsAction extends DumbAwareAction {

  private DaemonsUi myUi;

  public ShowGradleDaemonsAction() {
    super(GradleBundle.messagePointer("gradle.daemons.gradle.daemons.show"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myUi == null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    GradleActionsUsagesCollector.trigger(project, GradleActionsUsagesCollector.SHOW_GRADLE_DAEMONS_ACTION);
    myUi = new DaemonsUi(project) {
      @Override
      public void dispose() {
        myUi = null;
      }
    };
    List<DaemonState> daemonsStatus = GradleDaemonServices.getDaemonsStatus();
    myUi.show(daemonsStatus);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}