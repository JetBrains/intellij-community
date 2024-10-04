// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.ExtendableAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.TriggerAdditionOrDeletion;
import com.intellij.openapi.extensions.ExtensionPointName;
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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.VcsActivity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public class GetVersionAction extends ExtendableAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(GetVersionAction.class);

  private static final ExtensionPointName<AnActionExtensionProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.history.actions.GetVersionAction.ExtensionProvider");

  public GetVersionAction() {
    super(EP_NAME);
  }

  public static void doGet(@NotNull Project project, @NotNull VcsFileRevision revision, @NotNull FilePath filePath) {
    String activityName = VcsBundle.message("activity.name.get.from", revision.getRevisionNumber());
    doGet(project, activityName, Collections.singletonList(new VcsFileRevisionProvider(filePath, revision)), null);
  }

  public static void doGet(@NotNull Project project,
                           @NotNull @NlsContexts.Label String activityName,
                           @NotNull List<? extends FileRevisionProvider> providers,
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

    new MyWriteVersionTask(project, activityName, providers, onFinished).queue();
  }

  private static class MyWriteVersionTask extends Task.Backgroundable {
    @NotNull private final @NlsContexts.Label String myActivityName;
    private final @NotNull List<? extends FileRevisionProvider> myProviders;
    @Nullable private final Runnable myOnFinished;

    MyWriteVersionTask(@NotNull Project project,
                       @NotNull @NlsContexts.Label String activityName,
                       @NotNull List<? extends FileRevisionProvider> providers,
                       @Nullable Runnable onFinished) {
      super(project, VcsBundle.message("show.diff.progress.title"));
      myActivityName = activityName;
      myProviders = providers;
      myOnFinished = onFinished;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      LocalHistoryAction action = LocalHistory.getInstance().startAction(myActivityName, VcsActivity.Get);
      try {
        TriggerAdditionOrDeletion trigger = new TriggerAdditionOrDeletion(myProject);
        Object commandGroup = new Object();

        for (FileRevisionProvider provider : myProviders) {
          FilePath localFilePath = provider.getFilePath();
          FileRevisionContent revisionContent = provider.getContent();

          Ref<IOException> exRef = new Ref<>();
          ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
              CommandProcessor.getInstance().executeCommand(myProject, () -> {
                VirtualFile virtualFile = localFilePath.getVirtualFile();
                if (revisionContent == null && virtualFile == null) return;

                if (revisionContent == null) {
                  trigger.prepare(Collections.emptyList(), Collections.singletonList(localFilePath));
                }
                else if (virtualFile == null) {
                  trigger.prepare(Collections.singletonList(localFilePath), Collections.emptyList());
                }

                ApplicationManager.getApplication().runWriteAction(() -> {
                  try {
                    if (revisionContent == null) {
                      writeDeletion(virtualFile);
                    }
                    else if (virtualFile == null) {
                      FilePath path = ObjectUtils.chooseNotNull(revisionContent.oldFilePath, localFilePath);
                      writeCreation(path, revisionContent.bytes);
                    }
                    else {
                      if (revisionContent.oldFilePath != null && !localFilePath.equals(revisionContent.oldFilePath)) {
                        writeRename(virtualFile, revisionContent.oldFilePath);
                      }
                      writeModification(virtualFile, revisionContent.bytes);
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

    private static void writeRename(@NotNull VirtualFile virtualFile, @NotNull FilePath filePath) throws IOException {
      FilePath parentPath = filePath.getParentPath();
      if (parentPath != null) {
        VirtualFile parentFile = VfsUtil.createDirectories(parentPath.getPath());
        if (parentFile != null && !parentFile.equals(virtualFile.getParent())) {
          virtualFile.move(MyWriteVersionTask.class, parentFile);
        }
      }

      if (!virtualFile.getName().equals(filePath.getName())) {
        virtualFile.rename(MyWriteVersionTask.class, filePath.getName());
      }
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
    @Nullable FileRevisionContent getContent() throws VcsException;
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
    public @Nullable FileRevisionContent getContent() throws VcsException {
      try {
        byte[] bytes = VcsHistoryUtil.loadRevisionContent(myRevision);
        return new FileRevisionContent(bytes, null);
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
    }
  }

  public static class FileRevisionContent {
    public final byte @NotNull [] bytes;
    public final @Nullable FilePath oldFilePath;

    public FileRevisionContent(byte @NotNull [] bytes, @Nullable FilePath oldFilePath) {
      this.bytes = bytes;
      this.oldFilePath = oldFilePath;
    }
  }
}
