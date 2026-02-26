// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.TrashBin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public final class VirtualFileDeleteProvider implements DeleteProvider {
  private static final Logger LOG = Logger.getInstance(VirtualFileDeleteProvider.class);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    var files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    return files != null && files.length > 0;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    var files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (files == null || files.length == 0) return;

    var project = CommonDataKeys.PROJECT.getData(dataContext);
    var toBin = TrashBin.isSupported() && GeneralSettings.getInstance().isDeletingToBin();

    if (!(toBin && ContainerUtil.all(files, TrashBin::canMoveToTrash))) {
      var message = createConfirmationMessage(files);
      var returnValue = Messages.showOkCancelDialog(
        message, UIBundle.message("delete.dialog.title"), ApplicationBundle.message("button.delete"), CommonBundle.getCancelButtonText(), Messages.getQuestionIcon()
      );
      if (returnValue != Messages.OK) return;
    }

    Arrays.sort(files, Comparator.comparing(VirtualFile::getPath));

    var problems = new LinkedList<String>();
    CommandProcessor.getInstance().executeCommand(project, () -> new Task.Modal(project, IdeBundle.message("progress.deleting"), true) {
      private int counter = 0;

      @Override
      @SuppressWarnings("DuplicatedCode")
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        var app = ApplicationManager.getApplication();

        for (var file : files) {
          indicator.checkCanceled();
          indicator.setText(IdeBundle.message("progress.already.deleted", counter));

          if (toBin && TrashBin.canMoveToTrash(file)) {
            LocalFileSystem.MOVE_TO_TRASH.set(file, Boolean.TRUE);
            counter++;
          }
          else {
            LocalFileSystem.DELETE_CALLBACK.set(file, p -> {
              indicator.checkCanceled();
              indicator.setText(IdeBundle.message("progress.already.deleted", counter));
              counter++;
            });
          }

          try {
            app.runWriteAction(() -> {
              try {
                file.delete(this);
              }
              catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
          }
          catch (UncheckedIOException e) {
            LOG.info("Error when deleting " + file, e);
            problems.add(file.getName());
          }
          finally {
            LocalFileSystem.MOVE_TO_TRASH.set(file, null);
            LocalFileSystem.DELETE_CALLBACK.set(file, null);
          }
        }
      }

      @Override
      public void onSuccess() {
        reportProblems();
      }

      @Override
      public void onCancel() {
        reportProblems();
      }

      private void reportProblems() {
        if (!problems.isEmpty()) {
          reportDeletionProblem(problems);
        }
      }
    }.queue(), IdeBundle.message("command.deleting.files"), null);
  }

  private static void reportDeletionProblem(List<String> problems) {
    var more = false;
    if (problems.size() > 10) {
      problems = problems.subList(0, 10);
      more = true;
    }
    var message = IdeBundle.message("dialog.message.could.not.erase.files.or.folders.0.1", String.join(",\n  ", problems), more ? "\n  ..." : "");
    Messages.showMessageDialog(message, UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
  }

  private static @NlsContexts.DialogMessage String createConfirmationMessage(VirtualFile[] filesToDelete) {
    if (filesToDelete.length == 1) {
      if (filesToDelete[0].isDirectory()) {
        return UIBundle.message("are.you.sure.you.want.to.delete.selected.folder.confirmation.message", filesToDelete[0].getName());
      }
      else {
        return UIBundle.message("are.you.sure.you.want.to.delete.selected.file.confirmation.message", filesToDelete[0].getName());
      }
    }
    else {
      var hasFiles = false;
      var hasFolders = false;
      for (var file : filesToDelete) {
        var isDirectory = file.isDirectory();
        hasFiles |= !isDirectory;
        hasFolders |= isDirectory;
      }
      LOG.assertTrue(hasFiles || hasFolders);
      if (hasFiles && hasFolders) {
        return UIBundle.message("are.you.sure.you.want.to.delete.selected.files.and.directories.confirmation.message", filesToDelete.length);
      }
      else if (hasFolders) {
        return UIBundle.message("are.you.sure.you.want.to.delete.selected.folders.confirmation.message", filesToDelete.length);
      }
      else {
        return UIBundle.message("are.you.sure.you.want.to.delete.selected.files.and.files.confirmation.message", filesToDelete.length);
      }
    }
  }
}
