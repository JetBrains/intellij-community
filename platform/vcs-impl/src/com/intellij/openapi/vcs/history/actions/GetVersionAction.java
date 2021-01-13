// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;

public class GetVersionAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(GetVersionAction.class);

  public GetVersionAction() {
    super(VcsBundle.messagePointer("action.name.get.file.content.from.repository"),
          VcsBundle.messagePointer("action.description.get.file.content.from.repository"), AllIcons.Actions.Download);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);

    if (e.getProject() == null || filePath == null || revision == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(isContentAvailable(filePath, revision, e));
    }
  }

  protected boolean isContentAvailable(@NotNull FilePath filePath, @NotNull VcsFileRevision revision, @NotNull AnActionEvent e) {
    VcsHistorySession historySession = e.getData(VcsDataKeys.HISTORY_SESSION);
    if (historySession == null) {
      return false;
    }
    return historySession.isContentAvailable(revision) && !filePath.isDirectory();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);

    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    VcsFileRevision revision = e.getRequiredData(VcsDataKeys.VCS_FILE_REVISION);
    FilePath filePath = e.getRequiredData(VcsDataKeys.FILE_PATH);

    doGet(project, revision, filePath);
  }

  public static void doGet(@NotNull Project project, @NotNull VcsFileRevision revision, @NotNull FilePath filePath) {
    VirtualFile virtualFile = filePath.getVirtualFile();
    if (virtualFile != null) {
      ReplaceFileConfirmationDialog confirmationDialog =
        new ReplaceFileConfirmationDialog(project, VcsBundle.message("acton.name.get.revision"));
      if (!confirmationDialog.confirmFor(new VirtualFile[]{virtualFile})) {
        return;
      }
    }

    new MyWriteVersionTask(project, filePath, revision).queue();
  }

  private static void refreshFile(@NotNull FilePath filePath) {
    VirtualFile file = filePath.getVirtualFile();
    if (file != null) {
      file.refresh(false, false);
    }
    else {
      VirtualFile parent = filePath.getVirtualFileParent();
      if (parent != null) {
        parent.refresh(false, true);
      }
    }
  }

  private static void write(@NotNull FilePath filePath, byte[] revision) throws IOException {
    VirtualFile virtualFile = filePath.getVirtualFile();
    if (virtualFile == null) {
      FileUtil.writeToFile(filePath.getIOFile(), revision);
    }
    else {
      virtualFile.setBinaryContent(revision);
      // Avoid MemoryDiskConflictResolver. We've got user consent to override file in ReplaceFileConfirmationDialog.
      FileDocumentManager.getInstance().reloadFiles(virtualFile);
    }
  }

  private static class MyWriteVersionTask extends Task.Backgroundable {
    @NotNull private final FilePath myFilePath;
    @NotNull private final VcsFileRevision myRevision;

    MyWriteVersionTask(@NotNull Project project, @NotNull FilePath filePath, @NotNull VcsFileRevision revision) {
      super(project, VcsBundle.message("show.diff.progress.title"));
      myFilePath = filePath;
      myRevision = revision;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        byte[] revisionContent = VcsHistoryUtil.loadRevisionContent(myRevision);

        String actionTitle = VcsBundle.message("action.name.for.file.get.version", myFilePath.getPath(), myRevision.getRevisionNumber());
        LocalHistoryAction action = LocalHistory.getInstance().startAction(actionTitle);
        try {
          ApplicationManager.getApplication().invokeAndWait(() -> {
            writeFileContent(revisionContent);
          });

          refreshFile(myFilePath);
          VcsDirtyScopeManager.getInstance(myProject).fileDirty(myFilePath);
        }
        finally {
          action.finish();
        }
      }
      catch (IOException | VcsException e) {
        LOG.info(e);
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMessageDialog(VcsBundle.message("message.text.cannot.load.revision", e.getLocalizedMessage()),
                                           VcsBundle.message("message.title.get.revision.content"), Messages.getInformationIcon()));
      }
    }

    private void writeFileContent(byte @NotNull [] revisionContent) {
      VirtualFile virtualFile = myFilePath.getVirtualFile();
      if (virtualFile != null && !virtualFile.isWritable() &&
          ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(Collections.singletonList(virtualFile)).hasReadonlyFiles()) {
        return;
      }

      WriteCommandAction.writeCommandAction(myProject)
        .withName(VcsBundle.message("message.title.get.version"))
        .run(() -> {
          try {
            write(myFilePath, revisionContent);
          }
          catch (IOException e) {
            Messages.showMessageDialog(VcsBundle.message("message.text.cannot.save.content", e.getLocalizedMessage()),
                                       VcsBundle.message("message.title.get.revision.content"), Messages.getErrorIcon());
          }
        });
    }
  }
}
