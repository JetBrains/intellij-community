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
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsRootError;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.PlatformFacade;
import org.jetbrains.annotations.NotNull;

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
  private final @NotNull PlatformFacade myPlatformFacade;
  private final @NotNull ProjectLevelVcsManager myVcsManager;
  private final AbstractVcs myVcs;

  public GitRootErrorsFinder(@NotNull Project project, @NotNull PlatformFacade platformFacade) {
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
    if (hasProjectMapping(myPlatformFacade.getVcsManager(myProject).getDirectoryMappings())) {
      return errors;
    }
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
        if (!gitPaths.contains(mappedPath)) {
          errors.add(new VcsRootError(VcsRootError.Type.EXTRA_MAPPING, mappedPath));
        }
      }
    }
    return errors;
  }

  @NotNull
  private static Collection<String> rootsToPaths(@NotNull Collection<VirtualFile> gitRoots) {
    Collection<String> gitPaths = new ArrayList<String>(gitRoots.size());
    for (VirtualFile root : gitRoots) {
      gitPaths.add(root.getPath());
    }
    return gitPaths;
  }

  private static List<String> mappingsToPaths(List<VcsDirectoryMapping> mappings) {
    List<String> paths = new ArrayList<String>();
    for (VcsDirectoryMapping mapping : mappings) {
      if (!mapping.isDefaultMapping()) {
        paths.add(mapping.systemIndependentPath());
      }
    }
    return paths;
  }

  private static boolean hasProjectMapping(List<VcsDirectoryMapping> mappings) {
    for (VcsDirectoryMapping mapping : mappings) {
      if (mapping.isDefaultMapping()) {
        return true;
      }
    }
    return false;
  }

}
