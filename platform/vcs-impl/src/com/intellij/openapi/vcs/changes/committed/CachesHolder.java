// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.io.DigestUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.util.containers.ContainerUtil.find;
import static com.intellij.util.io.DigestUtilKt.hashToHexString;

@ApiStatus.Internal
public final class CachesHolder {
  @NonNls private static final String VCS_CACHE_PATH = "vcsCache";

  private final @NotNull Project myProject;
  private final @NotNull Map<String, ChangesCacheFile> cacheFiles = new ConcurrentHashMap<>();
  private final @NotNull RepositoryLocationCache myLocationCache;

  public CachesHolder(@NotNull Project project, @NotNull RepositoryLocationCache locationCache) {
    myProject = project;
    myLocationCache = locationCache;
  }

  /**
   * Returns all paths that will be used to collect committed changes about. Ideally, for one checkout, there should be one file
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
    cacheFiles.clear();
  }

  public void clearAllCaches() {
    cacheFiles.values().forEach(ChangesCacheFile::delete);
    reset();
  }

  public @NotNull Collection<ChangesCacheFile> getAllCaches() {
    CollectProcessor<ChangesCacheFile> processor = new CollectProcessor<>();
    iterateAllCaches(processor);
    return processor.getResults();
  }

  public @NotNull ChangesCacheFile getCacheFile(@NotNull AbstractVcs vcs, @NotNull VirtualFile root, @NotNull RepositoryLocation location) {
    return cacheFiles
      .computeIfAbsent(location.getKey(), key -> new ChangesCacheFile(myProject, getCachePath(location), vcs, root, location));
  }

  public @NotNull Path getCacheBasePath() {
    return Path.of(PathManager.getSystemPath(), VCS_CACHE_PATH, myProject.getLocationHash());
  }

  private @NotNull Path getCachePath(@NotNull RepositoryLocation location) {
    Path file = getCacheBasePath();
    try {
      Files.createDirectories(file);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return file.resolve(hashToHexString(location.getKey(), DigestUtil.md5()));
  }

  public @Nullable ChangesCacheFile haveCache(@NotNull RepositoryLocation location) {
    String key = location.getKey();
    ChangesCacheFile result = cacheFiles.get(key);
    if (result == null) {
      String keyWithSlash = key.endsWith("/") ? key : key + "/";
      String cachedSimilarKey = find(cacheFiles.keySet(), s -> keyWithSlash.startsWith(s) || s.startsWith(keyWithSlash));
      result = cachedSimilarKey != null ? cacheFiles.get(cachedSimilarKey) : null;
    }

    return result;
  }
}
