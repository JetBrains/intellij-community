/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitUntrackedFilesHolder;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * Git rollback/revert environment
 */
public class GitRollbackEnvironment implements RollbackEnvironment {
  private final Project myProject;

  public GitRollbackEnvironment(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public String getRollbackOperationName() {
    return GitBundle.getString("revert.action.name");
  }

  public void rollbackModifiedWithoutCheckout(@NotNull List<VirtualFile> files,
                                              final List<VcsException> exceptions,
                                              final RollbackProgressListener listener) {
    throw new UnsupportedOperationException("Explicit file checkout is not supported by GIT.");
  }

  public void rollbackMissingFileDeletion(@NotNull List<FilePath> files,
                                          final List<VcsException> exceptions,
                                          final RollbackProgressListener listener) {
    throw new UnsupportedOperationException("Missing file delete is not reported by GIT.");
  }

  public void rollbackIfUnchanged(@NotNull VirtualFile file) {
    // do nothing
  }

  public void rollbackChanges(@NotNull List<Change> changes,
                              final List<VcsException> exceptions,
                              @NotNull final RollbackProgressListener listener) {
    HashMap<VirtualFile, List<FilePath>> toUnindex = new HashMap<>();
    HashMap<VirtualFile, List<FilePath>> toUnversion = new HashMap<>();
    HashMap<VirtualFile, List<FilePath>> toRevert = new HashMap<>();
    List<FilePath> toDelete = new ArrayList<>();

    listener.determinate();
    // collect changes to revert
    for (Change c : changes) {
      switch (c.getType()) {
        case NEW:
          // note that this the only change that could happen
          // for HEAD-less working directories.
          registerFile(toUnversion, c.getAfterRevision().getFile(), exceptions);
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
        unindex(entry.getKey(), entry.getValue(), false);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }
    // unversion files
    for (Map.Entry<VirtualFile, List<FilePath>> entry : toUnversion.entrySet()) {
      listener.accept(entry.getValue());
      try {
        unindex(entry.getKey(), entry.getValue(), true);
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
    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
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
    finally {
      token.finish();
    }
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    HashSet<File> filesToRefresh = new HashSet<>();
    for (Change c : changes) {
      ContentRevision before = c.getBeforeRevision();
      if (before != null) {
        filesToRefresh.add(new File(before.getFile().getPath()));
      }
      ContentRevision after = c.getAfterRevision();
      if (after != null) {
        filesToRefresh.add(new File(after.getFile().getPath()));
      }
    }
    lfs.refreshIoFiles(filesToRefresh);

    for (GitRepository repo : GitUtil.getRepositoryManager(myProject).getRepositories()) {
      repo.update();
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
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.CHECKOUT);
      handler.addParameters("HEAD");
      handler.endOptions();
      handler.addParameters(paths);
      Git.getInstance().runCommand(handler).getOutputOrThrow();
    }
  }

  /**
   * Remove file paths from index (git remove --cached).
   *
   * @param root  a git root
   * @param files files to remove from index.
   * @param toUnversioned passed true if the file will be unversioned after unindexing, i.e. it was added before the revert operation.
   * @throws VcsException if there is a problem with running git
   */
  private void unindex(final VirtualFile root, final List<FilePath> files, boolean toUnversioned) throws VcsException {
    GitFileUtils.delete(myProject, root, files, "--cached", "-f");

    if (toUnversioned) {
      final GitRepository repo = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(root);
      final GitUntrackedFilesHolder untrackedFilesHolder = (repo == null ? null : repo.getUntrackedFilesHolder());
      for (FilePath path : files) {
        final VirtualFile vf = VcsUtil.getVirtualFile(path.getIOFile());
        if (untrackedFilesHolder != null && vf != null) {
          untrackedFilesHolder.add(vf);
        }
      }
    }
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
      paths = new ArrayList<>();
      files.put(root, paths);
    }
    paths.add(file);
  }

  public static void resetHardLocal(final Project project, final VirtualFile root) {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RESET);
    handler.addParameters("--hard");
    handler.endOptions();
    Git.getInstance().runCommand(handler);
  }
}
