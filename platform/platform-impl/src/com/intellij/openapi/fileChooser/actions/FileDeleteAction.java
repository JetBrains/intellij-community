// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.IoErrorText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

public final class FileDeleteAction extends FileChooserAction {
  /** @deprecated please use {@link FileDeleteAction#FileDeleteAction(String, String, Icon)} instead */
  @Deprecated(forRemoval = true)
  public FileDeleteAction() { }

  @SuppressWarnings("unused")
  public FileDeleteAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    var visible = isEnabled(e);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(
      visible &&
      !(e.getInputEvent() instanceof KeyEvent && e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) instanceof JTextField) &&  // do not override text deletion
      !panel.selectedPaths().isEmpty());
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    var paths = panel.selectedPaths();
    if (paths.isEmpty()) return;

    var project = e.getProject();
    var ok = MessageDialogBuilder.yesNo(UIBundle.message("file.chooser.delete.title"), UIBundle.message("file.chooser.delete.confirm"))
      .yesText(ApplicationBundle.message("button.delete")).noText(CommonBundle.getCancelButtonText())
      .icon(UIUtil.getWarningIcon())
      .ask(project);
    if (!ok) return;

    try {
      var progress = IdeBundle.message("progress.deleting");
      panel.reloadAfter(() -> ProgressManager.getInstance().run(new Task.WithResult<Path, IOException>(e.getProject(), panel.getComponent(), progress, true) {
        @Override
        protected Path compute(@NotNull ProgressIndicator indicator) throws IOException {
          for (var path : paths) {
            if (indicator.isCanceled()) break;
            indicator.setText(path.toString());
            NioFiles.deleteRecursively(path, p -> {
              indicator.checkCanceled();
              indicator.setText2(path.relativize(p).toString());
            });
          }
          return null;
        }
      }));
    }
    catch (IOException ex) {
      Messages.showErrorDialog(panel.getComponent(), IoErrorText.message(ex), CommonBundle.getErrorTitle());
    }
  }

  private static boolean isEnabled(AnActionEvent e) {
    return e.getData(FileChooserKeys.DELETE_ACTION_AVAILABLE) != Boolean.FALSE;
  }

  @Override
  protected void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    boolean visible = isEnabled(e);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(
      visible &&
      !(e.getInputEvent() instanceof KeyEvent && e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) instanceof JTextField) &&  // do not override text deletion
      new VirtualFileDeleteProvider().canDeleteElement(e.getDataContext()));
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e) {
    new VirtualFileDeleteProvider().deleteElement(e.getDataContext());
  }
}
