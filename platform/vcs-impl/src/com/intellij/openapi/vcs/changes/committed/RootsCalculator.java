// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static java.util.function.Function.identity;

@ApiStatus.Internal
public final class RootsCalculator {
  private final static Logger LOG = Logger.getInstance(RootsCalculator.class);

  @NotNull private final Project myProject;
  @NotNull private final AbstractVcs myVcs;
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
    LOG.debug("Collecting roots for " + myVcs);
    // TODO: It is not quite clear why using just ProjectLevelVcsManager.getRootsUnderVcs() is not sufficient
    List<VirtualFile> roots = getRootsFromMappings();
    ContainerUtil.addAll(roots, myPlManager.getRootsUnderVcs(myVcs));

    logRoots("Candidates", roots);

    roots.removeIf(file -> getLocation(file) == null);

    logRoots("Candidates with repository location", roots);

    Map<VirtualFile, RepositoryLocation> result = StreamEx.of(myVcs.filterUniqueRoots(roots, identity()))
      .distinct()
      .mapToEntry(this::getLocation)
      .nonNullValues()
      .toMap();
    logRoots("Unique roots", result.keySet());
    return result;
  }

  @NotNull
  private List<VirtualFile> getRootsFromMappings() {
    List<VirtualFile> result = new ArrayList<>();

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

  private static void logRoots(@NonNls @NotNull String prefix, @NotNull Collection<? extends VirtualFile> roots) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(prefix + ": " + join(roots, VirtualFile::getPath, ", "));
    }
  }
}
