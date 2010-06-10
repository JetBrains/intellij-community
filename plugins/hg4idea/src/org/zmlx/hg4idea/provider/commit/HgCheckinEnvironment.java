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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcsMessages;
import gnu.trove.THashSet;
import org.zmlx.hg4idea.*;

import org.zmlx.hg4idea.command.*;

import java.util.*;
import java.util.List;

public class HgCheckinEnvironment implements CheckinEnvironment {

  private final Project myProject;

  public HgCheckinEnvironment(Project project) {
    this.myProject = project;
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
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
  public List<VcsException> commit(List<Change> changes, String preparedComment, @NotNull NullableFunction<Object, Object> parametersHolder) {
    final List<VcsException> exceptions = new LinkedList<VcsException>();
    final Collection<Change> removedChanges = (Collection<Change>) parametersHolder.fun(HgVcs.getInstance(myProject));

    for (final Map.Entry<VirtualFile, List<Change>> entry : groupChangesByRepository(changes).entrySet()) {
      // separate commit for each repository
      final VirtualFile repo = entry.getKey();
      final HgCommitCommand command = new HgCommitCommand(myProject, repo, preparedComment);

      // commit files, except those which were deleted from filesystem, but not from the VCS.
      // HgRemoveCheckinHandler proposes to remove such files from the VCS before commit.
      // If some of those weren't removed, it was done intentionally, so just silently ignore them.
      final Set<HgFile> selectedFiles = new THashSet<HgFile>();
      for (Change c : entry.getValue()) {
        if (c.getFileStatus() == FileStatus.DELETED_FROM_FS) {
          if (removedChanges == null || !removedChanges.contains(c)) { // missing and not removed from vcs via the HgRemoveCheckinHandler
            continue;
          }
        }
        final FilePath filepath = (c.getAfterRevision() == null ? c.getBeforeRevision().getFile() : c.getAfterRevision().getFile());
        selectedFiles.add(new HgFile(repo, filepath));
      }

      if (isMergeCommit(repo)) {
        //partial commits are not allowed during merges
        //verifyResult that all changed files in the repo are selected
        //If so, commit the entire repository
        //If not, abort

        final Set<HgFile> changedFilesNotInCommit = getChangedFilesNotInCommit(repo, selectedFiles);
        if (!changedFilesNotInCommit.isEmpty()) {
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
          // else : all was included, or it was OK to commit everything,
          // so no need to set the files on the command, because then mercurial will complain
        }
      }
      else {
        if (selectedFiles.isEmpty()) {  // nothing to commit. Aborting here, because otherwise 'hg commit' without specifying files will commit all files.
          return exceptions;
        }
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
    return new HgWorkingCopyRevisionsCommand(myProject).parents(repo).size() > 1;
  }

  private Set<HgFile> getChangedFilesNotInCommit(VirtualFile repo, Set<HgFile> selectedFiles) {
    List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(repo);

    HgStatusCommand statusCommand = new HgStatusCommand(myProject);
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

  public List<VcsException> commit(List<Change> changes,
    String preparedComment) {
    return commit(changes, preparedComment, NullableFunction.NULL);
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    HgUtil.removeFilesFromVcs(myProject, files);
    return null;
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    HgAddCommand command = new HgAddCommand(myProject);
    for (VirtualFile file : files) {
      VirtualFile vcsRoot = VcsUtil.getVcsRootFor(myProject, file);
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

  /**
   * Groups the changes by repository roots.
   * @param changes the list of all changes.
   * @return Changes grouped by repository roots.
   */
  private Map<VirtualFile, List<Change>> groupChangesByRepository(List<Change> changes) {
    final Map<VirtualFile, List<Change>> result = new HashMap<VirtualFile, List<Change>>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      final ContentRevision beforeRevision = change.getBeforeRevision();
      assert beforeRevision != null || afterRevision != null; // nothing-to-nothing change cannot happen.
      final FilePath filePath = (afterRevision != null) ? afterRevision.getFile() : beforeRevision.getFile();

      final VirtualFile repo = VcsUtil.getVcsRootFor(myProject, filePath);
      if (repo == null || filePath.isDirectory()) {
        continue;
      }

      List<Change> repoChanges = result.get(repo);
      if (repoChanges == null) {
        repoChanges = new ArrayList<Change>();
        result.put(repo, repoChanges);
      }
      repoChanges.add(change);
    }
    return result;
  }

}
