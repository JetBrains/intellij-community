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

import com.intellij.openapi.application.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.*;
import com.intellij.openapi.vcs.ui.*;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.*;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.*;

import java.util.*;

public class HgCheckinEnvironment implements CheckinEnvironment {

  private final Project project;

  public HgCheckinEnvironment(Project project) {
    this.project = project;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel) {
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
  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    List<VcsException> exceptions = new LinkedList<VcsException>();
    for (Map.Entry<VirtualFile, Set<HgFile>> entry : getFilesByRepository(changes).entrySet()) {

      VirtualFile repo = entry.getKey();
      Set<HgFile> selectedFiles = entry.getValue();

      HgCommitCommand command = new HgCommitCommand(project, repo, preparedComment);
      
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
    return exceptions;
  }

  private boolean isMergeCommit(VirtualFile repo) {
    return new HgWorkingCopyRevisionsCommand(project).parents(repo).size() > 1;
  }

  private Set<HgFile> getChangedFilesNotInCommit(VirtualFile repo, Set<HgFile> selectedFiles) {
    List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(project).parents(repo);

    HgStatusCommand statusCommand = new HgStatusCommand(project);
    statusCommand.setBaseRevision(parents.get(0));
    statusCommand.setIncludeUnknown(false);
    statusCommand.setIncludeIgnored(false);
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
          project, 
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

  public List<VcsException> commit(List<Change> changes,
    String preparedComment, Object parameters) {
    return commit(changes, preparedComment);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    HgRemoveCommand command = new HgRemoveCommand(project);
    for (FilePath filePath : files) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, filePath);
      if (vcsRoot == null) {
        continue;
      }
      command.execute(new HgFile(vcsRoot, filePath));
    }
    return null;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    HgAddCommand command = new HgAddCommand(project);
    for (VirtualFile file : files) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
      if (vcsRoot == null) {
        continue;
      }
      command.execute(new HgFile(vcsRoot, VfsUtil.virtualToIoFile(file)));
    }
    return null;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

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

    VirtualFile repo = VcsUtil.getVcsRootFor(project, filePath);
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

}
