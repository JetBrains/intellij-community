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
  public static String delete(Project project, VirtualFile root, List<FilePath> files) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.RM);
    handler.endOptions();
    handler.addRelativePaths(files);
    handler.setNoSSH(true);
    return handler.run();
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
  public static String deleteFiles(Project project, VirtualFile root, List<VirtualFile> files) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.RM);
    handler.endOptions();
    handler.addRelativeFiles(files);
    handler.setNoSSH(true);
    return handler.run();
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
  public static String deleteFiles(Project project, VirtualFile root, VirtualFile... files) throws VcsException {
    return deleteFiles(project, root, Arrays.asList(files));
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
  public static String addFiles(Project project, VirtualFile root, Collection<VirtualFile> files) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.ADD);
    handler.endOptions();
    handler.addRelativeFiles(files);
    handler.setNoSSH(true);
    return handler.run();
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
  public static String addFiles(Project project, VirtualFile root, VirtualFile... files) throws VcsException {
    return addFiles(project, root, Arrays.asList(files));
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
  public static String addPaths(Project project, VirtualFile root, Collection<FilePath> files) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitHandler.ADD);
    handler.endOptions();
    handler.addRelativePaths(files);
    handler.setNoSSH(true);
    return handler.run();
  }
}
