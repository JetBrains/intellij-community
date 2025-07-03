// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GoToWslHomeAction extends FileChooserAction implements LightEditCompatible {
  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(
      WSLUtil.isSystemCompatible() &&
      Experiments.getInstance().isFeatureEnabled("wsl.p9.show.roots.in.file.chooser") &&
      PathEnvironmentVariableUtil.isOnPath("wsl.exe")
    );
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    List<WSLDistribution> vms = ProgressManager.getInstance().run(new Task.WithResult<>(e.getProject(), UIBundle.message("file.chooser.wsl.enumerating"), true) {
      @Override
      protected List<WSLDistribution> compute(@NotNull ProgressIndicator i) {
        return ProgressIndicatorUtils.awaitWithCheckCanceled(WslDistributionManager.getInstance().getInstalledDistributionsFuture(), i);
      }
    });

    if (vms.isEmpty()) {
      Messages.showWarningDialog(e.getProject(), UIBundle.message("file.chooser.wsl.missing.text"), UIBundle.message("file.chooser.wsl.missing.title"));
    }
    else if (vms.size() == 1) {
      navigate(vms.get(0), e.getProject(), panel);
    }
    else {
      vms = new ArrayList<>(vms);
      vms.sort(Comparator.comparing(WSLDistribution::toString));
      var popup = JBPopupFactory.getInstance().createPopupChooserBuilder(vms)
        .setItemChosenCallback(vm -> navigate(vm, e.getProject(), panel))
        .createPopup();
      PopupUtil.showForActionButtonEvent(popup, e);
    }
  }

  private static void navigate(WSLDistribution vm, Project project, FileChooserPanel panel) {
    Path home = ProgressManager.getInstance().run(new Task.WithResult<>(project, UIBundle.message("file.chooser.wsl.resolving"), false) {
      @Override
      protected Path compute(@NotNull ProgressIndicator i) {
        var candidate = vm.getUNCRootPath().resolve("home");
        var homes = NioFiles.list(candidate);
        if (homes.size() == 1) {
          candidate = homes.get(0);
        }
        else if (homes.size() > 1) {
          var env = vm.getUserHome();
          if (env != null && !env.isBlank()) {
            candidate = vm.getUNCRootPath().resolve(env);
          }
        }
        return candidate;
      }
    });
    panel.load(home);
  }

  @Override
  protected void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) { }
}
