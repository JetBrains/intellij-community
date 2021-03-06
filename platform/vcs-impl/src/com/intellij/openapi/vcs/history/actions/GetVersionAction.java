// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.TriggerAdditionOrDeletion;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    String actionTitle = VcsBundle.message("action.name.for.file.get.version", filePath.getPath(), revision.getRevisionNumber());
    doGet(project, actionTitle, Collections.singletonList(new VcsFileRevisionProvider(filePath, revision)), null);
  }

  public static void doGet(@NotNull Project project,
                           @NotNull @NlsContexts.Label String actionTitle,
                           @NotNull List<FileRevisionProvider> providers,
                           @Nullable Runnable onFinished) {
    List<VirtualFile> files = ContainerUtil.mapNotNull(providers, it -> it.getFilePath().getVirtualFile());
    if (!files.isEmpty()) {
      ReplaceFileConfirmationDialog confirmationDialog =
        new ReplaceFileConfirmationDialog(project, VcsBundle.message("acton.name.get.revision"));
      if (!confirmationDialog.confirmFor(VfsUtilCore.toVirtualFileArray(files))) {
        return;
      }

      if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files).hasReadonlyFiles()) {
        return;
      }
    }

    new MyWriteVersionTask(project, actionTitle, providers, onFinished).queue();
  }

  private static class MyWriteVersionTask extends Task.Backgroundable {
    @NotNull private final @NlsContexts.Label String myActionTitle;
    @NotNull private final List<FileRevisionProvider> myProviders;
    @Nullable private final Runnable myOnFinished;

    MyWriteVersionTask(@NotNull Project project,
                       @NotNull @NlsContexts.Label String actionTitle,
                       @NotNull List<FileRevisionProvider> providers,
                       @Nullable Runnable onFinished) {
      super(project, VcsBundle.message("show.diff.progress.title"));
      myActionTitle = actionTitle;
      myProviders = providers;
      myOnFinished = onFinished;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      LocalHistoryAction action = LocalHistory.getInstance().startAction(myActionTitle);
      try {
        TriggerAdditionOrDeletion trigger = new TriggerAdditionOrDeletion(myProject);
        Object commandGroup = new Object();

        for (FileRevisionProvider provider : myProviders) {
          FilePath filePath = provider.getFilePath();
          byte[] revisionContent = provider.getContent();

          Ref<IOException> exRef = new Ref<>();
          ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
              CommandProcessor.getInstance().executeCommand(myProject, () -> {
                VirtualFile virtualFile = filePath.getVirtualFile();
                if (revisionContent == null && virtualFile == null) return;

                if (revisionContent == null) {
                  trigger.prepare(Collections.emptyList(), Collections.singletonList(filePath));
                }
                else if (virtualFile == null) {
                  trigger.prepare(Collections.singletonList(filePath), Collections.emptyList());
                }

                ApplicationManager.getApplication().runWriteAction(() -> {
                  try {
                    if (revisionContent == null) {
                      writeDeletion(virtualFile);
                    }
                    else if (virtualFile == null) {
                      writeCreation(filePath, revisionContent);
                    }
                    else {
                      writeModification(virtualFile, revisionContent);
                    }
                  }
                  catch (IOException e) {
                    exRef.set(e);
                  }
                });
              }, VcsBundle.message("message.title.get.version"), commandGroup);
            }
            finally {
              trigger.cleanup();
            }
          });
          if (!exRef.isNull()) throw exRef.get();
        }

        trigger.processIt();
      }
      catch (IOException e) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMessageDialog(VcsBundle.message("message.text.cannot.save.content", e.getLocalizedMessage()),
                                           VcsBundle.message("message.title.get.revision.content"), Messages.getErrorIcon()));
      }
      catch (VcsException e) {
        LOG.info(e);
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMessageDialog(VcsBundle.message("message.text.cannot.load.revision", e.getLocalizedMessage()),
                                           VcsBundle.message("message.title.get.revision.content"), Messages.getInformationIcon()));
      }
      finally {
        action.finish();
      }
    }

    private static void writeDeletion(@NotNull VirtualFile virtualFile) throws IOException {
      virtualFile.delete(MyWriteVersionTask.class);
    }

    private static void writeCreation(@NotNull FilePath filePath, byte @NotNull [] revisionContent) throws IOException {
      FilePath parentPath = Objects.requireNonNull(filePath.getParentPath());
      VirtualFile parent = VfsUtil.createDirectories(parentPath.getPath());
      if (parent == null) throw new IOException("Can't create directory: " + parentPath);

      VirtualFile virtualFile = parent.createChildData(MyWriteVersionTask.class, filePath.getName());
      virtualFile.setBinaryContent(revisionContent);
    }

    private static void writeModification(@NotNull VirtualFile virtualFile, byte @NotNull [] revisionContent) throws IOException {
      virtualFile.setBinaryContent(revisionContent);
      // Avoid MemoryDiskConflictResolver. We've got user consent to override file in ReplaceFileConfirmationDialog.
      FileDocumentManager.getInstance().reloadFiles(virtualFile);
    }

    @Override
    public void onFinished() {
      if (myOnFinished != null) myOnFinished.run();
    }
  }

  public interface FileRevisionProvider {
    @NotNull
    FilePath getFilePath();

    /**
     * @return file content at some revision. Return <code>null</code> if file does not exist in this revision.
     */
    byte @Nullable [] getContent() throws VcsException;
  }

  private static class VcsFileRevisionProvider implements FileRevisionProvider {
    @NotNull private final FilePath myFilePath;
    @NotNull private final VcsFileRevision myRevision;

    private VcsFileRevisionProvider(@NotNull FilePath filePath, @NotNull VcsFileRevision revision) {
      myFilePath = filePath;
      myRevision = revision;
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return myFilePath;
    }

    @Override
    public byte @Nullable [] getContent() throws VcsException {
      try {
        return VcsHistoryUtil.loadRevisionContent(myRevision);
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
    }
  }
}
