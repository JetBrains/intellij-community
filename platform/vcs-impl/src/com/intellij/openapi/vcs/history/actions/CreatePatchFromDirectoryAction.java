/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.history.actions;

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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CreatePatchFromDirectoryAction implements AnActionExtensionProvider {

  @Override
  public void actionPerformed(AnActionEvent e) {
    FilePath filePath = e.getRequiredData(VcsDataKeys.FILE_PATH);
    VcsFileRevision[] revisions = e.getRequiredData(VcsDataKeys.VCS_FILE_REVISIONS);

    if (filePath.isDirectory()) {
      if (revisions.length != 1) return;

      AbstractVcs vcs = VcsUtil.findVcsByKey(e.getRequiredData(CommonDataKeys.PROJECT), e.getRequiredData(VcsDataKeys.VCS));
      if (vcs == null) return;

      ProgressManager.getInstance().run(new FolderPatchCreationTask(vcs, revisions[0]));
    }
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    VcsHistorySession session = e.getData(VcsDataKeys.HISTORY_SESSION);
    FilePath path = e.getData(VcsDataKeys.FILE_PATH);
    return session != null && path != null && path.isDirectory();
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setText(VcsBundle.message("action.name.create.patch.for.selected.revisions"));
    e.getPresentation().setDescription(VcsBundle.message("action.description.create.patch.for.selected.revisions"));

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

  private static class FolderPatchCreationTask extends Task.Backgroundable {
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
        myList = AbstractVcsHelperImpl.getRemoteList(myVcs, myRevision.getRevisionNumber(), vf);
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
        helper.showError(null, "Can not load changelist contents");
      }
      else {
        CreatePatchFromChangesAction.createPatch(myProject, myList.getComment(), new ArrayList<>(myList.getChanges()));
      }
    }
  }
}
