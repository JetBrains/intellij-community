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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsRootError;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Detects actual Git roots and compares them to the ones registered in Settings | Version Control.
 *
 * @author Kirill Likhodedov
 */
public class GitRootErrorsFinder {

  private final @NotNull Project myProject;
  private final @NotNull GitPlatformFacade myPlatformFacade;
  private final @NotNull ProjectLevelVcsManager myVcsManager;
  private final AbstractVcs myVcs;

  public GitRootErrorsFinder(@NotNull Project project, @NotNull GitPlatformFacade platformFacade) {
    myProject = project;
    myPlatformFacade = platformFacade;
    myVcsManager = myPlatformFacade.getVcsManager(myProject);
    myVcs = myPlatformFacade.getVcs(myProject);
  }

  @NotNull
  public Collection<VcsRootError> find() {
    List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings(myVcs);
    Collection<VirtualFile> gitRoots = new GitRootDetector(myProject, myPlatformFacade).detect().getRoots();

    Collection<VcsRootError> errors = new ArrayList<VcsRootError>();
    Collection<String> gitPaths = rootsToPaths(gitRoots);
    errors.addAll(findExtraMappings(mappings, gitPaths));
    errors.addAll(findUnregisteredRoots(mappings, gitPaths));
    return errors;
  }

  private Collection<VcsRootError> findUnregisteredRoots(List<VcsDirectoryMapping> mappings, Collection<String> gitPaths) {
    Collection<VcsRootError> errors = new ArrayList<VcsRootError>();
    List<String> mappedPaths = mappingsToPaths(mappings);
    for (String gitPath : gitPaths) {
      if (!mappedPaths.contains(gitPath)) {
        errors.add(new VcsRootError(VcsRootError.Type.UNREGISTERED_ROOT, gitPath));
      }
    }
    return errors;
  }

  private static Collection<VcsRootError> findExtraMappings(List<VcsDirectoryMapping> mappings, Collection<String> gitPaths) {
    Collection<VcsRootError> errors = new ArrayList<VcsRootError>();
    for (VcsDirectoryMapping mapping : mappings) {
      if (mapping.isDefaultMapping()) {
        if (gitPaths.isEmpty()) {
          errors.add(new VcsRootError(VcsRootError.Type.EXTRA_MAPPING, VcsDirectoryMapping.PROJECT_CONSTANT));
        }
      }
      else {
        String mappedPath = mapping.systemIndependentPath();
        if (!gitPaths.contains(mappedPath) && !hasGitDir(mappedPath)) {
          errors.add(new VcsRootError(VcsRootError.Type.EXTRA_MAPPING, mappedPath));
        }
      }
    }
    return errors;
  }

  private static boolean hasGitDir(String path) {
    File file = new File(path, GitUtil.DOT_GIT);
    return file.exists();
  }

  @NotNull
  private static Collection<String> rootsToPaths(@NotNull Collection<VirtualFile> gitRoots) {
    Collection<String> gitPaths = new ArrayList<String>(gitRoots.size());
    for (VirtualFile root : gitRoots) {
      gitPaths.add(root.getPath());
    }
    return gitPaths;
  }

  private List<String> mappingsToPaths(List<VcsDirectoryMapping> mappings) {
    List<String> paths = new ArrayList<String>();
    for (VcsDirectoryMapping mapping : mappings) {
      if (!mapping.isDefaultMapping()) {
        paths.add(mapping.systemIndependentPath());
      }
      else {
        String basePath = myProject.getBasePath();
        if (basePath != null) {
          paths.add(FileUtil.toSystemIndependentName(basePath));
        }
      }
    }
    return paths;
  }

}
