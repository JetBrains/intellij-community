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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors.CollectProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.find;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

public class CachesHolder {
  @NonNls private static final String VCS_CACHE_PATH = "vcsCache";

  @NotNull private final Project myProject;
  @NotNull private final Map<String, ChangesCacheFile> myCacheFiles = ContainerUtil.newConcurrentMap();
  @NotNull private final RepositoryLocationCache myLocationCache;
  @NotNull private final ProjectLevelVcsManager myPlManager;

  public CachesHolder(@NotNull Project project, @NotNull RepositoryLocationCache locationCache) {
    myProject = project;
    myLocationCache = locationCache;
    myPlManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  /**
   * Returns all paths that will be used to collect committed changes about. ideally, for one checkout there should be one file
   */
  @NotNull
  public Map<VirtualFile, RepositoryLocation> getAllRootsUnderVcs(@NotNull AbstractVcs vcs) {
    return new RootsCalculator(myProject, vcs, myLocationCache).getRoots();
  }

  public void iterateAllCaches(@NotNull Processor<ChangesCacheFile> processor) {
    for (AbstractVcs vcs : myPlManager.getAllActiveVcss()) {
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

  public void clearAllCaches() {
    myCacheFiles.values().forEach(ChangesCacheFile::delete);
    myCacheFiles.clear();
  }

  @NotNull
  public Collection<ChangesCacheFile> getAllCaches() {
    CollectProcessor<ChangesCacheFile> processor = new CollectProcessor<>();
    iterateAllCaches(processor);
    return processor.getResults();
  }

  @NotNull
  public ChangesCacheFile getCacheFile(@NotNull AbstractVcs vcs, @NotNull VirtualFile root, @NotNull RepositoryLocation location) {
    return myCacheFiles
      .computeIfAbsent(location.getKey(), key -> new ChangesCacheFile(myProject, getCachePath(location), vcs, root, location));
  }

  @NotNull
  public File getCacheBasePath() {
    File file = new File(PathManager.getSystemPath(), VCS_CACHE_PATH);
    file = new File(file, myProject.getLocationHash());
    return file;
  }

  @NotNull
  private File getCachePath(@NotNull RepositoryLocation location) {
    File file = getCacheBasePath();
    file.mkdirs();
    return new File(file, md5Hex(location.getKey()));
  }

  @Nullable
  public ChangesCacheFile haveCache(@NotNull RepositoryLocation location) {
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
