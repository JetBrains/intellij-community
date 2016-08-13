/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.Pair.pair;

class CanonicalPathMap {
  private static final Logger LOG = Logger.getInstance(FileWatcher.class);

  private final List<String> myRecursiveWatchRoots;
  private final List<String> myFlatWatchRoots;
  private final List<String> myCanonicalRecursiveWatchRoots;
  private final List<String> myCanonicalFlatWatchRoots;
  private final MultiMap<String, String> myPathMapping;

  public CanonicalPathMap() {
    myRecursiveWatchRoots = myCanonicalRecursiveWatchRoots = myFlatWatchRoots = myCanonicalFlatWatchRoots = Collections.emptyList();
    myPathMapping = MultiMap.empty();
  }

  public CanonicalPathMap(@NotNull List<String> recursive, @NotNull List<String> flat) {
    myRecursiveWatchRoots = ContainerUtil.newArrayList(recursive);
    myFlatWatchRoots = ContainerUtil.newArrayList(flat);

    List<Pair<String, String>> mapping = ContainerUtil.newSmartList();
    Map<String, String> resolvedPaths = resolvePaths(recursive, flat);
    myCanonicalRecursiveWatchRoots = mapPaths(resolvedPaths, recursive, mapping);
    myCanonicalFlatWatchRoots = mapPaths(resolvedPaths, flat, mapping);

    myPathMapping = MultiMap.createConcurrentSet();
    addMapping(mapping);
  }

  private static Map<String, String> resolvePaths(Collection<String> recursiveRoots, Collection<String> flatRoots) {
    Map<String, String> result = ContainerUtil.newConcurrentMap();

    List<String> roots = ContainerUtil.concat(Arrays.asList(recursiveRoots, flatRoots));
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(roots, null, false, false, root -> {
      ContainerUtil.putIfNotNull(root, FileSystemUtil.resolveSymLink(root), result);
      return true;
    });

    return result;
  }

  private static List<String> mapPaths(Map<String, String> resolvedPaths, List<String> paths, Collection<Pair<String, String>> mapping) {
    List<String> canonicalPaths = ContainerUtil.newArrayList(paths);
    for (int i = 0; i < paths.size(); i++) {
      String path = paths.get(i);
      String canonicalPath = resolvedPaths.get(path);
      if (canonicalPath != null && !path.equals(canonicalPath)) {
        canonicalPaths.set(i, canonicalPath);
        mapping.add(pair(canonicalPath, path));
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
    for (Pair<String, String> pair : mapping) {
      // See if we are adding a mapping that itself should be mapped to a different path
      // Example: /foo/real_path -> /foo/symlink, /foo/remapped_path -> /foo/real_path
      // In this case, if the file watcher returns /foo/remapped_path/file.txt, we want to report /foo/symlink/file.txt back to IntelliJ.
      Collection<String> preRemapPathToWatchedPaths = myPathMapping.get(pair.second);
      for (String realWatchedPath : preRemapPathToWatchedPaths) {
        Collection<String> remappedPathMappings = myPathMapping.getModifiable(pair.first);
        remappedPathMappings.add(realWatchedPath);
      }

      // Since there can be more than one file watcher and REMAPPING is an implementation detail of the native file watcher,
      // add the mapping as usual even if we added data above.
      Collection<String> symLinksToCanonicalPath = myPathMapping.getModifiable(pair.first);
      symLinksToCanonicalPath.add(pair.second);
    }
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
  public Collection<String> getWatchedPaths(@NotNull String reportedPath, boolean isExact) {
    if (myFlatWatchRoots.isEmpty() && myRecursiveWatchRoots.isEmpty()) return Collections.emptyList();

    Collection<String> affectedPaths = applyMapping(reportedPath);
    Collection<String> changedPaths = ContainerUtil.newSmartList();

    ext:
    for (String path : affectedPaths) {
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
        else {
          String rootParent = new File(root).getParent();
          if (rootParent != null && FileUtil.namesEqual(path, rootParent)) {
            changedPaths.add(root);
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
          String rootParent = new File(root).getParent();
          if (rootParent != null && FileUtil.namesEqual(path, rootParent)) {
            changedPaths.add(root);
            continue ext;
          }
        }
      }
    }

    if (changedPaths.isEmpty() && LOG.isDebugEnabled()) {
      LOG.debug("Not watchable, filtered: " + reportedPath);
    }

    return changedPaths;
  }

  private Collection<String> applyMapping(String reportedPath) {
    List<String> results = ContainerUtil.newSmartList(reportedPath);
    List<String> pathComponents = FileUtil.splitPath(reportedPath);

    File runningPath = null;
    for (int i = 0; i < pathComponents.size(); ++i) {
      String currentPathComponent = pathComponents.get(i);
      if (runningPath == null) {
        runningPath = new File(currentPathComponent.isEmpty() ? "/" : currentPathComponent);
      }
      else {
        runningPath = new File(runningPath, currentPathComponent);
      }
      Collection<String> mappedPaths = myPathMapping.get(runningPath.getPath());
      for (String mappedPath : mappedPaths) {
        // Append the specific file suffix to the mapped watch root.
        String fileSuffix = StringUtil.join(pathComponents.subList(i + 1, pathComponents.size()), File.separator);
        results.add(new File(mappedPath, fileSuffix).getPath());
      }
    }

    return results;
  }
}