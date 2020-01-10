// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem.WatchRequest;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.util.*;

import static com.intellij.util.PathUtil.getParentPath;

public class WatchRootsMap {

  protected static final Logger LOG = Logger.getInstance(WatchRootsMap.class);

  private static final Comparator<String> FILE_NAME_COMPARATOR = SystemInfo.isFileSystemCaseSensitive
                                                                 ? String::compareTo
                                                                 : String::compareToIgnoreCase;

  private final FileWatcher myFileWatcher;

  private final Object myLock = new Object();

  private final NavigableMap<String, List<WatchRequest>> myRecursiveWatchRoots = createFileNavigableMap();
  private final NavigableMap<String, List<WatchRequest>> myFlatWatchRoots = createFileNavigableMap();

  public WatchRootsMap(FileWatcher fileWatcher, @NotNull Disposable parentDisposable) {
    myFileWatcher = fileWatcher;
  }

  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequestsToRemove,
                                               @NotNull Collection<String> recursiveRootsToAdd,
                                               @NotNull Collection<String> flatRootsToAdd) {
    Set<WatchRequest> result = new HashSet<>(recursiveRootsToAdd.size() + flatRootsToAdd.size());

    synchronized (myLock) {
      boolean updated = updateWatchRoots(
        recursiveRootsToAdd,
        ContainerUtil.map2SetNotNull(watchRequestsToRemove, req -> req.isToWatchRecursively() ? req : null),
        result, false, myRecursiveWatchRoots);

      updated = updateWatchRoots(
        flatRootsToAdd,
        ContainerUtil.map2SetNotNull(watchRequestsToRemove, req -> req.isToWatchRecursively() ? null : req),
        result, updated, myFlatWatchRoots);

      if (updated) {
        updateFileWatcher();
      }
    }
    return result;
  }

  public void clear() {
    synchronized (myLock) {
      myRecursiveWatchRoots.clear();
      myFlatWatchRoots.clear();
    }
  }

  private boolean updateWatchRoots(@NotNull Collection<String> rootsToAdd,
                                   Set<WatchRequest> toRemoveSet,
                                   Set<WatchRequest> result,
                                   boolean updated,
                                   NavigableMap<String, List<WatchRequest>> roots) {
    for (String watchRoot : rootsToAdd) {
      watchRoot = mapToSystemPath(watchRoot);
      if (watchRoot != null) {
        List<WatchRequest> requests = roots.computeIfAbsent(watchRoot, (key) -> new SmartList<>());
        boolean foundSameRequest = false;
        if (!toRemoveSet.isEmpty()) {
          for (WatchRequest currentRequest : requests) {
            if (toRemoveSet.remove(currentRequest)) {
              foundSameRequest = true;
              result.add(currentRequest);
            }
          }
        }
        if (!foundSameRequest) {
          updated |= requests.isEmpty() && !isRecursivelyWatched(watchRoot);
          WatchRequest newRequest = new WatchRequestImpl(watchRoot, true);
          requests.add(newRequest);
          result.add(newRequest);
        }
      }
    }
    if (!toRemoveSet.isEmpty()) {
      for (WatchRequest request : toRemoveSet) {
        List<WatchRequest> requests = roots.get(request.getRootPath());
        if (requests != null) {
          requests.remove(request);
          if (requests.isEmpty()) {
            roots.remove(request.getRootPath());
            updated = true;
          }
        }
      }
    }
    return updated;
  }

  private void updateFileWatcher() {
    NavigableSet<String> recursiveWatchRoots = optimizeRecursiveRoots(myRecursiveWatchRoots.navigableKeySet());
    NavigableSet<String> flatWatchRoots = optimizeFlatRoots(myFlatWatchRoots.navigableKeySet(), recursiveWatchRoots);

    myFileWatcher.setWatchRoots(new DefaultWatchRootsMappingProvider(recursiveWatchRoots, flatWatchRoots));
  }

  private static boolean isCoveredRecursively(NavigableSet<String> recursiveRoots, String path) {
    String recursiveRoot = recursiveRoots.floor(path);
    return recursiveRoot != null && FileUtil.startsWith(path, recursiveRoot);
  }

  private static void insertRecursivePath(NavigableSet<String> recursiveRoots, String path) {
    if (!isCoveredRecursively(recursiveRoots, path)) {
      recursiveRoots.add(path);
      // Remove any roots covered by newly added
      String higher;
      while ((higher = recursiveRoots.higher(path)) != null && FileUtil.startsWith(higher, path)) {
        recursiveRoots.remove(higher);
      }
    }
  }

  private boolean isRecursivelyWatched(@SystemIndependent String watchRootKey) {
    int position = watchRootKey.indexOf('/');
    while (position >= 0 && position < watchRootKey.length() - 1) {
      String parentPath = watchRootKey.substring(0, position + 1);
      Map.Entry<String, List<WatchRequest>> entry = myRecursiveWatchRoots.ceilingEntry(parentPath);
      if (entry != null) {
        if (FILE_NAME_COMPARATOR.compare(entry.getKey(), watchRootKey) == 0
            && !entry.getValue().isEmpty()) {
          return true;
        }
      }
      position = watchRootKey.indexOf('/', position + 1);
    }
    return false;
  }

  @Nullable
  @SystemIndependent
  private static String mapToSystemPath(@SystemIndependent @NotNull String rootPath) {
    int index = rootPath.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (index >= 0) rootPath = rootPath.substring(0, index);

    File rootFile = new File(FileUtil.toSystemDependentName(rootPath));
    if (!rootFile.isAbsolute()) {
      LOG.warn("Invalid path: " + rootPath);
      return null;
    }
    String absolutePath = rootFile.getAbsolutePath();
    if (!absolutePath.endsWith(File.separator)) {
      absolutePath += File.separator;
    }
    return FileUtil.toSystemIndependentName(absolutePath);
  }

  @NotNull
  private static NavigableSet<String> optimizeRecursiveRoots(@NotNull SortedSet<String> recursiveRoots) {
    NavigableSet<String> result = createFileNavigableSet();
    String last = null;
    for (String root : recursiveRoots) {
      if (last == null) {
        last = root;
      }
      else if (!FileUtil.startsWith(root, last)) {
        result.add(last);
        last = root;
      }
    }
    if (last != null) {
      result.add(last);
    }
    return result;
  }

  private static NavigableSet<String> optimizeFlatRoots(Collection<String> flatRoots, NavigableSet<String> recursiveRoots) {
    NavigableSet<String> result = createFileNavigableSet();
    for (String flatRoot : flatRoots) {
      if (!isCoveredRecursively(recursiveRoots, flatRoot)) {
        result.add(flatRoot);
      }
    }
    return result;
  }

  private static NavigableSet<String> createFileNavigableSet() {
    return new TreeSet<>(FILE_NAME_COMPARATOR);
  }

  private static <T> NavigableMap<String, T> createFileNavigableMap() {
    return new TreeMap<>(FILE_NAME_COMPARATOR);
  }

  interface WatchRootsMappingProvider {

    void initialize();

    @NotNull
    List<@SystemDependent String> getCanonicalRecursiveWatchRoots();

    @NotNull
    List<@SystemDependent String> getCanonicalFlatWatchRoots();

    void addMapping(@NotNull Collection<? extends Pair<@SystemDependent String, @SystemDependent String>> mapping);

    @NotNull
    Collection<@SystemDependent String> mapToOriginalWatchRoots(@SystemDependent @NotNull String path, boolean isExact);
  }

  private static class WatchRequestImpl implements WatchRequest {
    private final String myFSRootPath;
    private final boolean myWatchRecursively;

    WatchRequestImpl(@SystemIndependent @NotNull String rootPath, boolean watchRecursively) {
      myFSRootPath = rootPath;
      myWatchRecursively = watchRecursively;
    }

    @Override
    @NotNull
    @SystemIndependent
    public String getRootPath() {
      return myFSRootPath;
    }

    @Override
    public boolean isToWatchRecursively() {
      return myWatchRecursively;
    }

    @Override
    public String toString() {
      return getRootPath();
    }
  }

  public static class EmptyWatchRootsMappingProvider extends DefaultWatchRootsMappingProvider {

    public EmptyWatchRootsMappingProvider() {
      super(Collections.emptyNavigableSet(), Collections.emptyNavigableSet());
    }
  }

  private static class DefaultWatchRootsMappingProvider implements WatchRootsMappingProvider {

    private List<@SystemDependent String> myCanonicalRecursiveRoots = null;
    private List<@SystemDependent String> myCanonicalFlatRoots = null;

    private final NavigableSet<@SystemIndependent String> myRecursiveWatchRoots;
    private final NavigableSet<@SystemIndependent String> myFlatWatchRoots;

    private final MultiMap<@SystemIndependent String, @SystemIndependent String> myPathMappings;

    DefaultWatchRootsMappingProvider(@NotNull NavigableSet<String> recursiveWatchRoots,
                                     @NotNull NavigableSet<String> flatWatchRoots) {

      myRecursiveWatchRoots = recursiveWatchRoots;
      myFlatWatchRoots = flatWatchRoots;
      myPathMappings = MultiMap.createConcurrentSet();
    }

    /**
     * Allow to run time consuming logic in a separate thread
     */
    @Override
    public void initialize() {
      Map<String, String> canonicalPaths = resolveCanonicalPaths(myRecursiveWatchRoots, myFlatWatchRoots);

      NavigableSet<String> canonicalRecursiveRoots = createCanonicalRecursiveRoots(myRecursiveWatchRoots, canonicalPaths, myPathMappings);
      Set<String> canonicalFlatRoots = createCanonicalFlatRoots(myRecursiveWatchRoots, myFlatWatchRoots,
                                                                canonicalRecursiveRoots, canonicalPaths, myPathMappings);

      myCanonicalRecursiveRoots = ContainerUtil.map(canonicalRecursiveRoots, FileUtil::toSystemDependentName);
      myCanonicalFlatRoots = ContainerUtil.map(canonicalFlatRoots, FileUtil::toSystemDependentName);
    }

    @NotNull
    @Override
    public List<@SystemDependent String> getCanonicalRecursiveWatchRoots() {
      return myCanonicalRecursiveRoots;
    }

    @NotNull
    @Override
    public List<@SystemDependent String> getCanonicalFlatWatchRoots() {
      return myCanonicalFlatRoots;
    }

    @Override
    public void addMapping(@NotNull Collection<? extends Pair<@SystemDependent String, @SystemDependent String>> mapping) {
      for (Pair<String, String> pair : mapping) {
        String from = FileUtil.toSystemIndependentName(pair.first);
        String to = FileUtil.toSystemIndependentName(pair.second);

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

    @NotNull
    @Override
    public Collection<@SystemDependent String> mapToOriginalWatchRoots(@SystemDependent @NotNull String reportedPath, boolean isExact) {
      if (myFlatWatchRoots.isEmpty() && myRecursiveWatchRoots.isEmpty()) return Collections.emptyList();

      boolean endsWithSlash = reportedPath.endsWith("/");
      if (!endsWithSlash) {
        reportedPath += "/";
      }

      @SystemIndependent
      Collection<String> affectedPaths = applyMapping(FileUtil.toSystemIndependentName(reportedPath));

      @SystemIndependent
      Set<String> changedPaths = new HashSet<>();

      for (String affectedPath : affectedPaths) {
        String normalized = affectedPath.endsWith("/") ? affectedPath : affectedPath + "/";
        if (isCoveredRecursively(myRecursiveWatchRoots, normalized)
            || myFlatWatchRoots.contains(normalized)
            || (isExact && myFlatWatchRoots.contains(getParentPath(normalized)))) {
          changedPaths.add(affectedPath);
        }
        else if (!isExact) {
          addPrefixedPaths(myRecursiveWatchRoots, affectedPath, changedPaths);
          addPrefixedPaths(myFlatWatchRoots, affectedPath, changedPaths);
        }
      }
      return ContainerUtil.map(changedPaths, path -> {
        return FileUtil.toSystemDependentName(endsWithSlash ? path : path.substring(0, path.length() - 1));
      });
    }

    private Collection<@SystemIndependent String> applyMapping(@SystemIndependent @NotNull String reportedPath) {
      if (myPathMappings.isEmpty()) {
        return Collections.singletonList(reportedPath);
      }
      int length = reportedPath.length();
      List<String> results = new SmartList<>(reportedPath);

      int position = reportedPath.indexOf('/');
      while (position >= 0 && position < length) {
        String path = reportedPath.substring(0, position + 1);
        Collection<String> mappedPaths = myPathMappings.get(path);
        for (String mappedPath : mappedPaths) {
          results.add(mappedPath + reportedPath.substring(position + 1, length));
        }
        position = reportedPath.indexOf('/', position + 1);
      }

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
    @SystemIndependent
    private static String getCanonicalFile(@NotNull String path) {
      String result = FileSystemUtil.resolveSymLink(FileUtil.toSystemDependentName(path));
      if (result != null) {
        result = FileUtil.toSystemIndependentName(result);
        if (!result.endsWith("/")) {
          result += "/";
        }
      }
      return result;
    }

    private static Map<String, String> resolveCanonicalPaths(Collection<String> recursiveRoots, Collection<String> flatRoots) {
      Map<String, String> result = ContainerUtil.newConcurrentMap();
      StreamEx.of(recursiveRoots)
        .append(flatRoots)
        .parallel()
        .forEach(root -> {
          String canonicalRoot = getCanonicalFile(root);
          if (canonicalRoot != null && FILE_NAME_COMPARATOR.compare(canonicalRoot, root) != 0) {
            result.put(root, canonicalRoot);
          }
        });
      return result;
    }

    @NotNull
    private static Set<String> createCanonicalFlatRoots(NavigableSet<String> recursiveWatchRoots,
                                                        Set<String> flatWatchRoots,
                                                        NavigableSet<String> canonicalRecursiveRoots,
                                                        Map<String, String> canonicalPaths,
                                                        MultiMap<String, String> pathMappings) {
      Set<String> canonicalFlatRoots = new HashSet<>();
      for (String flatRoot : flatWatchRoots) {
        if (!isCoveredRecursively(recursiveWatchRoots, flatRoot)) {
          String canonicalFlat = canonicalPaths.get(flatRoot);
          if (canonicalFlat != null) {
            pathMappings.putValue(canonicalFlat, flatRoot);
          }
          else {
            canonicalFlat = flatRoot;
          }
          if (!isCoveredRecursively(canonicalRecursiveRoots, canonicalFlat)) {
            canonicalFlatRoots.add(canonicalFlat);
          }
        }
      }
      return canonicalFlatRoots;
    }

    private static NavigableSet<String> createCanonicalRecursiveRoots(NavigableSet<String> recursiveRoots,
                                                                      Map<String, String> canonicalPaths,
                                                                      MultiMap<String, String> pathMappings) {
      NavigableSet<String> result = createFileNavigableSet();
      String last = null;
      for (String current : recursiveRoots) {
        if (last == null) {
          last = current;
        }
        else if (!FileUtil.startsWith(current, last)) {
          String canonical = canonicalPaths.get(last);
          if (canonical != null) {
            pathMappings.putValue(canonical, last);
          }
          else {
            canonical = last;
          }
          insertRecursivePath(result, canonical);
          last = current;
        }
      }
      if (last != null) {
        String canonical = canonicalPaths.get(last);
        if (canonical != null) {
          pathMappings.putValue(canonical, last);
        }
        else {
          canonical = last;
        }
        insertRecursivePath(result, canonical);
      }
      return result;
    }
  }
}
