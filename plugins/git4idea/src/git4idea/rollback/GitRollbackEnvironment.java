// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rollback;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.components.Service;
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
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.index.vfs.GitIndexFileSystemRefresher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitUntrackedFilesHolder;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

@Service
public final class GitRollbackEnvironment implements RollbackEnvironment {
  @NotNull private final Project myProject;

  public GitRollbackEnvironment(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  public String getRollbackOperationName() {
    return GitBundle.message("git.rollback");
  }

  @Override
  public void rollbackModifiedWithoutCheckout(List<? extends VirtualFile> files,
                                              List<? super VcsException> exceptions,
                                              RollbackProgressListener listener) {
    throw new UnsupportedOperationException("Explicit file checkout is not supported by Git.");
  }

  @Override
  public void rollbackMissingFileDeletion(List<? extends FilePath> files,
                                          List<? super VcsException> exceptions,
                                          RollbackProgressListener listener) {
    throw new UnsupportedOperationException("Missing file delete is not reported by Git.");
  }

  @Override
  public void rollbackIfUnchanged(@NotNull VirtualFile file) {
    // do nothing
  }

  @Override
  public void rollbackChanges(List<? extends Change> changes,
                              List<VcsException> exceptions,
                              @NotNull RollbackProgressListener listener) {
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
        File ioFile = file.getIOFile();
        if (ioFile.exists()) {
          if (!ioFile.delete()) {
            exceptions.add(new VcsException(GitBundle.message("error.cannot.delete.file", file.getPresentableUrl())));
          }
        }
      }
      catch (Exception e) {
        exceptions.add(new VcsException(GitBundle.message("error.cannot.delete.file", file.getPresentableUrl()), e));
      }
    }
    // revert files from HEAD
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, getRollbackOperationName())) {
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
    GitIndexFileSystemRefresher.refreshFilePaths(myProject, toUnindex);

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
  public void revert(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.CHECKOUT);
      handler.addParameters("HEAD");
      handler.endOptions();
      handler.addParameters(paths);
      Git.getInstance().runCommand(handler).throwOnError();
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
  private void unindex(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files, boolean toUnversioned) throws VcsException {
    GitFileUtils.deletePaths(myProject, root, files, "--cached", "-f"); //NON-NLS

    if (toUnversioned) {
      GitRepository repo = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(root);
      GitUntrackedFilesHolder untrackedFilesHolder = (repo == null ? null : repo.getUntrackedFilesHolder());
      for (FilePath path : files) {
        if (untrackedFilesHolder != null) {
          untrackedFilesHolder.add(path);
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
  private void registerFile(@NotNull Map<VirtualFile, List<FilePath>> files,
                            @NotNull FilePath file,
                            @NotNull List<? super VcsException> exceptions) {
    try {
      VirtualFile root = GitUtil.getRootForFile(myProject, file);
      List<FilePath> paths = files.computeIfAbsent(root, key -> new ArrayList<>());
      paths.add(file);
    }
    catch (VcsException e) {
      exceptions.add(e);
    }
  }
}
