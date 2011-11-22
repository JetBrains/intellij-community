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
package git4idea.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.repo.GitUntrackedFilesHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * File utilities for the git
 */
public class GitFileUtils {

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
      handler.setNoSSH(true);
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
  public static void deleteFiles(Project project, VirtualFile root, List<VirtualFile> files) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkFiles(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.RM);
      handler.endOptions();
      handler.addParameters(paths);
      handler.setNoSSH(true);
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
   * Add/index files
   *
   * @param project the project
   * @param root    a vcs root
   * @param files   files to add
   * @return a result of operation
   * @throws VcsException in case of git problem
   */
  public static void addFiles(Project project, VirtualFile root, Collection<VirtualFile> files) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkFiles(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.ADD);
      handler.endOptions();
      handler.addParameters(paths);
      handler.setNoSSH(true);
      handler.run();
    }
    final GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    if (repository != null) {
      repository.getUntrackedFilesHolder().remove(files);
    }
  }

  /**
   * Add/index files
   *
   * @param project the project
   * @param root    a vcs root
   * @param files   files to add
   * @return a result of operation
   * @throws VcsException in case of git problem
   */
  public static void addFiles(Project project, VirtualFile root, VirtualFile... files) throws VcsException {
    addFiles(project, root, Arrays.asList(files));
  }

  /**
   * Add/index files
   *
   * @param project the project
   * @param root    a vcs root
   * @param files   files to add
   * @return a result of operation
   * @throws VcsException in case of git problem
   */
  public static void addPaths(Project project, VirtualFile root, Collection<FilePath> files) throws VcsException {
    final GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
    final GitUntrackedFilesHolder untrackedFilesHolder = (repository == null ? null : repository.getUntrackedFilesHolder());

    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.ADD);
      handler.endOptions();
      handler.addParameters(paths);
      handler.setNoSSH(true);
      handler.run();
    }

    for (FilePath path : files) {
      VirtualFile vf = path.getVirtualFile();
      if (untrackedFilesHolder != null && vf != null) {
        untrackedFilesHolder.remove(vf);
      }
    }
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
    h.setNoSSH(true);
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
