/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.PlatformFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>
 *   Scans the file system looking for Git roots, which contain the Project or its parts,
 *   and returns the information enclosed to the {@link GitRootDetectInfo}.
 *   The main part of the information are Git roots which will be proposed to the user to be added as VCS roots.
 * </p>
 * <p>
 *   Linked sources are not scanned. User should add Git roots for them explicitly.
 * </p>
 *
 * @author Kirill Likhodedov
 */
public class GitRootDetector {

  @NotNull private final Project myProject;
  @NotNull private final PlatformFacade myPlatformFacade;

  public GitRootDetector(@NotNull Project project, @NotNull PlatformFacade platformFacade) {
    myProject = project;
    myPlatformFacade = platformFacade;
  }

  public GitRootDetectInfo detect() {
    VirtualFile projectDir = myProject.getBaseDir();

    if (projectDir == null) {
      return new GitRootDetectInfo(Collections.<VirtualFile>emptyList(), false, false);
    }

    final Set<VirtualFile> roots = scanForRootsInsideDir(projectDir);
    roots.addAll(scanForRootsInContentRoots());

    if (roots.contains(projectDir)) {
      return new GitRootDetectInfo(roots, true, false);
    }

    VirtualFile rootAbove = scanForSingleRootAboveDir(projectDir);
    if (rootAbove != null) {
      roots.add(rootAbove);
      return new GitRootDetectInfo(roots, true, true);
    }
    return new GitRootDetectInfo(roots, false, false);
  }

  private Set<VirtualFile> scanForRootsInContentRoots() {
    Set<VirtualFile> gitRoots = new HashSet<VirtualFile>();
    VirtualFile[] roots = myPlatformFacade.getProjectRootManager(myProject).getContentRoots();
    for (VirtualFile contentRoot : roots) {
      Set<VirtualFile> rootsInsideRoot = scanForRootsInsideDir(contentRoot);
      if (!rootsInsideRoot.contains(contentRoot)) {
        VirtualFile rootAbove = scanForSingleRootAboveDir(contentRoot);
        if (rootAbove != null) {
          rootsInsideRoot.add(rootAbove);
        }
      }
      gitRoots.addAll(rootsInsideRoot);
    }
    return gitRoots;
  }

  @NotNull
  private Set<VirtualFile> scanForRootsInsideDir(@NotNull final VirtualFile dir, final int depth) {
    final Set<VirtualFile> roots = new HashSet<VirtualFile>();
    if (depth > 2) {
      // performance optimization via limitation: don't scan deep though the whole VFS, 2 levels under a content root is enough
      return roots;
    }

    myPlatformFacade.runReadActionAndWaitIfNeeded(new Runnable() {
      @Override public void run() {
        if (myProject.isDisposed() || !dir.isDirectory()) {
          return;
        }
        if (hasGitDir(dir)) {
          roots.add(dir);
        }
        for (VirtualFile child : dir.getChildren()) {
          roots.addAll(scanForRootsInsideDir(child, depth + 1));
        }
      }
    });

    return roots;
  }

  @NotNull
  private Set<VirtualFile> scanForRootsInsideDir(@NotNull VirtualFile projectDir) {
    return scanForRootsInsideDir(projectDir, 0);
  }

  @Nullable
  private VirtualFile scanForSingleRootAboveDir(@NotNull final VirtualFile projectDir) {
    final AtomicReference<VirtualFile> parent = new AtomicReference<VirtualFile>();
    myPlatformFacade.runReadActionAndWaitIfNeeded(new Runnable() {
      @Override public void run() {
        if (myProject.isDisposed()) {
          return;
        }

        VirtualFile par = projectDir.getParent();
        while (par != null) {
          if (hasGitDir(par)) {
            parent.set(par);
            return;
          }
          par = par.getParent();
        }
      }
    });
    return parent.get();
  }

  private static boolean hasGitDir(@NotNull VirtualFile dir) {
    VirtualFile gitDir = dir.findChild(".git");
    return gitDir != null && gitDir.exists();
  }

}
