// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

@ApiStatus.Internal
public class CreatePatchFromDirectoryAction implements AnActionExtensionProvider {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    if (filePath == null) return;
    VcsFileRevision[] revisions = e.getData(VcsDataKeys.VCS_FILE_REVISIONS);
    if (revisions == null) return;

    if (filePath.isDirectory()) {
      if (revisions.length != 1) return;

      Project project = e.getData(CommonDataKeys.PROJECT);
      if (project == null) return;
      VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
      if (vcsKey == null) return;
      AbstractVcs vcs = VcsUtil.findVcsByKey(project, vcsKey);
      if (vcs == null) return;

      ProgressManager.getInstance().run(new FolderPatchCreationTask(vcs, revisions[0]));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    VcsHistorySession session = e.getData(VcsDataKeys.HISTORY_SESSION);
    FilePath path = e.getData(VcsDataKeys.FILE_PATH);
    return session != null && path != null && path.isDirectory();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setText(VcsBundle.messagePointer("action.name.create.patch"));
    e.getPresentation().setDescription(VcsBundle.messagePointer("action.description.create.patch.for.selected.revisions"));

    VcsFileRevision[] revisions = e.getData(VcsDataKeys.VCS_FILE_REVISIONS);
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsHistoryProvider provider = e.getData(VcsDataKeys.HISTORY_PROVIDER);
    Project project = e.getProject();
    VcsKey vcsKey = e.getData(VcsDataKeys.VCS);

    if (filePath == null || filePath.isNonLocal() || revisions == null || provider == null || project == null || vcsKey == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    if (filePath.isDirectory()) {
      if (!provider.supportsHistoryForDirectories()) {
        e.getPresentation().setEnabled(false);
      }
      else {
        e.getPresentation().setEnabled(revisions.length == 1 && revisions[0].getChangedRepositoryPath() != null);
      }
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  private static final class FolderPatchCreationTask extends Task.Backgroundable {
    @NotNull private final AbstractVcs myVcs;
    @NotNull private final VcsFileRevision myRevision;
    private CommittedChangeList myList;
    private VcsException myException;

    private FolderPatchCreationTask(@NotNull AbstractVcs vcs, @NotNull VcsFileRevision revision) {
      super(vcs.getProject(), VcsBundle.message("create.patch.loading.content.progress"), true);
      myVcs = vcs;
      myRevision = revision;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      CommittedChangesProvider provider = myVcs.getCommittedChangesProvider();
      if (provider == null) return;

      RepositoryLocation changedRepositoryPath = myRevision.getChangedRepositoryPath();
      if (changedRepositoryPath == null) return;

      VcsVirtualFile vf = new VcsVirtualFile(changedRepositoryPath.toPresentableString(), myRevision, VcsFileSystem.getInstance());

      try {
        myList = AbstractVcsHelperImpl.getRemoteList(provider, myRevision.getRevisionNumber(), vf);
        //myList = provider.getOneList(vf, myRevision.getRevisionNumber());
      }
      catch (VcsException e1) {
        myException = e1;
      }
    }

    @Override
    public void onSuccess() {
      AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
      if (myException != null) {
        helper.showError(myException, VcsBundle.message("create.patch.error.title", myException.getMessage()));
      }
      else if (myList == null) {
        helper.showError(null, VcsBundle.message("history.tab.title.can.not.load.changelist.contents"));
      }
      else {
        CreatePatchFromChangesAction.createPatch(myProject, myList.getComment(), new ArrayList<>(myList.getChanges()));
      }
    }
  }
}
