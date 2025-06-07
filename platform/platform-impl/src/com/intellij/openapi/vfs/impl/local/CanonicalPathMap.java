// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import static com.intellij.util.PathUtil.getParentPath;

/**
 * Unless stated otherwise, all paths are {@link org.jetbrains.annotations.SystemDependent @SystemDependent}.
 */
@ApiStatus.Internal
public final class CanonicalPathMap {
  private static final Executor ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "CanonicalPathMap", ProcessIOExecutorService.INSTANCE, Runtime.getRuntime().availableProcessors());

  static CanonicalPathMap empty() {
    return new CanonicalPathMap(Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Collections.emptyNavigableSet());
  }

  private final NavigableSet<String> myOptimizedRecursiveWatchRoots;
  private final NavigableSet<String> myOptimizedFlatWatchRoots;
  private Collection<Pair<String, String>> myInitialPathMappings;
  private final MultiMap<String, String> myPathMappings;

  public CanonicalPathMap(
    @NotNull NavigableSet<String> optimizedRecursiveWatchRoots,
    @NotNull NavigableSet<String> optimizedFlatWatchRoots,
    @NotNull Collection<Pair<String, String>> initialPathMappings
  ) {
    myOptimizedRecursiveWatchRoots = optimizedRecursiveWatchRoots;
    myOptimizedFlatWatchRoots = optimizedFlatWatchRoots;
    myInitialPathMappings = initialPathMappings;
    myPathMappings = MultiMap.createConcurrentSet();
  }

  @VisibleForTesting
  public @NotNull Pair<List<String>, List<String>> getCanonicalWatchRoots() {
    initializeMappings();

    var canonicalPathMappings = new ConcurrentHashMap<String, String>();
    var futures = Stream.concat(myOptimizedRecursiveWatchRoots.stream(), myOptimizedFlatWatchRoots.stream())
      .map(root -> CompletableFuture.runAsync(() -> {
        var canonicalRoot = FileSystemUtil.resolveSymLink(root);
        if (canonicalRoot != null && OSAgnosticPathUtil.COMPARATOR.compare(canonicalRoot, root) != 0) {
          canonicalPathMappings.put(root, canonicalRoot);
        }
      }, ourExecutor))
      .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(futures).join();

    var canonicalRecursiveRoots = WatchRootsUtil.createFileNavigableSet();
    for (var root : myOptimizedRecursiveWatchRoots) {
      var canonical = canonicalPathMappings.get(root);
      if (canonical != null) {
        myPathMappings.putValue(canonical, root);
      }
      else {
        canonical = root;
      }
      WatchRootsUtil.insertRecursivePath(canonicalRecursiveRoots, canonical);
    }

    var canonicalFlatRoots = new HashSet<String>();
    for (var root : myOptimizedFlatWatchRoots) {
      var canonical = canonicalPathMappings.get(root);
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

    return new Pair<>(new ArrayList<>(canonicalRecursiveRoots), new ArrayList<>(canonicalFlatRoots));
  }

  private void initializeMappings() {
    var lastMapping = "";
    var lastCollection = (Collection<String>)null;

    for (var mapping: myInitialPathMappings) {
      var currentMapping = mapping.first;
      // If mappings are sorted, the below check should improve performance by avoiding unnecessary gets from the concurrent multi-map.
      if (!currentMapping.equals(lastMapping) || lastCollection == null) {
        lastMapping = mapping.first;
        lastCollection = myPathMappings.getModifiable(lastMapping);
      }
      lastCollection.add(mapping.second);
    }
    // Freeing the memory
    myInitialPathMappings = null;
  }

  @VisibleForTesting
  public void addMapping(@NotNull Collection<? extends Pair<String, String>> mapping) {
    for (var pair : mapping) {
      var from = pair.first;
      var to = pair.second;

      // See if we are adding a mapping that itself should be mapped to a different path
      // Example: /foo/real_path -> /foo/symlink, /foo/remapped_path -> /foo/real_path
      // In this case, if the file watcher returns /foo/remapped_path/file.txt, we want to report /foo/symlink/file.txt back to IntelliJ.
      var preRemapPathToWatchedPaths = applyMapping(to);
      for (var realWatchedPath : preRemapPathToWatchedPaths) {
        myPathMappings.putValue(from, realWatchedPath);
      }

      // Since there can be more than one file watcher and REMAPPING is an implementation detail of the native file watcher,
      // add the mapping as usual even if we added data above.
      myPathMappings.putValue(from, to);
    }
  }

  boolean belongsToWatchRoots(@NotNull String reportedPath, boolean isFile) {
    return WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, reportedPath) ||
           myOptimizedFlatWatchRoots.contains(reportedPath) ||
           (isFile && myOptimizedFlatWatchRoots.contains(getParentPath(reportedPath)));
  }

  /**
   * Maps reported paths from canonical representation and linked locations to requested paths,
   * then filters out those that do not fall under watched roots.
   *
   * <h3>Exactness</h3>
   * Some watchers (e.g., the native one on macOS) report a parent directory as dirty instead of the "exact" file path.
   * <p>
   * For flat roots, it means that if and only if the exact dirty file path is returned, we should compare the parent to the flat roots,
   * otherwise we should compare to a path given to us because it is already the parent of the actual dirty path.
   * <p>
   * For recursive roots, if the path given to us is already the parent of the actual dirty path, we need to compare the path to the parent
   * of the recursive root because if the root itself was changed, we need to know about it.
   */
  @VisibleForTesting
  public @NotNull Collection<String> mapToOriginalWatchRoots(@NotNull String reportedPath, boolean isExact) {
    if (myOptimizedFlatWatchRoots.isEmpty() && myOptimizedRecursiveWatchRoots.isEmpty()) return Collections.emptyList();

    var affectedPaths = applyMapping(reportedPath);
    var changedPaths = new HashSet<String>();

    for (var affectedPath : affectedPaths) {
      if (WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, affectedPath) ||
          myOptimizedFlatWatchRoots.contains(affectedPath) ||
          (isExact && myOptimizedFlatWatchRoots.contains(getParentPath(affectedPath)))) {
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
      return List.of(reportedPath);
    }

    var results = new SmartList<>(reportedPath);
    WatchRootsUtil.forEachPathSegment(reportedPath, File.separatorChar, path -> {
      myPathMappings.get(path).forEach(mapped -> results.add(mapped + reportedPath.substring(path.length())));
      return true;
    });
    return results;
  }

  private static void addPrefixedPaths(NavigableSet<String> paths, String prefix, Collection<? super String> result) {
    var possibleRoot = paths.ceiling(prefix);
    if (possibleRoot != null && FileUtil.startsWith(possibleRoot, prefix)) {
      // It's worth going for the set and iterator
      for (var root : paths.tailSet(prefix, false)) {
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
