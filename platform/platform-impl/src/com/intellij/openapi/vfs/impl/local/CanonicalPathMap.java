// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.PathUtil.getParentPath;

/**
 * Unless stated otherwise, all paths are {@link org.jetbrains.annotations.SystemDependent @SystemDependent}.
 */
final class CanonicalPathMap {
  static CanonicalPathMap empty() {
    return new CanonicalPathMap(Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), MultiMap.createConcurrentSet());
  }

  private final NavigableSet<String> myOptimizedRecursiveWatchRoots;
  private final NavigableSet<String> myOptimizedFlatWatchRoots;
  private final MultiMap<String, String> myPathMappings;

  CanonicalPathMap(@NotNull NavigableSet<String> optimizedRecursiveWatchRoots,
                   @NotNull NavigableSet<String> optimizedFlatWatchRoots,
                   @NotNull MultiMap<String, String> initialPathMappings) {
    myOptimizedRecursiveWatchRoots = optimizedRecursiveWatchRoots;
    myOptimizedFlatWatchRoots = optimizedFlatWatchRoots;
    myPathMappings = initialPathMappings;
    assert myPathMappings.getClass() == MultiMap.createConcurrentSet().getClass()
      : "initialPathMappings must be created with MultiMap.createConcurrentSet()";
  }

  @NotNull Pair<List<String>, List<String>> getCanonicalWatchRoots() {
    Map<String, String> canonicalPathMappings = new ConcurrentHashMap<>();
    Stream.concat(myOptimizedRecursiveWatchRoots.stream(), myOptimizedFlatWatchRoots.stream())
      .parallel()
      .forEach(root -> {
        String canonicalRoot = FileSystemUtil.resolveSymLink(root);
        if (canonicalRoot != null && OSAgnosticPathUtil.COMPARATOR.compare(canonicalRoot, root) != 0) {
          canonicalPathMappings.put(root, canonicalRoot);
        }
      });

    NavigableSet<String> canonicalRecursiveRoots = WatchRootsUtil.createFileNavigableSet();
    for (String root : myOptimizedRecursiveWatchRoots) {
      String canonical = canonicalPathMappings.get(root);
      if (canonical != null) {
        myPathMappings.putValue(canonical, root);
      }
      else {
        canonical = root;
      }
      WatchRootsUtil.insertRecursivePath(canonicalRecursiveRoots, canonical);
    }

    Set<String> canonicalFlatRoots = new HashSet<>();
    for (String root : myOptimizedFlatWatchRoots) {
      String canonical = canonicalPathMappings.get(root);
      if (canonical != null) {
        myPathMappings.putValue(canonical, root);
      }
      else {
        canonical = root;
      }
      if (!WatchRootsUtil.isCoveredRecursively(canonicalRecursiveRoots, canonical)) {
        canonicalFlatRoots.add(canonical);
      }
    }

    return pair(new ArrayList<>(canonicalRecursiveRoots), new ArrayList<>(canonicalFlatRoots));
  }

  void addMapping(@NotNull Collection<? extends Pair<String, String>> mapping) {
    for (Pair<String, String> pair : mapping) {
      String from = pair.first, to = pair.second;

      // See if we are adding a mapping that itself should be mapped to a different path
      // Example: /foo/real_path -> /foo/symlink, /foo/remapped_path -> /foo/real_path
      // In this case, if the file watcher returns /foo/remapped_path/file.txt, we want to report /foo/symlink/file.txt back to IntelliJ.
      Collection<String> preRemapPathToWatchedPaths = applyMapping(to);
      for (String realWatchedPath : preRemapPathToWatchedPaths) {
        myPathMappings.putValue(from, realWatchedPath);
      }

      // Since there can be more than one file watcher and REMAPPING is an implementation detail of the native file watcher,
      // add the mapping as usual even if we added data above.
      myPathMappings.putValue(from, to);
    }
  }

  boolean belongsToWatchRoots(@NotNull String reportedPath, boolean isFile) {
    return WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, reportedPath)
           || myOptimizedFlatWatchRoots.contains(reportedPath)
           || (isFile && myOptimizedFlatWatchRoots.contains(getParentPath(reportedPath)));
  }

  /**
   * Maps reported paths from canonical representation and linked locations to requested paths,
   * then filters out those that do not fall under watched roots.
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
  @NotNull Collection<String> mapToOriginalWatchRoots(@NotNull String reportedPath, boolean isExact) {
    if (myOptimizedFlatWatchRoots.isEmpty() && myOptimizedRecursiveWatchRoots.isEmpty()) return Collections.emptyList();

    Collection<String> affectedPaths = applyMapping(reportedPath);
    Collection<String> changedPaths = new HashSet<>();

    for (String affectedPath : affectedPaths) {
      if (WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, affectedPath)
          || myOptimizedFlatWatchRoots.contains(affectedPath)
          || (isExact && myOptimizedFlatWatchRoots.contains(getParentPath(affectedPath)))) {
        changedPaths.add(affectedPath);
      }
      else if (!isExact) {
        addPrefixedPaths(myOptimizedRecursiveWatchRoots, affectedPath, changedPaths);
        addPrefixedPaths(myOptimizedFlatWatchRoots, affectedPath, changedPaths);
      }
    }

    return changedPaths;
  }

  private Collection<String> applyMapping(String reportedPath) {
    if (myPathMappings.isEmpty()) {
      return Collections.singletonList(reportedPath);
    }
    List<String> results = new SmartList<>(reportedPath);
    WatchRootsUtil.forEachPathSegment(reportedPath, File.separatorChar, path -> {
      Collection<String> mappedPaths = myPathMappings.get(path);
      for (String mappedPath : mappedPaths) {
        results.add(mappedPath + reportedPath.substring(path.length()));
      }
      return true;
    });
    return results;
  }

  private static void addPrefixedPaths(NavigableSet<String> paths, String prefix, Collection<String> result) {
    String possibleRoot = paths.ceiling(prefix);
    if (possibleRoot != null && FileUtil.startsWith(possibleRoot, prefix)) {
      // It's worth going for the set and iterator
      for (String root : paths.tailSet(prefix, false)) {
        if (FileUtil.startsWith(root, prefix)) {
          result.add(root);
        }
        else {
          return;
        }
      }
    }
  }
}
