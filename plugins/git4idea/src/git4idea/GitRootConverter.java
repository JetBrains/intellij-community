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

package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import git4idea.repo.GitRepositoryManager;
import git4idea.roots.GitRootDetector;
import git4idea.roots.GitRootsListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Given VFS content roots, filters them and returns only those, which are actual Git roots.
 */
public class GitRootConverter implements AbstractVcs.RootsConvertor, GitRootsListener {

  @NotNull private final Project myProject;
  @NotNull private final PlatformFacade myPlatformFacade;

  @Nullable private Collection<VirtualFile> myDetectedRoots;

  public GitRootConverter(@NotNull Project project, @NotNull PlatformFacade facade) {
    myProject = project;
    myPlatformFacade = facade;
    myProject.getMessageBus().connect().subscribe(GitRepositoryManager.GIT_ROOTS_CHANGE, this);
  }

  @NotNull
  public List<VirtualFile> convertRoots(@NotNull List<VirtualFile> result) {
    if (myDetectedRoots == null) {
      myDetectedRoots = new GitRootDetector(myProject, myPlatformFacade).detect().getRoots();
    }

    ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>();
    if (hasProjectMapping()) {
      roots.addAll(myDetectedRoots);
    }

    HashSet<VirtualFile> listed = new HashSet<VirtualFile>();
    for (VirtualFile f : result) {
      VirtualFile r = GitUtil.gitRootOrNull(f);
      if (r != null && listed.add(r)) {
        if (!roots.contains(r)) {
          roots.add(r);
        }
      }
    }
    return roots;
  }

  private boolean hasProjectMapping() {
    for (VcsDirectoryMapping mapping : myPlatformFacade.getVcsManager(myProject).getDirectoryMappings()) {
      if (mapping.isDefaultMapping()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void gitRootsChanged(Collection<VirtualFile> roots) {
    myDetectedRoots = roots;
  }

}
