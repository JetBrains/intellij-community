// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors.CollectProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.util.containers.ContainerUtil.find;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

public class CachesHolder {
  @NonNls private static final String VCS_CACHE_PATH = "vcsCache";

  private final @NotNull Project myProject;
  private final @NotNull Map<String, ChangesCacheFile> myCacheFiles = new ConcurrentHashMap<>();
  private final @NotNull RepositoryLocationCache myLocationCache;

  public CachesHolder(@NotNull Project project, @NotNull RepositoryLocationCache locationCache) {
    myProject = project;
    myLocationCache = locationCache;
  }

  /**
   * Returns all paths that will be used to collect committed changes about. ideally, for one checkout there should be one file
   */
  public @NotNull Map<VirtualFile, RepositoryLocation> getAllRootsUnderVcs(@NotNull AbstractVcs vcs) {
    return new RootsCalculator(myProject, vcs, myLocationCache).getRoots();
  }

  public void iterateAllCaches(@NotNull Processor<? super ChangesCacheFile> processor) {
    for (AbstractVcs vcs : ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss()) {
      if (vcs.getCommittedChangesProvider() instanceof CachingCommittedChangesProvider) {
        for (Map.Entry<VirtualFile, RepositoryLocation> entry : getAllRootsUnderVcs(vcs).entrySet()) {
          ChangesCacheFile cacheFile = getCacheFile(vcs, entry.getKey(), entry.getValue());
          if (!processor.process(cacheFile)) {
            return;
          }
        }
      }
    }
  }

  public void reset() {
    myCacheFiles.clear();
  }

  public void clearAllCaches() {
    myCacheFiles.values().forEach(ChangesCacheFile::delete);
    reset();
  }

  public @NotNull Collection<ChangesCacheFile> getAllCaches() {
    CollectProcessor<ChangesCacheFile> processor = new CollectProcessor<>();
    iterateAllCaches(processor);
    return processor.getResults();
  }

  public @NotNull ChangesCacheFile getCacheFile(@NotNull AbstractVcs vcs, @NotNull VirtualFile root, @NotNull RepositoryLocation location) {
    return myCacheFiles
      .computeIfAbsent(location.getKey(), key -> new ChangesCacheFile(myProject, getCachePath(location), vcs, root, location));
  }

  public @NotNull Path getCacheBasePath() {
    return Paths.get(PathManager.getSystemPath(), VCS_CACHE_PATH, myProject.getLocationHash());
  }

  private @NotNull File getCachePath(@NotNull RepositoryLocation location) {
    File file = getCacheBasePath().toFile();
    file.mkdirs();
    return new File(file, md5Hex(location.getKey()));
  }

  public @Nullable ChangesCacheFile haveCache(@NotNull RepositoryLocation location) {
    String key = location.getKey();
    ChangesCacheFile result = myCacheFiles.get(key);

    if (result == null) {
      String keyWithSlash = key.endsWith("/") ? key : key + "/";
      String cachedSimilarKey = find(myCacheFiles.keySet(), s -> keyWithSlash.startsWith(s) || s.startsWith(keyWithSlash));
      result = cachedSimilarKey != null ? myCacheFiles.get(cachedSimilarKey) : null;
    }

    return result;
  }
}
