/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class CanonicalPathMap {
  private static final Logger LOG = Logger.getInstance(FileWatcher.class);

  private final List<String> myRecursiveWatchRoots;
  private final List<String> myFlatWatchRoots;
  private final List<String> myCanonicalRecursiveWatchRoots;
  private final List<String> myCanonicalFlatWatchRoots;

  /**
   * Translate from the path reported from the file watcher to the path requested by IntelliJ.
   *
   * <p> It is possible to add a mapping that requires a single level of transitive closure. Implementations must ensure that this single
   * level of transitive closure is performed so all paths returned by {@link #applyMapping(String)} are in the form initially requested
   * by the caller of {@link #addMapping(Collection)} or {@link #addMappings(Collection)}.</p>
   */
  interface PathMapper {
    /**
     * Translate from path reported by the file watcher to the path that was requested to be watched.
     *
     * If /root/link/bar is being watched and is a symlink to /root/impl/bar and /root/impl/bar/foo.txt is reported from the file watcher,
     * this method should return /root/link/bar/foo.txt
     */
    @NotNull Collection<String> applyMapping(@NotNull String reportedPath);

    void addMappings(@NotNull Collection<Pair<String, String>> mappings);
    void addMapping(@NotNull String reportedPath, @NotNull String watchedPath);
  }

  @NotNull private final PathMapper myMapping;

  @NotNull
  private static PathMapper createMapper() {
    return new PathMapperMapImpl();
    //return new PathMapperListImpl();
  }

  public CanonicalPathMap() {
    myRecursiveWatchRoots = myCanonicalRecursiveWatchRoots = myFlatWatchRoots = myCanonicalFlatWatchRoots = Collections.emptyList();
    myMapping = createMapper();
  }

  public CanonicalPathMap(@NotNull List<String> recursive, @NotNull List<String> flat) {
    myRecursiveWatchRoots = ContainerUtil.newArrayList(recursive);
    myFlatWatchRoots = ContainerUtil.newArrayList(flat);

    PathMapper mapping = createMapper();
    myCanonicalRecursiveWatchRoots = mapPaths(recursive, mapping);
    myCanonicalFlatWatchRoots = mapPaths(flat, mapping);
    myMapping = mapping;
  }

  private static List<String> mapPaths(List<String> paths, @NotNull PathMapper mapping) {
    List<String> canonicalPaths = ContainerUtil.newArrayList(paths);
    for (int i = 0; i < paths.size(); i++) {
      String path = paths.get(i);
      String canonicalPath = FileSystemUtil.resolveSymLink(path);
      if (canonicalPath != null && !path.equals(canonicalPath)) {
        canonicalPaths.set(i, canonicalPath);
        mapping.addMapping(canonicalPath, path);
      }
    }
    return canonicalPaths;
  }

  public List<String> getCanonicalRecursiveWatchRoots() {
    return myCanonicalRecursiveWatchRoots;
  }

  public List<String> getCanonicalFlatWatchRoots() {
    return myCanonicalFlatWatchRoots;
  }

  public void addMapping(@NotNull Collection<Pair<String, String>> mapping) {
    myMapping.addMappings(mapping);
  }

  /**
   * Maps reported paths from canonical representation to requested paths, then filters out those which do not fall under watched roots.
   *
   * <h3>Exactness</h3>
   * Some watchers (notable the native one on OS X) report a parent directory as dirty instead of the "exact" file path.
   * <p>
   * For flat roots, it means that if and only if the exact dirty file path is returned, we should compare the parent to the flat roots,
   * otherwise we should compare to path given to us because it is already the parent of the actual dirty path.
   * <p>
   * For recursive roots, if the path given to us is already the parent of the actual dirty path, we need to compare the path to the parent
   * of the recursive root because if the root itself was changed, we need to know about it.
   */
  @NotNull
  public Collection<String> getWatchedPaths(@NotNull String reportedPath, boolean isExact, boolean fastPath) {
    if (myFlatWatchRoots.isEmpty() && myRecursiveWatchRoots.isEmpty()) return Collections.emptyList();

    Collection<String> affectedPaths = myMapping.applyMapping(reportedPath);
    Collection<String> changedPaths = ContainerUtil.newSmartList();

    ext:
    for (String path : affectedPaths) {
      if (fastPath && !changedPaths.isEmpty()) break;

      for (String root : myFlatWatchRoots) {
        if (FileUtil.namesEqual(path, root)) {
          changedPaths.add(path);
          continue ext;
        }
        if (isExact) {
          String parentPath = new File(path).getParent();
          if (parentPath != null && FileUtil.namesEqual(parentPath, root)) {
            changedPaths.add(path);
            continue ext;
          }
        }
      }

      for (String root : myRecursiveWatchRoots) {
        if (FileUtil.startsWith(path, root)) {
          changedPaths.add(path);
          continue ext;
        }
        if (!isExact) {
          String parentPath = new File(root).getParent();
          if (parentPath != null && FileUtil.namesEqual(path, parentPath)) {
            changedPaths.add(root);
            continue ext;
          }
        }
      }
    }

    if (!fastPath && changedPaths.isEmpty() && LOG.isDebugEnabled()) {
      LOG.debug("Not watchable, filtered: " + reportedPath);
    }

    return changedPaths;
  }
}