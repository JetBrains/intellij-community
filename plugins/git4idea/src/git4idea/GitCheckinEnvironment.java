package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.actions.GitAdd;
import git4idea.actions.GitDelete;
import git4idea.commands.GitCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Git environment for commit operations.
 */
public class GitCheckinEnvironment implements CheckinEnvironment {
  private Project project;
  private GitVcsSettings settings;

  public GitCheckinEnvironment(@NotNull Project project, @NotNull GitVcsSettings settings) {
    this.project = project;
    this.settings = settings;
  }

  public void setProject(@NotNull Project project) {
    this.project = project;
  }

  public void setSettings(@NotNull GitVcsSettings settings) {
    this.settings = settings;
  }


  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    // TODO review it later
    return false;
  }

  @Nullable
  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel) {
    return null;
  }

  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return GitBundle.getString("git.default.commit.message");
  }

  public String getHelpId() {
    return null;
  }

  public String getCheckinOperationName() {
    return GitBundle.getString("commit.action.name");
  }

  @SuppressWarnings({"ConstantConditions"})
  public List<VcsException> commit(@NotNull List<Change> changes, @NotNull String message) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    Map<VirtualFile, List<Change>> sortedChanges = sortChangesByVcsRoot(changes);

    for (VirtualFile root : sortedChanges.keySet()) {
      GitCommand command = new GitCommand(project, settings, root);
      Set<FilePath> files = new HashSet<FilePath>();
      for (Change change : changes) {
        switch (change.getType()) {
          case NEW:
          case MODIFICATION:
            files.add(change.getAfterRevision().getFile());
            break;
          case DELETED:
            files.add(change.getBeforeRevision().getFile());
            break;
          case MOVED:
            files.add(change.getAfterRevision().getFile());
            files.add(change.getBeforeRevision().getFile());
            break;
          default:
            throw new IllegalStateException("Unknown change type: " + change.getType());
        }
      }
      try {
        command.commit(files.toArray(new FilePath[files.size()]), message);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }

    return exceptions;
  }

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    try {
      VirtualFile[] vfiles = new VirtualFile[files.size()];
      int count = 0;
      for (FilePath file : files) {
        vfiles[count++] = file.getVirtualFile();
      }
      GitDelete.deleteFiles(project, vfiles);
      return Collections.emptyList();
    }
    catch (VcsException e) {
      return Collections.singletonList(e);
    }
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    try {
      GitAdd.addFiles(project, files.toArray(new VirtualFile[files.size()]));
      return Collections.emptyList();
    }
    catch (VcsException e) {
      return Collections.singletonList(e);
    }
  }

  private Map<VirtualFile, List<Change>> sortChangesByVcsRoot(@NotNull List<Change> changes) {
    Map<VirtualFile, List<Change>> result = new HashMap<VirtualFile, List<Change>>();

    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      final ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        final FilePath filePath = afterRevision != null ? afterRevision.getFile() : beforeRevision.getFile();
        final VirtualFile vcsRoot = GitUtil.getVcsRoot(project, filePath);

        List<Change> changeList = result.get(vcsRoot);
        if (changeList == null) {
          changeList = new ArrayList<Change>();
          result.put(vcsRoot, changeList);
        }
        changeList.add(change);
      }
    }

    return result;
  }
}
