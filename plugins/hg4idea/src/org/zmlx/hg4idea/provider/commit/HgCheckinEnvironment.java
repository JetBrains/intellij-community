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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.execution.HgCommandException;

import java.util.*;

public class HgCheckinEnvironment implements CheckinEnvironment {

  private final Project myProject;
  private boolean myNextCommitIsPushed;

  public HgCheckinEnvironment(Project project) {
    myProject = project;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    myNextCommitIsPushed = false;
    return null;
  }

  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  public String getHelpId() {
    return null;
  }

  public String getCheckinOperationName() {
    return HgVcsMessages.message("hg4idea.commit");
  }

  @SuppressWarnings({"ThrowableInstanceNeverThrown"})
  public List<VcsException> commit(List<Change> changes,
                                   String preparedComment,
                                   @NotNull NullableFunction<Object, Object> parametersHolder,
                                   Set<String> feedback) {
    List<VcsException> exceptions = new LinkedList<VcsException>();
    Map<VirtualFile, Set<HgFile>> repositoriesMap = getFilesByRepository(changes);
    for (Map.Entry<VirtualFile, Set<HgFile>> entry : repositoriesMap.entrySet()) {

      VirtualFile repo = entry.getKey();
      Set<HgFile> selectedFiles = entry.getValue();

      HgCommitCommand command = new HgCommitCommand(myProject, repo, preparedComment);
      
      if (isMergeCommit(repo)) {
        //partial commits are not allowed during merges
        //verifyResult that all changed files in the repo are selected
        //If so, commit the entire repository
        //If not, abort

        Set<HgFile> changedFilesNotInCommit = getChangedFilesNotInCommit(repo, selectedFiles);
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
        }
        // else : all was included, or it was OK to commit everything,
        // so no need to set the files on the command, because then mercurial will complain
      } else {
        command.setFiles(selectedFiles);
      }
      try {
        command.execute();
      } catch (HgCommandException e) {
        exceptions.add(new VcsException(e));
      } catch (VcsException e) {
        exceptions.add(e);
      }
    }

    // push if needed
    if (myNextCommitIsPushed && exceptions.isEmpty()) {
      final VirtualFile preselectedRepo = repositoriesMap.size() == 1 ? repositoriesMap.keySet().iterator().next() : null;
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          new HgPusher(myProject).showDialogAndPush(preselectedRepo);
        }
      });
    }

    return exceptions;
  }

  private boolean isMergeCommit(VirtualFile repo) {
    return new HgWorkingCopyRevisionsCommand(myProject).parents(repo).size() > 1;
  }

  private Set<HgFile> getChangedFilesNotInCommit(VirtualFile repo, Set<HgFile> selectedFiles) {
    List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(repo);

    HgStatusCommand statusCommand = new HgStatusCommand.Builder(true).unknown(false).ignored(false).baseRevision(parents.get(0)).build(myProject);
    Set<HgChange> allChangedFilesInRepo = statusCommand.execute(repo);

    Set<HgFile> filesNotIncluded = new HashSet<HgFile>();

    for (HgChange change : allChangedFilesInRepo) {
      HgFile beforeFile = change.beforeFile();
      HgFile afterFile = change.afterFile();
      if (!selectedFiles.contains(beforeFile)) {
        filesNotIncluded.add(beforeFile);
      } else if (!selectedFiles.contains(afterFile)) {
        filesNotIncluded.add(afterFile);
      }
    }
    return filesNotIncluded;
  }

  private boolean mayCommitEverything(final String filesNotIncludedString) {
    final int[] choice = new int[1];
    Runnable runnable = new Runnable() {
      public void run() {
        choice[0] = Messages.showOkCancelDialog(
          myProject,
          HgVcsMessages.message("hg4idea.commit.partial.merge.message", filesNotIncludedString),
          HgVcsMessages.message("hg4idea.commit.partial.merge.title"),
          null
        );
      }
    };
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.defaultModalityState());
    } else {
      runnable.run();
    }
    return choice[0] == 0;
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    return commit(changes, preparedComment, FunctionUtil.<Object, Object>nullConstant(), null);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    final List<HgFile> filesWithRoots = new ArrayList<HgFile>();
    for (FilePath filePath : files) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, filePath);
      if (vcsRoot == null) {
        continue;
      }
      filesWithRoots.add(new HgFile(vcsRoot, filePath));
    }
    new Task.Backgroundable(myProject, "Removing files...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        HgRemoveCommand command = new HgRemoveCommand(myProject);
        command.execute(filesWithRoots);
      }
    }.queue();
    return null;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(final List<VirtualFile> files) {
    new HgAddCommand(myProject).addWithProgress(files);
    return null;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return false;
  }

  @NotNull
  private Map<VirtualFile, Set<HgFile>> getFilesByRepository(List<Change> changes) {
    Map<VirtualFile, Set<HgFile>> result = new HashMap<VirtualFile, Set<HgFile>>();
    for (Change change : changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      ContentRevision beforeRevision = change.getBeforeRevision();

      if (afterRevision != null) {
        addFile(result, afterRevision.getFile());
      }
      if (beforeRevision != null) {
        addFile(result, beforeRevision.getFile());
      }
    }
    return result;
  }

  private void addFile(Map<VirtualFile, Set<HgFile>> result, FilePath filePath) {
    if (filePath == null) {
      return;
    }

    VirtualFile repo = VcsUtil.getVcsRootFor(myProject, filePath);
    if (repo == null || filePath.isDirectory()) {
      return;
    }

    Set<HgFile> hgFiles = result.get(repo);
    if (hgFiles == null) {
      hgFiles = new HashSet<HgFile>();
      result.put(repo, hgFiles);
    }

    hgFiles.add(new HgFile(repo, filePath));
  }

  public void setNextCommitIsPushed(boolean pushed) {
    myNextCommitIsPushed = true;
  }
}
