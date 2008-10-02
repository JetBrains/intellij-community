/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitCommand;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Git rollback/revert environment
 */
public class GitRollbackEnvironment implements RollbackEnvironment {
  /**
   * The project
   */
  private final Project project;
  /**
   * GIT settings
   */
  private final GitVcsSettings settings;

  /**
   * A constructor
   *
   * @param project  the context project
   * @param settings the GIT settings in the project
   */
  public GitRollbackEnvironment(@NotNull Project project, @NotNull GitVcsSettings settings) {
    this.project = project;
    this.settings = settings;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getRollbackOperationName() {
    return GitBundle.getString("revert.action.name");
  }

  /**
   * {@inheritDoc}
   */
  public List<VcsException> rollbackModifiedWithoutCheckout(@NotNull List<VirtualFile> files) {
    throw new UnsupportedOperationException("Explicit file checkout is not supported by GIT.");
  }

  /**
   * {@inheritDoc}
   */
  public List<VcsException> rollbackMissingFileDeletion(@NotNull List<FilePath> files) {
    throw new UnsupportedOperationException("Missing file delete is not reported by GIT.");
  }

  /**
   * {@inheritDoc}
   */
  public void rollbackIfUnchanged(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException("Explicit file checkout is not supported by GIT.");
  }

  /**
   * {@inheritDoc}
   */
  public List<VcsException> rollbackChanges(@NotNull List<Change> changes) {
    List<VcsException> result = new ArrayList<VcsException>();
    HashMap<VirtualFile, List<FilePath>> toUnindex = new HashMap<VirtualFile, List<FilePath>>();
    HashMap<VirtualFile, List<FilePath>> toRevert = new HashMap<VirtualFile, List<FilePath>>();
    List<FilePath> toDelete = new ArrayList<FilePath>();
    // collect changes to revert
    for (Change c : changes) {
      switch (c.getType()) {
        case NEW:
          // note that this the only change that could happen
          // for HEAD-less working directories.
          registerFile(project, toUnindex, c.getAfterRevision().getFile());
          break;
        case MOVED:
          registerFile(project, toRevert, c.getBeforeRevision().getFile());
          registerFile(project, toUnindex, c.getAfterRevision().getFile());
          toDelete.add(c.getAfterRevision().getFile());
          break;
        case MODIFICATION:
          // note that changes are also removed from index, if they got into index somehow
          registerFile(project, toUnindex, c.getBeforeRevision().getFile());
          registerFile(project, toRevert, c.getBeforeRevision().getFile());
          break;
        case DELETED:
          registerFile(project, toRevert, c.getBeforeRevision().getFile());
          break;
      }
    }
    // unindex files
    for (Map.Entry<VirtualFile, List<FilePath>> entry : toUnindex.entrySet()) {
      GitCommand cmd = new GitCommand(project, settings, entry.getKey());
      try {
        cmd.unindex(entry.getValue());
      }
      catch (VcsException e) {
        result.add(e);
      }
    }
    // delete files
    for (FilePath file : toDelete) {
      try {
        final File ioFile = file.getIOFile();
        if (ioFile.exists()) {
          if (!ioFile.delete()) {
            result.add(new VcsException("Unable to delete file: " + file));
          }
        }
      }
      catch (Exception e) {
        result.add(new VcsException("Unable to delete file: " + file, e));
      }
    }
    // revert files from HEAD
    for (Map.Entry<VirtualFile, List<FilePath>> entry : toRevert.entrySet()) {
      GitCommand cmd = new GitCommand(project, settings, entry.getKey());
      try {
        cmd.revert(entry.getValue());
      }
      catch (VcsException e) {
        result.add(e);
      }
    }
    return result;
  }

  /**
   * Register file in the map under uppropriate root
   *
   * @param file  a file to register
   * @param files a map to use
   */
  private static void registerFile(Project project, Map<VirtualFile, List<FilePath>> files, FilePath file) {
    final VirtualFile root = GitUtil.getVcsRoot(project, file);
    List<FilePath> paths = files.get(root);
    if (paths == null) {
      paths = new ArrayList<FilePath>();
      files.put(root, paths);
    }
    paths.add(file);
  }
}
