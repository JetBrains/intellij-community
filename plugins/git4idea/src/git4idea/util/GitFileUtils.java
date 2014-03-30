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
package git4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitUtil;
import git4idea.commands.GitBinaryHandler;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * File utilities for the git
 */
public class GitFileUtils {
  private static final Logger LOG = Logger.getInstance(GitFileUtils.class.getName());

  /**
   * The private constructor for static utility class
   */
  private GitFileUtils() {
    // do nothing
  }

  /**
   * Delete files
   *
   * @param project the project
   * @param root    a vcs root
   * @param files   files to delete
   * @return a result of operation
   * @throws VcsException in case of git problem
   */

  public static void delete(Project project, VirtualFile root, Collection<FilePath> files, String... additionalOptions)
    throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.RM);
      handler.addParameters(additionalOptions);
      handler.endOptions();
      handler.addParameters(paths);
      handler.run();
    }
  }

  /**
   * Delete files
   *
   * @param project the project
   * @param root    a vcs root
   * @param files   files to delete
   * @return a result of operation
   * @throws VcsException in case of git problem
   */
  public static void deleteFiles(Project project, VirtualFile root, Collection<VirtualFile> files, String... additionalOptions)
    throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkFiles(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.RM);
      handler.addParameters(additionalOptions);
      handler.endOptions();
      handler.addParameters(paths);
      handler.run();
    }
  }

  /**
   * Delete files
   *
   * @param project the project
   * @param root    a vcs root
   * @param files   files to delete
   * @return a result of operation
   * @throws VcsException in case of git problem
   */
  public static void deleteFiles(Project project, VirtualFile root, VirtualFile... files) throws VcsException {
    deleteFiles(project, root, Arrays.asList(files));
  }

  /**
   * Delete files from cache (mark untracked)
   *
   * @param project the project
   * @param root    a vcs root
   * @param files   files to delete
   * @return a result of operation
   * @throws VcsException in case of git problem
   */
  public static void deleteFilesFromCache(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<VirtualFile> files)
    throws VcsException {
    deleteFiles(project, root, files, "--cached");
    updateUntrackedFilesHolderOnFileRemove(project, root, files);
  }

  /**
   * Add files to the Git index.
   */
  public static void addFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<VirtualFile> files)
    throws VcsException {
    addPaths(project, root, VcsFileUtil.chunkFiles(root, files));
    updateUntrackedFilesHolderOnFileAdd(project, root, files);
  }

  private static void updateUntrackedFilesHolderOnFileAdd(@NotNull Project project, @NotNull VirtualFile root,
                                                          @NotNull Collection<VirtualFile> addedFiles) {
    final GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root.getPresentableUrl());
      return;
    }
    repository.getUntrackedFilesHolder().remove(addedFiles);
  }

  private static void updateUntrackedFilesHolderOnFileRemove(@NotNull Project project, @NotNull VirtualFile root,
                                                             @NotNull Collection<VirtualFile> removedFiles) {
    final GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root.getPresentableUrl());
      return;
    }
    repository.getUntrackedFilesHolder().add(removedFiles);
  }

  /**
   * Add files to the Git index.
   */
  public static void addFiles(Project project, VirtualFile root, VirtualFile... files) throws VcsException {
    addFiles(project, root, Arrays.asList(files));
  }

  /**
   * Add files to the Git index.
   */
  public static void addPaths(@NotNull Project project, @NotNull VirtualFile root,
                              @NotNull Collection<FilePath> files) throws VcsException {
    addPaths(project, root, VcsFileUtil.chunkPaths(root, files));
    updateUntrackedFilesHolderOnFileAdd(project, root, getVirtualFilesFromFilePaths(files));
  }

  @NotNull
  private static Collection<VirtualFile> getVirtualFilesFromFilePaths(@NotNull Collection<FilePath> paths) {
    Collection<VirtualFile> files = new ArrayList<VirtualFile>(paths.size());
    for (FilePath path : paths) {
      VirtualFile file = path.getVirtualFile();
      if (file != null) {
        files.add(file);
      }
    }
    return files;
  }

  private static void addPaths(@NotNull Project project, @NotNull VirtualFile root,
                               @NotNull List<List<String>> chunkedPaths) throws VcsException {
    for (List<String> paths : chunkedPaths) {
      paths = excludeIgnoredFiles(project, root, paths);

      if (paths.isEmpty()) {
        continue;
      }
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.ADD);
      handler.addParameters("--ignore-errors");
      handler.endOptions();
      handler.addParameters(paths);
      handler.run();
    }
  }

  @NotNull
  private static List<String> excludeIgnoredFiles(@NotNull Project project, @NotNull VirtualFile root,
                                                  @NotNull List<String> paths) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.LS_FILES);
    handler.setSilent(true);
    handler.addParameters("--ignored", "--others", "--exclude-standard");
    handler.endOptions();
    handler.addParameters(paths);
    String output = handler.run();

    List<String> nonIgnoredFiles = new ArrayList<String>(paths.size());
    Set<String> ignoredPaths = new HashSet<String>(Arrays.asList(StringUtil.splitByLines(output)));
    for (String pathToCheck : paths) {
      if (!ignoredPaths.contains(pathToCheck)) {
        nonIgnoredFiles.add(pathToCheck);
      }
    }
    return nonIgnoredFiles;
  }

  /**
   * Get file content for the specific revision
   *
   * @param project      the project
   * @param root         the vcs root
   * @param revisionOrBranch     the revision to find path in or branch 
   * @param relativePath
   * @return the content of file if file is found, null if the file is missing in the revision
   * @throws VcsException if there is a problem with running git
   */
  public static byte[] getFileContent(Project project, VirtualFile root, String revisionOrBranch, String relativePath) throws VcsException {
    GitBinaryHandler h = new GitBinaryHandler(project, root, GitCommand.SHOW);
    h.setSilent(true);
    h.addParameters(revisionOrBranch + ":" + relativePath);
    return h.run();
  }

  public static String stripFileProtocolPrefix(String path) {
    final String FILE_PROTOCOL = "file://";
    if (path.startsWith(FILE_PROTOCOL)) {
      return path.substring(FILE_PROTOCOL.length());
    }
    return path;
  }

  /**
   * Checks if two file paths are different only by case in a case insensitive OS.
   * @return true if the difference between paths should probably be ignored, i.e. the OS is case-insensitive, and case is the only
   *         difference between paths.
   */
  public static boolean shouldIgnoreCaseChange(@NotNull String onePath, @NotNull String secondPath) {
    return !SystemInfo.isFileSystemCaseSensitive && onlyCaseChanged(onePath, secondPath);
  }
  
  private static boolean onlyCaseChanged(@NotNull String one, @NotNull String second) {
    return one.compareToIgnoreCase(second) == 0;
  }
  

}
