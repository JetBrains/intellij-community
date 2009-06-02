/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package git4idea.vfs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.*;
import git4idea.GitVcs;
import org.jetbrains.annotations.Nullable;

/**
 * The tracker for ".gitignore" and ".git/info/exclude" files. If it detects changes
 * in ignored files in the project it dirties the project state. The following changes
 * are detected:
 * <ul>
 * <li>Addition or removal of the ".git/info/exclude" or ".gitignore" files</li>
 * <li>The content change of ignore configuration (using modification date change)</li>
 * <li>VCS root change (the roots are rescanned, but files are not marked dirty)</li>
 * </ul>
 * The entire subdirectory is dirtied. The scanner assumes that the git repositories
 * are correctly configured. In the case of incorrect configuration some events could be
 * missed.
 */
public class GitIgnoreTracker {
  /**
   * The vcs manager that tracks content roots
   */
  private final ProjectLevelVcsManager myVcsManager;
  /**
   * The vcs instance
   */
  private final GitVcs myVcs;
  /**
   * The local exclude path
   */
  private static final String LOCAL_EXCLUDE = ".git/info/exclude";
  /**
   * The local exclude path
   */
  private static final String[] LOCAL_EXCLUDE_ARRAY = LOCAL_EXCLUDE.split("/");
  /**
   * The git folder
   */
  private static final String GIT_FOLDER = ".git";
  /**
   * Dirty scope manager
   */
  private final VcsDirtyScopeManager myDirtyScopeManager;
  /**
   * The listener for vcs events
   */
  private final VcsListener myVcsListener;
  /**
   * The listener for file events
   */
  private final MyFileListener myFileListener;

  /**
   * The constructor for service
   *
   * @param project the context project
   * @param vcs     the git vcs instance
   */
  public GitIgnoreTracker(Project project, GitVcs vcs) {
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(project);
    myVcsListener = new VcsListener() {
      public void directoryMappingChanged() {
        scan();
      }
    };
    myVcsManager.addVcsListener(myVcsListener);
    myFileListener = new MyFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myFileListener);
    scan();
  }

  /**
   * This method is invoked when component is started or when vcs root mapping changes.
   */
  public void scan() {
    VirtualFile[] contentRoots = myVcsManager.getRootsUnderVcs(myVcs);
    if (contentRoots == null || contentRoots.length == 0) {
      return;
    }
    for (VirtualFile root : contentRoots) {
      scanParents(root);
      // note that the component relies on root tracker to scan all children including .gitignore files.
    }
  }

  /**
   * Scan this root and parents in the search of .gitignore
   *
   * @param root the directory to scan
   */
  private static void scanParents(VirtualFile root) {
    VirtualFile meta = root.findChild(GIT_FOLDER);
    if (meta != null) {
      root.findFileByRelativePath(LOCAL_EXCLUDE);
    }
    else {
      VirtualFile parent = root.getParent();
      if (parent != null) {
        scanParents(parent);
      }
    }
  }

  /**
   * Dispose the component removing all related listeners
   */
  public void dispose() {
    myVcsManager.removeVcsListener(myVcsListener);
    VirtualFileManager.getInstance().removeVirtualFileListener(myFileListener);
  }

  /**
   * The file listener
   */
  class MyFileListener extends VirtualFileAdapter {
    /**
     * {@inheritDoc}
     */
    @Override
    public void fileCreated(VirtualFileEvent event) {
      checkIgnoreConfigChange(event.getFile());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeFileDeletion(VirtualFileEvent event) {
      checkIgnoreConfigChange(event.getFile());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeFileMovement(VirtualFileMoveEvent event) {
      if (".gitignore".equals(event.getFileName())) {
        myDirtyScopeManager.dirDirtyRecursively(event.getNewParent());
        myDirtyScopeManager.dirDirtyRecursively(event.getOldParent());
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fileCopied(VirtualFileCopyEvent event) {
      checkIgnoreConfigChange(event.getFile());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contentsChanged(VirtualFileEvent event) {
      checkIgnoreConfigChange(event.getFile());
    }

    /**
     * Check if the event affects dirty scope configuration, and if this the case, notify dirty scope manager.
     *
     * @param file the file to check
     */
    private void checkIgnoreConfigChange(VirtualFile file) {
      if (".gitignore".equals(file.getName())) {
        VirtualFile parent = file.getParent();
        if (parent != null) {
          myDirtyScopeManager.dirDirtyRecursively(parent);
        }
        return;
      }
      VirtualFile base = getBase(file, LOCAL_EXCLUDE_ARRAY);
      if (base != null) {
        myDirtyScopeManager.dirDirtyRecursively(base);
      }
    }

    /**
     * The get the possible base for the path. It tries to find the parent for the provided path, if it fails, it looks for the path without last member.
     *
     * @param file the file to get base for
     * @param path the path to to check
     * @return the file base
     */
    @Nullable
    private VirtualFile getBase(VirtualFile file, String... path) {
      return getBase(file, path.length, path);
    }

    /**
     * The get the possible base for the path. It tries to find the parent for the provided path, if it fails, it looks for the path without last member.
     *
     * @param file the file to get base for
     * @param n    the length of the path to check
     * @param path the path to to check
     * @return the file base
     */
    @Nullable
    private VirtualFile getBase(VirtualFile file, int n, String... path) {
      if (file == null || n <= 0 || n > path.length) {
        return null;
      }
      int i = 1;
      VirtualFile c = file;
      for (; c != null && i < n; i++, c = c.getParent()) {
        if (!path[n - i].equals(c.getName())) {
          break;
        }
      }
      if (i == n && c != null) {
        // all components matched
        return c.getParent();
      }
      // try shorter paths paths
      return getBase(file, n - 1, path);
    }
  }
}
