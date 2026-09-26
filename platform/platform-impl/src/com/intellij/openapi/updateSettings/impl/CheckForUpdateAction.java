// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

<<<<<<< HEAD
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
=======
import com.intellij.ide.plugins.PluginHostsConfigurable;
>>>>>>> origin/115
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class CheckForUpdateAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    if (ActionPlaces.WELCOME_SCREEN.equals(e.getPlace())) {
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      e.getPresentation().setVisible(!ActionPlaces.isMacSystemMenuAction(e));
    }

    if (ExternalUpdateManager.ACTUAL != null) {
      e.getPresentation().setDescription(ActionsBundle.message("action.CheckForUpdate.description.plugins"));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

<<<<<<< HEAD
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    UpdateCheckerFacade.getInstance().updateAndShowResult(e.getProject());
=======
  public static void actionPerformed(Project project, final boolean enableLink, final @Nullable PluginHostsConfigurable hostsConfigurable) {
    ProgressManager.getInstance().run(new Task.Modal(project, "Checking for updates", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final CheckForUpdateResult result = UpdateChecker.checkForUpdates(UpdateSettings.getInstance(), true);

        final List<PluginDownloader> updatedPlugins = UpdateChecker.updatePlugins(true, hostsConfigurable);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (result.getState() == UpdateStrategy.State.CONNECTION_ERROR) {
              UpdateChecker.showConnectionErrorDialog();
              return;
            }

            UpdateSettings.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
            UpdateChecker.showUpdateResult(result, updatedPlugins, true, enableLink, true);
          }
        });
      }
    });
>>>>>>> origin/115
  }
}
