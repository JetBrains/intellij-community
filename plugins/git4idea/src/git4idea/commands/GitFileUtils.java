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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * File utilities for the git
 */
public class GitFileUtils {
  /**
   * If multiple paths are specified on the command line, this limit is used to split paths into chunks.
   * The limit is less than OS limit to leave space to quoting, spaces, charset conversion, and commands arguments.
   */
  public static final int FILE_PATH_LIMIT = 7600;

  /**
   * The private constructor for static utility class
   */
  private GitFileUtils() {
    // do nothing
  }

  /**
   * Chunk paths on the command line
   *
   * @param files the paths to chunk
   * @return the a list of list of relative paths
   */
  public static List<List<String>> chunkRelativePaths(List<String> files) {
    ArrayList<List<String>> rc = new ArrayList<List<String>>();
    int start = 0;
    int size = 0;
    int i = 0;
    for (; i < files.size(); i++) {
      String p = files.get(i);
      if (size + p.length() > FILE_PATH_LIMIT) {
        if (start == i) {
          rc.add(files.subList(i, i + 1));
          start = i + 1;
        }
        else {
          rc.add(files.subList(start, i));
          start = i;
        }
        size = 0;
      }
      else {
        size += p.length();
      }
    }
    if (start != files.size()) {
      rc.add(files.subList(start, i));
    }
    return rc;
  }

  /**
   * The chunk paths
   *
   * @param root  the vcs root
   * @param files the file list
   * @return chunked relative paths
   */
  public static List<List<String>> chunkPaths(VirtualFile root, Collection<FilePath> files) {
    return chunkRelativePaths(GitUtil.toRelativePaths(root, files));
  }

  /**
   * The chunk paths
   *
   * @param root  the vcs root
   * @param files the file list
   * @return chunked relative paths
   */
  public static List<List<String>> chunkFiles(VirtualFile root, Collection<VirtualFile> files) {
    return chunkRelativePaths(GitUtil.toRelativeFiles(root, files));
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
    for (List<String> paths : chunkPaths(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.RM);
      handler.addParameters(additionalOptions);
      handler.endOptions();
      handler.addParameters(paths);
      handler.setNoSSH(true);
      handler.run();
    }
  }

  public static void cherryPick(final Project project, final VirtualFile root, final String hash) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.CHERRY_PICK);
    handler.addParameters("-x", "-n", hash);
    handler.endOptions();
    //handler.addRelativePaths(new FilePathImpl(root));
    handler.setNoSSH(true);
    handler.run();
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
    for (List<String> paths : chunkFiles(root, files)) {
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
    for (List<String> paths : chunkFiles(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.ADD);
      handler.endOptions();
      handler.addParameters(paths);
      handler.setNoSSH(true);
      handler.run();
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
    for (List<String> paths : chunkPaths(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.ADD);
      handler.endOptions();
      handler.addParameters(paths);
      handler.setNoSSH(true);
      handler.run();
    }
  }
}
