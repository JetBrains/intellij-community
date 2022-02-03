// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.IoErrorText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class FileDeleteAction extends FileChooserAction {
  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    boolean visible = !isDisabled(e);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && !panel.selectedPaths().isEmpty());
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    var paths = panel.selectedPaths();
    if (paths.isEmpty()) return;

    var project = e.getProject();
    var ok = MessageDialogBuilder.yesNo(UIBundle.message("delete.dialog.title"), IdeBundle.message("chooser.delete.confirm"))
      .yesText(ApplicationBundle.message("button.delete")).noText(CommonBundle.getCancelButtonText())
      .icon(UIUtil.getWarningIcon())
      .ask(project);
    if (!ok) return;

    new Task.Modal(project, panel.getComponent(), IdeBundle.message("progress.deleting"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        try {
          for (var path : paths) {
            if (indicator.isCanceled()) break;
            indicator.setText(path.toString());
            NioFiles.deleteRecursively(path, p -> {
              indicator.checkCanceled();
              indicator.setText2(path.relativize(p).toString());
            });
          }
          ApplicationManager.getApplication().invokeLater(() -> panel.reload());
        }
        catch (IOException e) {
          ApplicationManager.getApplication().invokeLater(
            () -> Messages.showMessageDialog(project, IoErrorText.message(e), CommonBundle.getErrorTitle(), Messages.getErrorIcon()));
        }
      }
    }.queue();
  }

  private static boolean isDisabled(AnActionEvent e) {
    return e.getData(FileChooserKeys.DELETE_ACTION_AVAILABLE) == Boolean.FALSE;
  }

  @Override
  protected void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    boolean visible = !isDisabled(e);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && new VirtualFileDeleteProvider().canDeleteElement(e.getDataContext()));
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    new VirtualFileDeleteProvider().deleteElement(e.getDataContext());
  }
}
