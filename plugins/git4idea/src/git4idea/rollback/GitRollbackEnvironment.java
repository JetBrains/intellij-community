/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.rollback;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitFileUtils;
import git4idea.commands.GitSimpleHandler;
import git4idea.i18n.GitBundle;
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
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project the context project
   */
  public GitRollbackEnvironment(@NotNull Project project) {
    myProject = project;
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
  public void rollbackModifiedWithoutCheckout(@NotNull List<VirtualFile> files,
                                              final List<VcsException> exceptions,
                                              final RollbackProgressListener listener) {
    throw new UnsupportedOperationException("Explicit file checkout is not supported by GIT.");
  }

  /**
   * {@inheritDoc}
   */
  public void rollbackMissingFileDeletion(@NotNull List<FilePath> files,
                                          final List<VcsException> exceptions,
                                          final RollbackProgressListener listener) {
    throw new UnsupportedOperationException("Missing file delete is not reported by GIT.");
  }

  /**
   * {@inheritDoc}
   */
  public void rollbackIfUnchanged(@NotNull VirtualFile file) {
    // do nothing
  }

  /**
   * {@inheritDoc}
   */
  public void rollbackChanges(@NotNull List<Change> changes,
                              final List<VcsException> exceptions,
                              @NotNull final RollbackProgressListener listener) {
    HashMap<VirtualFile, List<FilePath>> toUnindex = new HashMap<VirtualFile, List<FilePath>>();
    HashMap<VirtualFile, List<FilePath>> toRevert = new HashMap<VirtualFile, List<FilePath>>();
    List<FilePath> toDelete = new ArrayList<FilePath>();

    listener.determinate();
    // collect changes to revert
    for (Change c : changes) {
      switch (c.getType()) {
        case NEW:
          // note that this the only change that could happen
          // for HEAD-less working directories.
          registerFile(toUnindex, c.getAfterRevision().getFile(), exceptions);
          break;
        case MOVED:
          registerFile(toRevert, c.getBeforeRevision().getFile(), exceptions);
          registerFile(toUnindex, c.getAfterRevision().getFile(), exceptions);
          toDelete.add(c.getAfterRevision().getFile());
          break;
        case MODIFICATION:
          // note that changes are also removed from index, if they got into index somehow
          registerFile(toUnindex, c.getBeforeRevision().getFile(), exceptions);
          registerFile(toRevert, c.getBeforeRevision().getFile(), exceptions);
          break;
        case DELETED:
          registerFile(toRevert, c.getBeforeRevision().getFile(), exceptions);
          break;
      }
    }
    // unindex files
    for (Map.Entry<VirtualFile, List<FilePath>> entry : toUnindex.entrySet()) {
      listener.accept(entry.getValue());
      try {
        unindex(entry.getKey(), entry.getValue());
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }
    // delete files
    for (FilePath file : toDelete) {
      listener.accept(file);
      try {
        final File ioFile = file.getIOFile();
        if (ioFile.exists()) {
          if (!ioFile.delete()) {
            //noinspection ThrowableInstanceNeverThrown
            exceptions.add(new VcsException("Unable to delete file: " + file));
          }
        }
      }
      catch (Exception e) {
        //noinspection ThrowableInstanceNeverThrown
        exceptions.add(new VcsException("Unable to delete file: " + file, e));
      }
    }
    // revert files from HEAD
    for (Map.Entry<VirtualFile, List<FilePath>> entry : toRevert.entrySet()) {
      listener.accept(entry.getValue());
      try {
        revert(entry.getKey(), entry.getValue());
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }
  }

  /**
   * Reverts the list of files we are passed.
   *
   * @param root  the VCS root
   * @param files The array of files to revert.
   * @throws VcsException Id it breaks.
   */
  public void revert(final VirtualFile root, final List<FilePath> files) throws VcsException {
    for (List<String> paths : GitFileUtils.chunkPaths(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(myProject, root, GitCommand.CHECKOUT);
      handler.setNoSSH(true);
      handler.addParameters("HEAD");
      handler.endOptions();
      handler.addParameters(paths);
      handler.run();
    }
  }

  /**
   * Remove file paths from index (git remove --cached).
   *
   * @param root  a git root
   * @param files files to remove from index. @throws VcsException if there is a problem with running command
   * @throws VcsException if there is a problem with running git
   */
  private void unindex(final VirtualFile root, final List<FilePath> files) throws VcsException {
    GitFileUtils.delete(myProject, root, files, "--cached", "-f");
  }


  /**
   * Register file in the map under appropriate root
   *
   * @param files      a map to use
   * @param file       a file to register
   * @param exceptions the list of exceptions to update
   */
  private static void registerFile(Map<VirtualFile, List<FilePath>> files, FilePath file, List<VcsException> exceptions) {
    final VirtualFile root;
    try {
      root = GitUtil.getGitRoot(file);
    }
    catch (VcsException e) {
      exceptions.add(e);
      return;
    }
    List<FilePath> paths = files.get(root);
    if (paths == null) {
      paths = new ArrayList<FilePath>();
      files.put(root, paths);
    }
    paths.add(file);
  }

  /**
   * Get instance of the service
   *
   * @param project a context project
   * @return a project-specific instance of the service
   */
  public static GitRollbackEnvironment getInstance(final Project project) {
    return ServiceManager.getService(project, GitRollbackEnvironment.class);
  }
}
