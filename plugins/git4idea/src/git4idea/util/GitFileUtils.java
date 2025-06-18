// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.commands.*;
import git4idea.config.GitExecutableManager;
import git4idea.config.GitVersion;
import git4idea.index.GitIndexUtil;
import git4idea.index.vfs.GitIndexFileSystemRefresher;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.util.*;

import static com.intellij.util.ArrayUtil.EMPTY_BYTE_ARRAY;
import static git4idea.config.GitVersionSpecialty.CAT_FILE_SUPPORTS_FILTERS;
import static git4idea.config.GitVersionSpecialty.CAT_FILE_SUPPORTS_TEXTCONV;

public final class GitFileUtils {

  private static final Logger LOG = Logger.getInstance(GitFileUtils.class);
  public static final String READ_CONTENT_WITH = "git.read.content.with";

  private GitFileUtils() {
  }

  public static void deletePaths(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends FilePath> files,
                                 @NonNls String @NotNull ... additionalOptions) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      doDelete(project, root, paths, additionalOptions);
    }
  }

  public static void deleteFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files,
                                 @NonNls String @NotNull ... additionalOptions) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkFiles(root, files)) {
      doDelete(project, root, paths, additionalOptions);
    }
  }

  public static void deleteFiles(@NotNull Project project, @NotNull VirtualFile root, VirtualFile @NotNull ... files) throws VcsException {
    deleteFiles(project, root, Arrays.asList(files));
  }

  private static void doDelete(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<String> paths,
                               @NonNls String @NotNull ... additionalOptions) throws VcsException {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RM);
    handler.addParameters(additionalOptions);
    handler.endOptions();
    handler.addParameters(paths);
    Git.getInstance().runCommand(handler).throwOnError();
  }

  public static void deleteFilesFromCache(@NotNull Project project,
                                          @NotNull VirtualFile root,
                                          @NotNull Collection<? extends VirtualFile> files)
    throws VcsException {
    List<FilePath> paths = ContainerUtil.map(files, VcsUtil::getFilePath);
    deletePaths(project, root, paths, "--cached");
    updateUntrackedFilesHolderOnFileRemove(project, root, paths);
  }

  public static void addFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files)
    throws VcsException {
    List<FilePath> paths = ContainerUtil.mapNotNull(files, VcsUtil::getFilePath);
    addPaths(project, root, paths);
  }

  public static void addFilesForce(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files)
    throws VcsException {
    List<FilePath> paths = ContainerUtil.mapNotNull(files, VcsUtil::getFilePath);
    addPathsForce(project, root, paths);
  }

  private static void updateUntrackedFilesHolderOnFileAdd(@NotNull Project project, @NotNull VirtualFile root,
                                                          @NotNull Collection<? extends FilePath> addedFiles) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.warn("Repository not found for root " + root.getPresentableUrl());
      return;
    }
    repository.getUntrackedFilesHolder().removeUntracked(addedFiles);
  }

  private static void updateIgnoredFilesHolderOnFileAdd(@NotNull Project project, @NotNull VirtualFile root,
                                                        @NotNull Collection<? extends FilePath> addedFiles) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.warn("Repository not found for root " + root.getPresentableUrl());
      return;
    }
    repository.getIgnoredFilesHolder().removeIgnoredFiles(addedFiles);
  }

  private static void updateUntrackedFilesHolderOnFileRemove(@NotNull Project project, @NotNull VirtualFile root,
                                                             @NotNull Collection<? extends FilePath> removedFiles) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.warn("Repository not found for root " + root.getPresentableUrl());
      return;
    }
    repository.getUntrackedFilesHolder().addUntracked(removedFiles);
  }

  private static void updateUntrackedFilesHolderOnFileReset(@NotNull Project project, @NotNull VirtualFile root,
                                                            @NotNull Collection<? extends FilePath> resetFiles) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.warn("Repository not found for root " + root.getPresentableUrl());
      return;
    }
    repository.getUntrackedFilesHolder().markPossiblyUntracked(resetFiles);
  }

  public static void addFiles(@NotNull Project project, @NotNull VirtualFile root, VirtualFile @NotNull ... files) throws VcsException {
    addFiles(project, root, Arrays.asList(files));
  }

  public static void addPaths(@NotNull Project project, @NotNull VirtualFile root,
                              @NotNull Collection<? extends FilePath> paths) throws VcsException {
    addPaths(project, root, paths, false);
  }

  public static void addPaths(@NotNull Project project, @NotNull VirtualFile root,
                              @NotNull Collection<? extends FilePath> files, boolean force) throws VcsException {
    addPaths(project, root, files, force, !force);
  }

  public static void addPathsToIndex(@NotNull Project project, @NotNull VirtualFile root,
                                     @NotNull Collection<? extends FilePath> files) throws VcsException {
    for (FilePath file : files) {
      GitIndexUtil.write(project, root, file, new ByteArrayInputStream(EMPTY_BYTE_ARRAY), false, true);
    }

    updateAndRefresh(project, root, files, false);
  }

  public static void addPaths(@NotNull Project project, @NotNull VirtualFile root,
                              @NotNull Collection<? extends FilePath> files,
                              boolean force, boolean filterOutIgnored) throws VcsException {
    addPaths(project, root, files, force, filterOutIgnored, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public static void addPaths(@NotNull Project project, @NotNull VirtualFile root,
                              @NotNull Collection<? extends FilePath> files,
                              boolean force, boolean filterOutIgnored,
                              String... additionalOptions) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      addPathsImpl(project, root, paths, force, filterOutIgnored, additionalOptions);
    }
    updateAndRefresh(project, root, files, force);
  }

  private static void updateAndRefresh(@NotNull Project project,
                                       @NotNull VirtualFile root,
                                       @NotNull Collection<? extends FilePath> files,
                                       boolean updateIgnoredHolders) {
    updateUntrackedFilesHolderOnFileAdd(project, root, files);
    if (updateIgnoredHolders) {
      updateIgnoredFilesHolderOnFileAdd(project, root, files);
    }
    GitIndexFileSystemRefresher.refreshFilePaths(project, files);
  }

  public static void addPathsForce(@NotNull Project project, @NotNull VirtualFile root,
                                   @NotNull Collection<? extends FilePath> files) throws VcsException {
    addPaths(project, root, files, true, false);
  }

  private static void addPathsImpl(@NotNull Project project, @NotNull VirtualFile root,
                                   @NotNull List<String> paths,
                                   boolean force, boolean filterOutIgnored,
                                   String... additionalOptions) throws VcsException {
    if (filterOutIgnored) {
      paths = excludeIgnoredFiles(project, root, paths);
      if (paths.isEmpty()) return;
    }

    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.ADD);
    handler.addParameters("--ignore-errors", "-A");
    if (force) handler.addParameters("-f");
    handler.addParameters(additionalOptions);
    handler.endOptions();
    handler.addParameters(paths);
    Git.getInstance().runCommand(handler).throwOnError();
  }

  private static @NotNull List<String> excludeIgnoredFiles(@NotNull Project project, @NotNull VirtualFile root,
                                                           @NotNull List<String> paths) throws VcsException {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.LS_FILES);
    handler.setSilent(true);
    handler.addParameters("--ignored", "--others", "--exclude-standard");
    handler.endOptions();
    handler.addParameters(paths);
    String output = Git.getInstance().runCommand(handler).getOutputOrThrow();

    List<String> nonIgnoredFiles = new ArrayList<>(paths.size());
    Set<String> ignoredPaths = new HashSet<>(Arrays.asList(StringUtil.splitByLines(output)));
    for (String pathToCheck : paths) {
      if (!ignoredPaths.contains(pathToCheck)) {
        nonIgnoredFiles.add(pathToCheck);
      }
    }
    return nonIgnoredFiles;
  }

  public static void resetPaths(@NotNull Project project, @NotNull VirtualFile root,
                                @NotNull Collection<? extends FilePath> files) throws VcsException {
    for (List<String> filesChunk : VcsFileUtil.chunkPaths(root, files)) {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RESET);
      handler.endOptions();
      handler.addParameters(filesChunk);
      Git.getInstance().runCommand(handler).throwOnError();
    }
    updateUntrackedFilesHolderOnFileReset(project, root, files);
    GitIndexFileSystemRefresher.refreshFilePaths(project, files);
  }

  public static void revertUnstagedPaths(@NotNull Project project, @NotNull VirtualFile root,
                                         @NotNull List<? extends FilePath> files) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.CHECKOUT);
      handler.endOptions();
      handler.addParameters(paths);
      Git.getInstance().runCommand(handler).throwOnError();
    }
  }

  public static void restoreStagedAndWorktree(@NotNull Project project,
                                              @NotNull VirtualFile root,
                                              @NotNull List<FilePath> files,
                                              @NotNull String source)
    throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RESTORE);
      handler.addParameters("--staged", "--worktree", "--source=" + source);
      handler.endOptions();
      handler.addParameters(paths);
      Git.getInstance().runCommand(handler).throwOnError();
    }
  }

  /**
   * Get file content for the specific revision
   *
   * @param project          the project
   * @param root             the vcs root
   * @param revisionOrBranch the revision to find path in or branch
   * @return the content of file if file is found
   * @throws VcsException if there is a problem with running git
   */
  public static byte @NotNull [] getFileContent(@Nullable Project project,
                                                @NotNull VirtualFile root,
                                                @NotNull @NonNls String revisionOrBranch,
                                                @NotNull @NonNls String relativePath) throws VcsException {
    GitBinaryHandler h = new GitBinaryHandler(project, root, GitCommand.CAT_FILE);
    h.setSilent(true);
    addTextConvParameters(project, h, true);
    h.addParameters(revisionOrBranch + ":" + relativePath);
    return h.run();
  }

  public static void addTextConvParameters(@Nullable Project project, @NotNull GitBinaryHandler h, boolean addp) {
    addTextConvParameters(GitExecutableManager.getInstance().tryGetVersion(project, h.getExecutable()), h, addp);
  }

  public static void addTextConvParameters(@Nullable GitVersion version, @NotNull GitBinaryHandler h, boolean addp) {
    version = ObjectUtils.chooseNotNull(version, GitVersion.NULL);
    GitTextConvMode mode = AdvancedSettings.getEnum(READ_CONTENT_WITH, GitTextConvMode.class);

    if (mode == GitTextConvMode.FILTERS && CAT_FILE_SUPPORTS_FILTERS.existsIn(version)) {
      h.addParameters("--filters");
      return;
    }
    if (mode == GitTextConvMode.TEXTCONV && CAT_FILE_SUPPORTS_TEXTCONV.existsIn(version)) {
      h.addParameters("--textconv");
      return;
    }

    // '-p' is not needed with '--batch' parameter
    if (addp) {
      h.addParameters("-p");
    }
  }
}
