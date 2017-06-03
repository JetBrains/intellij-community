/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.addAll;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static java.util.function.Function.identity;

public class RootsCalculator {
  private final static Logger LOG = Logger.getInstance(RootsCalculator.class);

  @NotNull private final Project myProject;
  @NotNull private final AbstractVcs<?> myVcs;
  @NotNull private final ProjectLevelVcsManager myPlManager;
  @NotNull private final RepositoryLocationCache myLocationCache;

  public RootsCalculator(@NotNull Project project, @NotNull AbstractVcs vcs, @NotNull RepositoryLocationCache locationCache) {
    myProject = project;
    myLocationCache = locationCache;
    myPlManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcs = vcs;
  }

  @NotNull
  public Map<VirtualFile, RepositoryLocation> getRoots() {
    // TODO: It is not quite clear why using just ProjectLevelVcsManager.getRootsUnderVcs() is not sufficient
    List<VirtualFile> roots = getRootsFromMappings();
    addAll(roots, myPlManager.getRootsUnderVcs(myVcs));

    roots.removeIf(file -> getLocation(file) == null);

    Map<VirtualFile, RepositoryLocation> result = StreamEx.of(myVcs.filterUniqueRoots(roots, identity()))
      .distinct()
      .mapToEntry(this::getLocation)
      .nonNullValues()
      .toMap();
    logRoots(result.keySet());
    return result;
  }

  @NotNull
  private List<VirtualFile> getRootsFromMappings() {
    List<VirtualFile> result = newArrayList();

    for (VcsDirectoryMapping mapping : myPlManager.getDirectoryMappings(myVcs)) {
      if (mapping.isDefaultMapping()) {
        if (myVcs.equals(myPlManager.getVcsFor(myProject.getBaseDir()))) {
          result.add(myProject.getBaseDir());
        }
      }
      else {
        VirtualFile newFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(mapping.getDirectory());
        if (newFile != null) {
          result.add(newFile);
        }
        else {
          LOG.info("Can not find virtual file for root: " + mapping.getDirectory());
        }
      }
    }

    return result;
  }

  @Nullable
  private RepositoryLocation getLocation(@NotNull VirtualFile file) {
    return myLocationCache.getLocation(myVcs, getFilePath(file), false);
  }

  private static void logRoots(@NotNull Collection<VirtualFile> roots) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Roots for committed changes load: " + join(roots, VirtualFile::getPath, ", "));
    }
  }
}
