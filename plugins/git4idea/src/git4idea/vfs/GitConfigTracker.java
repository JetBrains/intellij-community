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

package git4idea.vfs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.HashSet;
import git4idea.GitUtil;
import git4idea.GitVcs;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * The tracker for configuration files, it tracks the following events:
 * <ul>
 * <li>Changes in configuration files: .git/config and ~/.gitconfig</li>
 * </ul>
 * The tracker assumes that git roots are configured correctly.
 */
public class GitConfigTracker implements GitRootsListener {
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The vcs object
   */
  private final GitVcs myVcs;
  /**
   * The vcs manager that tracks content roots
   */
  private final ProjectLevelVcsManager myVcsManager;
  /**
   * The listener collection (managed by GitVcs object since lifetime of this object is less than lifetime of GitVcs)
   */
  private final GitConfigListener myMulticaster;
  /**
   * The appeared roots that has been already reported as changed
   */
  private final HashSet<VirtualFile> myReportedRoots = new HashSet<VirtualFile>();
  /**
   * Local file system service
   */
  private final LocalFileSystem myLocalFileSystem;
  /**
   * The file listener
   */
  private final MyFileListener myFileListener;

  /**
   * The constructor
   *
   * @param project     the context project
   * @param vcs         the vcs object
   * @param multicaster the listener collection to use
   */
  public GitConfigTracker(Project project, GitVcs vcs, GitConfigListener multicaster) {
    myProject = project;
    myVcs = vcs;
    myMulticaster = multicaster;
    myLocalFileSystem = LocalFileSystem.getInstance();
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myVcs.addGitRootsListener(this);
    myFileListener = new MyFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myFileListener);
    gitRootsChanged();
  }

  /**
   * This method is invoked when set of configured roots changed.
   */
  public void gitRootsChanged() {
    VirtualFile[] contentRoots = myVcsManager.getRootsUnderVcs(myVcs);
    if (contentRoots == null || contentRoots.length == 0) {
      return;
    }
    Set<VirtualFile> currentRootSet = GitUtil.gitRootsForPaths(Arrays.asList(contentRoots));
    HashSet<VirtualFile> newRoots = new HashSet<VirtualFile>(currentRootSet);
    synchronized (myReportedRoots) {
      for (Iterator<VirtualFile> i = myReportedRoots.iterator(); i.hasNext();) {
        VirtualFile root = i.next();
        if (!root.isValid()) {
          i.remove();
        }
      }
      newRoots.removeAll(myReportedRoots);
      myReportedRoots.clear();
      myReportedRoots.addAll(currentRootSet);
    }
    for (VirtualFile root : newRoots) {
      VirtualFile config = root.findFileByRelativePath(".git/config");
      myMulticaster.configChanged(root, config);
    }
    // visit user home directory in order to notice .gitconfig changes later
    VirtualFile userHome = getUserHome();
    if (userHome != null) {
      userHome.getChildren();
    }
  }

  /**
   * @return user home directory
   */
  @Nullable
  private VirtualFile getUserHome() {
    return myLocalFileSystem.findFileByPath(System.getProperty("user.home"));
  }


  /**
   * Dispose the tracker removing all registered listeners
   */
  public void dispose() {
    myVcs.removeGitRootsListener(this);
    VirtualFileManager.getInstance().removeVirtualFileListener(myFileListener);
  }


  /**
   * The listener for the file system that checks if the configuration files are changed.
   * Note that events are checked in quite a shallow form. More radical events will cause
   * remapping of git roots and gitRootsChanged() event will be delivered.
   */
  private class MyFileListener extends VirtualFileAdapter {

    /**
     * Check if the event affects configuration files in registered roots
     *
     * @param file the file to check
     */
    private void checkConfigAffected(VirtualFile file) {
      if (file.getName().equals(".gitconfig")) {
        VirtualFile userHome = getUserHome();
        VirtualFile parent = file.getParent();
        if (userHome != null && parent != null && parent.equals(userHome)) {
          HashSet<VirtualFile> allRoots;
          synchronized (myReportedRoots) {
            allRoots = new HashSet<VirtualFile>(myReportedRoots);
          }
          for (VirtualFile root : allRoots) {
            myMulticaster.configChanged(root, file);
          }
        }
        return;
      }
      VirtualFile base = GitUtil.getPossibleBase(file, ".git", "config");
      if (base != null) {
        boolean reported;
        synchronized (myReportedRoots) {
          reported = myReportedRoots.contains(base);
        }
        if (reported) {
          myMulticaster.configChanged(base, file);
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fileCreated(VirtualFileEvent event) {
      checkConfigAffected(event.getFile());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeFileDeletion(VirtualFileEvent event) {
      checkConfigAffected(event.getFile());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contentsChanged(VirtualFileEvent event) {
      checkConfigAffected(event.getFile());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fileCopied(VirtualFileCopyEvent event) {
      super.fileCopied(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      String fileName = event.getFileName();
      VirtualFile newParent = event.getNewParent();
      VirtualFile oldParent = event.getOldParent();
      if (fileName.equals("config")) {
        checkParent(newParent);
        checkParent(oldParent);
      }
      if (fileName.equals(".gitconfig")) {
        VirtualFile userHome = getUserHome();
        if (userHome != null && (newParent.equals(userHome) || oldParent.equals(userHome))) {
          HashSet<VirtualFile> allRoots;
          synchronized (myReportedRoots) {
            allRoots = new HashSet<VirtualFile>(myReportedRoots);
          }
          VirtualFile config = userHome.findChild(".gitconfig");
          for (VirtualFile root : allRoots) {
            myMulticaster.configChanged(root, config);
          }
        }
      }
    }

    /**
     * Check parent and report event if it is one of reported roots
     *
     * @param parent the parent to check
     */
    private void checkParent(VirtualFile parent) {
      if (parent.getName().equals(".git")) {
        VirtualFile base = parent.getParent();
        if (base != null) {
          boolean reported;
          synchronized (myReportedRoots) {
            reported = myReportedRoots.contains(base);
          }
          if (reported) {
            myMulticaster.configChanged(base, parent.findChild("config"));
          }
        }
      }
    }
  }
}
