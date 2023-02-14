// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider.commit;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.commit.AmendCommitAware;
import com.intellij.vcs.commit.EditedCommitDetails;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.command.mq.HgQNewCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.provider.HgCurrentBinaryContentRevision;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.*;

import static com.intellij.vcs.commit.AbstractCommitWorkflowKt.isAmendCommitMode;
import static com.intellij.vcs.commit.LocalChangesCommitterKt.getCommitWithoutChangesRoots;
import static com.intellij.vcs.commit.ToggleAmendCommitOption.isAmendCommitOptionSupported;
import static java.util.Collections.emptySet;
import static org.zmlx.hg4idea.provider.commit.HgCommitAndPushExecutorKt.isPushAfterCommit;
import static org.zmlx.hg4idea.provider.commit.HgCommitOptionsKt.*;
import static org.zmlx.hg4idea.util.HgUtil.getRepositoryManager;

public class HgCheckinEnvironment implements CheckinEnvironment, AmendCommitAware {
  @NotNull private final HgVcs myVcs;
  @NotNull private final Project myProject;

  public HgCheckinEnvironment(@NotNull HgVcs vcs) {
    myVcs = vcs;
    myProject = vcs.getProject();
  }

  @Nullable
  @Override
  public RefreshableOnComponent createCommitOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext) {
    Collection<HgRepository> repos =
      ContainerUtil.map2SetNotNull(commitPanel.getRoots(), getRepositoryManager(myProject)::getRepositoryForFileQuick);
    boolean hasSubrepos = ContainerUtil.exists(repos, HgRepository::hasSubrepos);
    boolean showAmendOption = isAmendCommitOptionSupported(commitPanel, this);

    if (!hasSubrepos && !showAmendOption) return null;

    return new HgCommitAdditionalComponent(commitPanel, commitContext, hasSubrepos, showAmendOption);
  }

  @Override
  public String getHelpId() {
    return null;
  }

  @Override
  public String getCheckinOperationName() {
    return HgBundle.message("hg4idea.commit");
  }

  @Override
  public boolean isAmendCommitSupported() {
    return getAmendService().isAmendCommitSupported();
  }

  @Nullable
  @Override
  public String getLastCommitMessage(@NotNull VirtualFile root) {
    return getAmendService().getLastCommitMessage(root);
  }

  @NotNull
  @Override
  public CancellablePromise<EditedCommitDetails> getAmendCommitDetails(@NotNull VirtualFile root) {
    return getAmendService().getAmendCommitDetails(root);
  }

  @NotNull
  private HgAmendCommitService getAmendService() {
    return myProject.getService(HgAmendCommitService.class);
  }

  @NotNull
  @Override
  public List<VcsException> commit(@NotNull List<? extends Change> changes,
                                   @NotNull String commitMessage,
                                   @NotNull CommitContext commitContext,
                                   @NotNull Set<? super String> feedback) {
    List<VcsException> exceptions = new LinkedList<>();
    Map<HgRepository, Set<HgFile>> repositoriesMap = getFilesByRepository(changes);
    addRepositoriesWithoutChanges(repositoriesMap, commitContext);
    boolean isAmend = isAmendCommitMode(commitContext);
    for (Map.Entry<HgRepository, Set<HgFile>> entry : repositoriesMap.entrySet()) {

      HgRepository repo = entry.getKey();
      Set<HgFile> selectedFiles = entry.getValue();
      boolean isCloseBranch = isCloseBranch(commitContext);
      boolean isCommitSubrepositories = isCommitSubrepositories(commitContext);
      HgCommitTypeCommand command =
        isMqNewPatch(commitContext)
        ? new HgQNewCommand(myProject, repo, commitMessage, isAmend)
        : new HgCommitCommand(myProject, repo, commitMessage, isAmend, isCloseBranch, isCommitSubrepositories && !selectedFiles.isEmpty());

      if (isMergeCommit(repo.getRoot())) {
        //partial commits are not allowed during merges
        //verifyResult that all changed files in the repo are selected
        //If so, commit the entire repository
        //If not, abort

        Set<HgFile> changedFilesNotInCommit = getChangedFilesNotInCommit(repo.getRoot(), selectedFiles);
        boolean partial = !changedFilesNotInCommit.isEmpty();


        if (partial) {
          final StringBuilder filesNotIncludedString = new StringBuilder();
          for (HgFile hgFile : changedFilesNotInCommit) {
            filesNotIncludedString.append("<li>");
            filesNotIncludedString.append(hgFile.getRelativePath());
            filesNotIncludedString.append("</li>");
          }
          if (!mayCommitEverything(filesNotIncludedString.toString())) {
            //abort
            return exceptions;
          }
          //firstly selected changes marked dirty in SingleChangeListCommitter -> doPostRefresh, so we need to mark others
          VcsDirtyScopeManager dirtyManager = VcsDirtyScopeManager.getInstance(myProject);
          for (HgFile hgFile : changedFilesNotInCommit) {
            dirtyManager.fileDirty(hgFile.toFilePath());
          }
        }
        // else : all was included, or it was OK to commit everything,
        // so no need to set the files on the command, because then mercurial will complain
      }
      else {
        command.setFiles(selectedFiles);
      }
      try {
        command.executeInCurrentThread();
      }
      catch (HgCommandException e) {
        exceptions.add(new VcsException(e));
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    // push if needed
    if (isPushAfterCommit(commitContext) && exceptions.isEmpty()) {
      List<HgRepository> preselectedRepositories = new ArrayList<>(repositoriesMap.keySet());
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
        HgRepository selectedRepo = DvcsUtil.guessRepositoryForOperation(myProject, getRepositoryManager(myProject));
        new VcsPushDialog(myProject, preselectedRepositories, selectedRepo).show();
      });
    }

    return exceptions;
  }

  private boolean isMergeCommit(VirtualFile repo) {
    return new HgWorkingCopyRevisionsCommand(myProject).parents(repo).size() > 1;
  }

  private Set<HgFile> getChangedFilesNotInCommit(VirtualFile repo, Set<HgFile> selectedFiles) {
    List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(repo);

    HgStatusCommand statusCommand =
      new HgStatusCommand.Builder(true).unknown(false).ignored(false).baseRevision(parents.get(0)).build(myProject);
    Set<HgChange> allChangedFilesInRepo = statusCommand.executeInCurrentThread(repo);

    Set<HgFile> filesNotIncluded = new HashSet<>();

    for (HgChange change : allChangedFilesInRepo) {
      HgFile beforeFile = change.beforeFile();
      HgFile afterFile = change.afterFile();
      if (!selectedFiles.contains(beforeFile)) {
        filesNotIncluded.add(beforeFile);
      }
      else if (!selectedFiles.contains(afterFile)) {
        filesNotIncluded.add(afterFile);
      }
    }
    return filesNotIncluded;
  }

  private boolean mayCommitEverything(final String filesNotIncludedString) {
    final int[] choice = new int[1];
    Runnable runnable = () -> choice[0] = Messages.showOkCancelDialog(
      myProject,
      XmlStringUtil.wrapInHtml(HgBundle.message("hg4idea.commit.partial.merge.message", filesNotIncludedString)),
      HgBundle.message("hg4idea.commit.partial.merge.title"),
      null
    );
    ApplicationManager.getApplication().invokeAndWait(runnable);
    return choice[0] == Messages.OK;
  }

  @Override
  public List<VcsException> scheduleMissingFileForDeletion(@NotNull List<? extends FilePath> files) {
    final List<HgFile> filesWithRoots = new ArrayList<>();
    for (FilePath filePath : files) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, filePath);
      if (vcsRoot == null) {
        continue;
      }
      filesWithRoots.add(new HgFile(vcsRoot, filePath));
    }
    new Task.Backgroundable(myProject, HgBundle.message("files.removing.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        new HgRemoveCommand(myProject).executeInCurrentThread(filesWithRoots);
      }
    }.queue();
    return null;
  }

  @Override
  public List<VcsException> scheduleUnversionedFilesForAddition(final @NotNull List<? extends VirtualFile> files) {
    new HgAddCommand(myProject).addWithProgress(files);
    return null;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return false;
  }

  @NotNull
  private Map<HgRepository, Set<HgFile>> getFilesByRepository(List<? extends Change> changes) {
    Map<HgRepository, Set<HgFile>> result = new HashMap<>();
    for (Change change : changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      ContentRevision beforeRevision = change.getBeforeRevision();

      if (afterRevision != null) {
        addFile(result, afterRevision);
      }
      if (beforeRevision != null) {
        addFile(result, beforeRevision);
      }
    }
    return result;
  }

  private void addFile(Map<HgRepository, Set<HgFile>> result, ContentRevision contentRevision) {
    FilePath filePath = contentRevision.getFile();
    // try to find repository from hgFile from change: to be able commit sub repositories as expected
    HgRepository repo = HgUtil.getRepositoryForFile(myProject, contentRevision instanceof HgCurrentBinaryContentRevision
                                                               ? ((HgCurrentBinaryContentRevision)contentRevision).getRepositoryRoot()
                                                               : ChangesUtil.findValidParentAccurately(filePath));
    if (repo == null) {
      return;
    }

    Set<HgFile> hgFiles = result.get(repo);
    if (hgFiles == null) {
      hgFiles = new HashSet<>();
      result.put(repo, hgFiles);
    }

    hgFiles.add(new HgFile(repo.getRoot(), filePath));
  }

  private void addRepositoriesWithoutChanges(@NotNull Map<HgRepository, Set<HgFile>> repositoryMap, @NotNull CommitContext commitContext) {
    HgRepositoryManager repositoryManager = getRepositoryManager(myProject);

    for (VcsRoot root : getCommitWithoutChangesRoots(commitContext)) {
      HgRepository repository = root.getVcs() == myVcs ? repositoryManager.getRepositoryForRoot(root.getPath()) : null;

      if (repository != null && !repositoryMap.containsKey(repository)) {
        repositoryMap.put(repository, emptySet());
      }
    }
  }

  @SuppressWarnings("InnerClassMayBeStatic") // for compatibility with external plugins
  public class HgCommitAdditionalComponent extends org.zmlx.hg4idea.provider.commit.HgCommitAdditionalComponent {
    public HgCommitAdditionalComponent(@NotNull CheckinProjectPanel panel,
                                       @NotNull CommitContext commitContext,
                                       boolean hasSubrepos,
                                       boolean showAmendOption) {
      super(panel, commitContext, hasSubrepos, showAmendOption);
    }
  }
}
