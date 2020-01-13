// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;

import java.io.File;
import java.util.*;

import static com.intellij.util.PathUtil.getParentPath;

final class CanonicalPathMap {

  public static CanonicalPathMap empty() {
    return new CanonicalPathMap(Collections.emptyNavigableSet(),
                                Collections.emptyNavigableSet(),
                                MultiMap.createConcurrentSet());
  }

  private final List<@SystemDependent String> myCanonicalRecursiveWatchRoots;
  private final List<@SystemDependent String> myCanonicalFlatWatchRoots;

  private final NavigableSet<@SystemDependent String> myOptimizedRecursiveWatchRoots;
  private final NavigableSet<@SystemDependent String> myOptimizedFlatWatchRoots;

  private final MultiMap<@SystemDependent String, @SystemDependent String> myPathMappings;

  CanonicalPathMap(@NotNull NavigableSet<@SystemDependent String> optimizedRecursiveWatchRoots,
                   @NotNull NavigableSet<@SystemDependent String> optimizedFlatWatchRoots,
                   @NotNull MultiMap<@SystemDependent String, @SystemDependent String> initialPathMappings) {

    myOptimizedRecursiveWatchRoots = optimizedRecursiveWatchRoots;
    myOptimizedFlatWatchRoots = optimizedFlatWatchRoots;
    myPathMappings = initialPathMappings;

    Map<String, String> canonicalPathMappings = resolveCanonicalPaths();

    NavigableSet<String> canonicalRecursiveRoots = createCanonicalRecursiveRoots(canonicalPathMappings);
    Set<String> canonicalFlatRoots = createCanonicalFlatRoots(canonicalRecursiveRoots, canonicalPathMappings);

    myCanonicalRecursiveWatchRoots = ContainerUtil.map(canonicalRecursiveRoots, FileUtil::toSystemDependentName);
    myCanonicalFlatWatchRoots = ContainerUtil.map(canonicalFlatRoots, FileUtil::toSystemDependentName);
  }

  @NotNull
  public List<@SystemDependent String> getCanonicalRecursiveWatchRoots() {
    return myCanonicalRecursiveWatchRoots;
  }

  @NotNull
  public List<@SystemDependent String> getCanonicalFlatWatchRoots() {
    return myCanonicalFlatWatchRoots;
  }

  public void addMapping(@NotNull Collection<? extends Pair<@SystemDependent String, @SystemDependent String>> mapping) {
    for (Pair<String, String> pair : mapping) {
      String from = ensureNormalized(pair.first);
      String to = ensureNormalized(pair.second);

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
  @NotNull
  public Collection<@SystemDependent String> mapToOriginalWatchRoots(@SystemDependent @NotNull String reportedPath, boolean isExact) {
    if (myOptimizedFlatWatchRoots.isEmpty() && myOptimizedRecursiveWatchRoots.isEmpty()) return Collections.emptyList();

    boolean endsWithSlash = reportedPath.endsWith("/");

    Collection<@SystemDependent String> affectedPaths = applyMapping(ensureNormalized(reportedPath));

    Set<@SystemDependent String> changedPaths = new HashSet<>();

    for (String affectedPath : affectedPaths) {
      if (WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, affectedPath)
          || myOptimizedFlatWatchRoots.contains(affectedPath)
          || (isExact && myOptimizedFlatWatchRoots.contains(ensureNormalized(getParentPath(affectedPath))))) {
        changedPaths.add(affectedPath);
      }
      else if (!isExact) {
        addPrefixedPaths(myOptimizedRecursiveWatchRoots, affectedPath, changedPaths);
        addPrefixedPaths(myOptimizedFlatWatchRoots, affectedPath, changedPaths);
      }
    }
    return ContainerUtil.map(changedPaths, path -> endsWithSlash ? path : path.substring(0, path.length() - 1));
  }

  private Collection<@SystemDependent String> applyMapping(@SystemDependent @NotNull String reportedPath) {
    if (myPathMappings.isEmpty()) {
      return Collections.singletonList(reportedPath);
    }
    List<String> results = new SmartList<>(reportedPath);
    WatchRootsUtil.forEachFilePathSegment(reportedPath, File.separatorChar, path -> {
      Collection<String> mappedPaths = myPathMappings.get(path);
      for (String mappedPath : mappedPaths) {
        results.add(mappedPath + reportedPath.substring(path.length()));
      }
      return true;
    });
    return results;
  }

  private static void addPrefixedPaths(@NotNull NavigableSet<String> paths,
                                       @NotNull String prefix,
                                       @NotNull Collection<String> result) {
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

  @Nullable
  @SystemDependent
  private static String getCanonicalFile(@NotNull @SystemDependent String path) {
    return ensureNormalized(FileSystemUtil.resolveSymLink(path));
  }

  private Map<String, String> resolveCanonicalPaths() {
    Map<String, String> result = ContainerUtil.newConcurrentMap();
    StreamEx.of(myOptimizedRecursiveWatchRoots)
      .append(myOptimizedFlatWatchRoots)
      .parallel()
      .forEach(root -> {
        String canonicalRoot = getCanonicalFile(root);
        if (canonicalRoot != null && WatchRootsUtil.FILE_NAME_COMPARATOR.compare(canonicalRoot, root) != 0) {
          result.put(root, canonicalRoot);
        }
      });
    return result;
  }

  @NotNull
  private Set<String> createCanonicalFlatRoots(NavigableSet<String> canonicalRecursiveRoots,
                                               Map<String, String> canonicalPaths) {
    Set<String> canonicalFlatRoots = new HashSet<>();
    for (String flatRoot : myOptimizedFlatWatchRoots) {
      String canonicalFlat = canonicalPaths.get(flatRoot);
      if (canonicalFlat != null) {
        myPathMappings.putValue(canonicalFlat, flatRoot);
      }
      else {
        canonicalFlat = flatRoot;
      }
      if (!WatchRootsUtil.isCoveredRecursively(canonicalRecursiveRoots, canonicalFlat)) {
        canonicalFlatRoots.add(canonicalFlat);
      }
    }
    return canonicalFlatRoots;
  }

  private NavigableSet<String> createCanonicalRecursiveRoots(Map<String, String> canonicalPathMappings) {
    NavigableSet<String> result = WatchRootsUtil.createFileNavigableSet();
    for (String current : myOptimizedRecursiveWatchRoots) {
      String canonical = canonicalPathMappings.get(current);
      if (canonical != null) {
        myPathMappings.putValue(canonical, current);
      }
      else {
        canonical = current;
      }
      WatchRootsUtil.insertRecursivePath(result, canonical);
    }
    return result;
  }

  @Contract("null -> null; !null -> !null")
  @SystemDependent
  static String ensureNormalized(@SystemDependent @Nullable String path) {
    if (path != null && !path.endsWith(File.separator)){
      return path + File.separator;
    }
    return path;
  }
}
