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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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

  public GitRootDetector(@NotNull Project project) {
    myProject = project;
  }

  public GitRootDetectInfo detect() {
    VirtualFile projectDir = myProject.getBaseDir();

    if (projectDir == null) {
      return new GitRootDetectInfo(Collections.<VirtualFile>emptyList(), false);
    }

    final Collection<VirtualFile> roots = scanForRootsInsideProject(projectDir);

    if (roots.contains(projectDir)) {
      return new GitRootDetectInfo(roots, true);
    }

    VirtualFile rootAbove = scanForSingleRootAboveProject(projectDir);
    if (rootAbove != null) {
      roots.add(rootAbove);
      return new GitRootDetectInfo(roots, true);
    }
    return new GitRootDetectInfo(roots, false);
  }

  @NotNull
  private static Collection<VirtualFile> scanForRootsInsideProject(@NotNull VirtualFile projectDir) {
    final Collection<VirtualFile> roots = new ArrayList<VirtualFile>();
    VfsUtil.processFilesRecursively(projectDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (virtualFile.isDirectory() && hasGitDir(virtualFile)) {
          roots.add(virtualFile);
        }
        return true;
      }
    });
    return roots;
  }

  @Nullable
  private static VirtualFile scanForSingleRootAboveProject(@NotNull VirtualFile projectDir) {
    VirtualFile parent = projectDir.getParent();
    while (parent != null) {
      if (hasGitDir(parent)) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  private static boolean hasGitDir(@NotNull VirtualFile dir) {
    VirtualFile gitDir = dir.findChild(".git");
    return gitDir != null && gitDir.exists();
  }

}
