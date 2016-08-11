/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.CollectionQuery;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SLRUMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

public class RootIndex {
  public static final Comparator<OrderEntry> BY_OWNER_MODULE = (o1, o2) -> {
    String name1 = o1.getOwnerModule().getName();
    String name2 = o2.getOwnerModule().getName();
    return name1.compareTo(name2);
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.RootIndex");
  private static final FileTypeRegistry ourFileTypes = FileTypeRegistry.getInstance();

  private final Map<VirtualFile, String> myPackagePrefixByRoot = ContainerUtil.newHashMap();

  private final InfoCache myInfoCache;
  private final List<JpsModuleSourceRootType<?>> myRootTypes = ContainerUtil.newArrayList();
  private final TObjectIntHashMap<JpsModuleSourceRootType<?>> myRootTypeId = new TObjectIntHashMap<>();
  @NotNull private final Project myProject;
  private final PackageDirectoryCache myPackageDirectoryCache;
  private OrderEntryGraph myOrderEntryGraph;

  // made public for Upsource
  public RootIndex(@NotNull Project project, @NotNull InfoCache cache) {
    myProject = project;
    myInfoCache = cache;
    final RootInfo info = buildRootInfo(project);

    MultiMap<String, VirtualFile> rootsByPackagePrefix = MultiMap.create();
    Set<VirtualFile> allRoots = info.getAllRoots();
    for (VirtualFile root : allRoots) {
      List<VirtualFile> hierarchy = getHierarchy(root, allRoots, info);
      Pair<DirectoryInfo, String> pair = hierarchy != null
                                         ? calcDirectoryInfo(root, hierarchy, info)
                                         : new Pair<>(NonProjectDirectoryInfo.IGNORED, null);
      cacheInfos(root, root, pair.first);
      rootsByPackagePrefix.putValue(pair.second, root);
      myPackagePrefixByRoot.put(root, pair.second);
    }
    myPackageDirectoryCache = new PackageDirectoryCache(rootsByPackagePrefix) {
      @Override
      protected boolean isPackageDirectory(@NotNull VirtualFile dir, @NotNull String packageName) {
        return getInfoForFile(dir).isInProject() && packageName.equals(getPackageName(dir));
      }
    };
  }

  @NotNull
  private RootInfo buildRootInfo(@NotNull Project project) {
    final RootInfo info = new RootInfo();
    for (final Module module : ModuleManager.getInstance(project).getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

      for (final VirtualFile contentRoot : moduleRootManager.getContentRoots()) {
        if (!info.contentRootOf.containsKey(contentRoot)) {
          info.contentRootOf.put(contentRoot, module);
        }
      }

      for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
        if (!(contentEntry instanceof ContentEntryImpl) || !((ContentEntryImpl)contentEntry).isDisposed()) {
          for (VirtualFile excludeRoot : contentEntry.getExcludeFolderFiles()) {
            info.excludedFromModule.put(excludeRoot, module);
          }
        }

        // Init module sources
        for (final SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          final VirtualFile sourceFolderRoot = sourceFolder.getFile();
          if (sourceFolderRoot != null) {
            info.rootTypeId.put(sourceFolderRoot, getRootTypeId(sourceFolder.getRootType()));
            info.classAndSourceRoots.add(sourceFolderRoot);
            info.sourceRootOf.putValue(sourceFolderRoot, module);
            info.packagePrefix.put(sourceFolderRoot, sourceFolder.getPackagePrefix());
          }
        }
      }

      for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
          final VirtualFile[] sourceRoots = entry.getRootFiles(OrderRootType.SOURCES);
          final VirtualFile[] classRoots = entry.getRootFiles(OrderRootType.CLASSES);

          // Init library sources
          for (final VirtualFile sourceRoot : sourceRoots) {
            info.classAndSourceRoots.add(sourceRoot);
            info.libraryOrSdkSources.add(sourceRoot);
            info.packagePrefix.put(sourceRoot, "");
          }

          // init library classes
          for (final VirtualFile classRoot : classRoots) {
            info.classAndSourceRoots.add(classRoot);
            info.libraryOrSdkClasses.add(classRoot);
            info.packagePrefix.put(classRoot, "");
          }

          if (orderEntry instanceof LibraryOrderEntry) {
            Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
            if (library != null) {
              for (VirtualFile root : ((LibraryEx)library).getExcludedRoots()) {
                info.excludedFromLibraries.putValue(root, library);
              }
              for (VirtualFile root : sourceRoots) {
                info.sourceOfLibraries.putValue(root, library);
              }
              for (VirtualFile root : classRoots) {
                info.classOfLibraries.putValue(root, library);
              }
            }
          }
        }
      }
    }

    for (AdditionalLibraryRootsProvider provider : Extensions.getExtensions(AdditionalLibraryRootsProvider.EP_NAME)) {
      Collection<VirtualFile> roots = provider.getAdditionalProjectLibrarySourceRoots(project);
      info.libraryOrSdkSources.addAll(roots);
      info.classAndSourceRoots.addAll(roots);
    }
    for (DirectoryIndexExcludePolicy policy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, project)) {
      Collections.addAll(info.excludedFromProject, policy.getExcludeRootsForProject());
    }
    return info;
  }

  @NotNull
  private synchronized OrderEntryGraph getOrderEntryGraph() {
    if (myOrderEntryGraph == null) {
      RootInfo rootInfo = buildRootInfo(myProject);
      myOrderEntryGraph = new OrderEntryGraph(myProject, rootInfo);
    }
    return myOrderEntryGraph;
  }

  /**
   * A reverse dependency graph of (library, jdk, module, module source) -> (module).
   *
   * <p>Each edge carries with it the associated OrderEntry that caused the dependency.
   */
  private static class OrderEntryGraph {

    private static class Edge {
      Module myKey;
      ModuleOrderEntry myOrderEntry; // Order entry from myKey -> the node containing the edge
      boolean myRecursive; // Whether this edge should be descended into during graph walk

      public Edge(Module key, ModuleOrderEntry orderEntry, boolean recursive) {
        myKey = key;
        myOrderEntry = orderEntry;
        myRecursive = recursive;
      }

      @Override
      public String toString() {
        return myOrderEntry.toString();
      }
    }

    private static class Node {
      Module myKey;
      List<Edge> myEdges = new ArrayList<>();

      @Override
      public String toString() {
        return myKey.toString();
      }
    }

    private static class Graph {
      Map<Module, Node> myNodes = new HashMap<>();
    }

    final Project myProject;
    final RootInfo myRootInfo;
    final Set<VirtualFile> myAllRoots;
    Graph myGraph;
    MultiMap<VirtualFile, Node> myRoots; // Map of roots to their root nodes, eg. library jar -> library node
    final SynchronizedSLRUCache<VirtualFile, List<OrderEntry>> myCache;
    private MultiMap<VirtualFile, OrderEntry> myLibClassRootEntries;
    private MultiMap<VirtualFile, OrderEntry> myLibSourceRootEntries;

    public OrderEntryGraph(Project project, RootInfo rootInfo) {
      myProject = project;
      myRootInfo = rootInfo;
      myAllRoots = myRootInfo.getAllRoots();
      int cacheSize = Math.max(25, (myAllRoots.size() / 100) * 2);
      myCache = new SynchronizedSLRUCache<VirtualFile, List<OrderEntry>>(cacheSize, cacheSize) {
        @NotNull
        @Override
        public List<OrderEntry> createValue(VirtualFile key) {
          return collectOrderEntries(key);
        }
      };
      initGraph();
      initLibraryRoots();
    }

    private void initGraph() {
      Graph graph = new Graph();

      MultiMap<VirtualFile, Node> roots = MultiMap.createSmart();

      for (final Module module : ModuleManager.getInstance(myProject).getModules()) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        List<OrderEnumerationHandler> handlers = OrderEnumeratorBase.getCustomHandlers(module);
        for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
          if (orderEntry instanceof ModuleOrderEntry) {
            ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
            final Module depModule = moduleOrderEntry.getModule();
            if (depModule != null) {
              Node node = graph.myNodes.get(depModule);
              OrderEnumerator en = OrderEnumerator.orderEntries(depModule).exportedOnly();
              if (node == null) {
                node = new Node();
                node.myKey = depModule;
                graph.myNodes.put(depModule, node);

                VirtualFile[] importedClassRoots = en.classes().usingCache().getRoots();
                for (VirtualFile importedClassRoot : importedClassRoots) {
                  roots.putValue(importedClassRoot, node);
                }

                VirtualFile[] importedSourceRoots = en.sources().usingCache().getRoots();
                for (VirtualFile sourceRoot : importedSourceRoots) {
                  roots.putValue(sourceRoot, node);
                }
              }
              boolean shouldRecurse = en.recursively().shouldRecurse(moduleOrderEntry, handlers);
              node.myEdges.add(new Edge(module, moduleOrderEntry, shouldRecurse));
            }
          }
        }
      }

      myGraph = graph;
      myRoots = roots;
    }

    private void initLibraryRoots() {
      MultiMap<VirtualFile, OrderEntry> libClassRootEntries = MultiMap.createSmart();
      MultiMap<VirtualFile, OrderEntry> libSourceRootEntries = MultiMap.createSmart();

      for (final Module module : ModuleManager.getInstance(myProject).getModules()) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
          if (orderEntry instanceof LibraryOrSdkOrderEntry) {
            final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
            for (final VirtualFile sourceRoot : entry.getRootFiles(OrderRootType.SOURCES)) {
              libSourceRootEntries.putValue(sourceRoot, orderEntry);
            }
            for (final VirtualFile classRoot : entry.getRootFiles(OrderRootType.CLASSES)) {
              libClassRootEntries.putValue(classRoot, orderEntry);
            }
          }
        }
      }

      myLibClassRootEntries = libClassRootEntries;
      myLibSourceRootEntries = libSourceRootEntries;
    }

    private List<OrderEntry> getOrderEntries(@NotNull VirtualFile file) {
      return myCache.get(file);
    }

    /**
     * Traverses the graph from the given file, collecting all encountered order entries.
     */
    private List<OrderEntry> collectOrderEntries(@NotNull VirtualFile file) {
      List<VirtualFile> roots = getHierarchy(file, myAllRoots, myRootInfo);
      if (roots == null) {
        return Collections.emptyList();
      }
      List<OrderEntry> result = new ArrayList<>();
      Stack<Node> stack = new Stack<>();
      for (VirtualFile root : roots) {
        Collection<Node> nodes = myRoots.get(root);
        for (Node node : nodes) {
          stack.push(node);
        }
      }

      Set<Node> seen = new HashSet<>();
      while (!stack.isEmpty()) {
        Node node = stack.pop();
        if (seen.contains(node)) {
          continue;
        }
        seen.add(node);

        for (Edge edge : node.myEdges) {
          result.add(edge.myOrderEntry);

          if (edge.myRecursive) {
            Node targetNode = myGraph.myNodes.get(edge.myKey);
            if (targetNode != null) {
              stack.push(targetNode);
            }
          }
        }
      }

      @Nullable VirtualFile libraryClassRoot = myRootInfo.findLibraryRootInfo(roots, false);
      @Nullable VirtualFile librarySourceRoot = myRootInfo.findLibraryRootInfo(roots, true);
      result.addAll(myRootInfo.getLibraryOrderEntries(roots, libraryClassRoot, librarySourceRoot, myLibClassRootEntries, myLibSourceRootEntries));

      VirtualFile moduleContentRoot = myRootInfo.findModuleRootInfo(roots);
      if (moduleContentRoot != null) {
        ContainerUtil.addIfNotNull(result, myRootInfo.getModuleSourceEntry(roots, moduleContentRoot, myLibClassRootEntries));
      }
      Collections.sort(result, BY_OWNER_MODULE);
      return result;
    }
  }

  private int getRootTypeId(@NotNull JpsModuleSourceRootType<?> rootType) {
    if (myRootTypeId.containsKey(rootType)) {
      return myRootTypeId.get(rootType);
    }

    int id = myRootTypes.size();
    if (id > DirectoryInfoImpl.MAX_ROOT_TYPE_ID) {
      LOG.error("Too many different types of module source roots (" + id + ") registered: " + myRootTypes);
    }
    myRootTypes.add(rootType);
    myRootTypeId.put(rootType, id);
    return id;
  }

  @NotNull
  public DirectoryInfo getInfoForFile(@NotNull VirtualFile file) {
    if (!file.isValid()) {
      return NonProjectDirectoryInfo.INVALID;
    }
    VirtualFile dir;
    if (!file.isDirectory()) {
      DirectoryInfo info = myInfoCache.getCachedInfo(file);
      if (info != null) {
        return info;
      }
      if (ourFileTypes.isFileIgnored(file)) {
        return NonProjectDirectoryInfo.IGNORED;
      }
      dir = file.getParent();
    }
    else {
      dir = file;
    }

    int count = 0;
    for (VirtualFile root = dir; root != null; root = root.getParent()) {
      if (++count > 1000) {
        throw new IllegalStateException("Possible loop in tree, started at " + dir.getName());
      }
      DirectoryInfo info = myInfoCache.getCachedInfo(root);
      if (info != null) {
        if (!dir.equals(root)) {
          cacheInfos(dir, root, info);
        }
        return info;
      }

      if (ourFileTypes.isFileIgnored(root)) {
        return cacheInfos(dir, root, NonProjectDirectoryInfo.IGNORED);
      }
    }

    return cacheInfos(dir, null, NonProjectDirectoryInfo.NOT_UNDER_PROJECT_ROOTS);
  }

  @NotNull
  private DirectoryInfo cacheInfos(VirtualFile dir, @Nullable VirtualFile stopAt, @NotNull DirectoryInfo info) {
    while (dir != null) {
      myInfoCache.cacheInfo(dir, info);
      if (dir.equals(stopAt)) {
        break;
      }
      dir = dir.getParent();
    }
    return info;
  }

  @NotNull
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull final String packageName, final boolean includeLibrarySources) {
    // Note that this method is used in upsource as well, hence, don't reduce this method's visibility.
    List<VirtualFile> result = myPackageDirectoryCache.getDirectoriesByPackageName(packageName);
    if (!includeLibrarySources) {
      result = ContainerUtil.filter(result, file -> {
        DirectoryInfo info = getInfoForFile(file);
        return info.isInProject() && (!info.isInLibrarySource() || info.isInModuleSource() || info.hasLibraryClassRoot());
      });
    }
    return new CollectionQuery<>(result);
  }

  @Nullable
  public String getPackageName(@NotNull final VirtualFile dir) {
    if (dir.isDirectory()) {
      if (ourFileTypes.isFileIgnored(dir)) {
        return null;
      }

      if (myPackagePrefixByRoot.containsKey(dir)) {
        return myPackagePrefixByRoot.get(dir);
      }

      final VirtualFile parent = dir.getParent();
      if (parent != null) {
        return getPackageNameForSubdir(getPackageName(parent), dir.getName());
      }
    }

    return null;
  }

  @Nullable
  protected static String getPackageNameForSubdir(@Nullable String parentPackageName, @NotNull String subdirName) {
    if (parentPackageName == null) return null;
    return parentPackageName.isEmpty() ? subdirName : parentPackageName + "." + subdirName;
  }

  @Nullable
  public JpsModuleSourceRootType<?> getSourceRootType(@NotNull DirectoryInfo directoryInfo) {
    return myRootTypes.get(directoryInfo.getSourceRootTypeId());
  }

  boolean resetOnEvents(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file == null || file.isDirectory()) {
        return true;
      }
    }
    return false;
  }

  @Nullable("returns null only if dir is under ignored folder")
  private static List<VirtualFile> getHierarchy(VirtualFile dir, @NotNull Set<VirtualFile> allRoots, @NotNull RootInfo info) {
    List<VirtualFile> hierarchy = ContainerUtil.newArrayList();
    boolean hasContentRoots = false;
    while (dir != null) {
      hasContentRoots |= info.contentRootOf.get(dir) != null;
      if (!hasContentRoots && ourFileTypes.isFileIgnored(dir)) {
        return null;
      }
      if (allRoots.contains(dir)) {
        hierarchy.add(dir);
      }
      dir = dir.getParent();
    }
    return hierarchy;
  }

  private static class RootInfo {
    // getDirectoriesByPackageName used to be in this order, some clients might rely on that
    @NotNull final LinkedHashSet<VirtualFile> classAndSourceRoots = ContainerUtil.newLinkedHashSet();

    @NotNull final Set<VirtualFile> libraryOrSdkSources = ContainerUtil.newHashSet();
    @NotNull final Set<VirtualFile> libraryOrSdkClasses = ContainerUtil.newHashSet();
    @NotNull final Map<VirtualFile, Module> contentRootOf = ContainerUtil.newHashMap();
    @NotNull final MultiMap<VirtualFile, Module> sourceRootOf = MultiMap.createSet();
    @NotNull final TObjectIntHashMap<VirtualFile> rootTypeId = new TObjectIntHashMap<>();
    @NotNull final MultiMap<VirtualFile, Library> excludedFromLibraries = MultiMap.createSmart();
    @NotNull final MultiMap<VirtualFile, Library> classOfLibraries = MultiMap.createSmart();
    @NotNull final MultiMap<VirtualFile, Library> sourceOfLibraries = MultiMap.createSmart();
    @NotNull final Set<VirtualFile> excludedFromProject = ContainerUtil.newHashSet();
    @NotNull final Map<VirtualFile, Module> excludedFromModule = ContainerUtil.newHashMap();
    @NotNull final Map<VirtualFile, String> packagePrefix = ContainerUtil.newHashMap();

    @NotNull
    Set<VirtualFile> getAllRoots() {
      LinkedHashSet<VirtualFile> result = ContainerUtil.newLinkedHashSet();
      result.addAll(classAndSourceRoots);
      result.addAll(contentRootOf.keySet());
      result.addAll(excludedFromLibraries.keySet());
      result.addAll(excludedFromModule.keySet());
      result.addAll(excludedFromProject);
      return result;
    }

    @Nullable
    private VirtualFile findModuleRootInfo(@NotNull List<VirtualFile> hierarchy) {
      for (VirtualFile root : hierarchy) {
        Module module = contentRootOf.get(root);
        Module excludedFrom = excludedFromModule.get(root);
        if (module != null && excludedFrom != module) {
          return root;
        }
        if (excludedFrom != null || excludedFromProject.contains(root)) {
          return null;
        }
      }
      return null;
    }

    @Nullable
    private VirtualFile findNearestContentRootForExcluded(@NotNull List<VirtualFile> hierarchy) {
      for (VirtualFile root : hierarchy) {
        if (contentRootOf.containsKey(root)) {
          return root;
        }
      }
      return null;
    }

    @Nullable
    private VirtualFile findLibraryRootInfo(@NotNull List<VirtualFile> hierarchy, boolean source) {
      Set<Library> librariesToIgnore = ContainerUtil.newHashSet();
      for (VirtualFile root : hierarchy) {
        librariesToIgnore.addAll(excludedFromLibraries.get(root));
        if (source && libraryOrSdkSources.contains(root) &&
            (!sourceOfLibraries.containsKey(root) || !librariesToIgnore.containsAll(sourceOfLibraries.get(root)))) {
          return root;
        }
        else if (!source && libraryOrSdkClasses.contains(root) &&
                 (!classOfLibraries.containsKey(root) || !librariesToIgnore.containsAll(classOfLibraries.get(root)))) {
          return root;
        }
      }
      return null;
    }

    private String calcPackagePrefix(@NotNull VirtualFile root,
                                     @NotNull List<VirtualFile> hierarchy,
                                     VirtualFile moduleContentRoot,
                                     VirtualFile libraryClassRoot,
                                     VirtualFile librarySourceRoot) {
      VirtualFile packageRoot = findPackageRootInfo(hierarchy, moduleContentRoot, libraryClassRoot, librarySourceRoot);
      String prefix = packagePrefix.get(packageRoot);
      if (prefix != null && !root.equals(packageRoot)) {
        assert packageRoot != null;
        String relative = VfsUtilCore.getRelativePath(root, packageRoot, '.');
        prefix = StringUtil.isEmpty(prefix) ? relative : prefix + '.' + relative;
      }
      return prefix;
    }

    @Nullable
    private VirtualFile findPackageRootInfo(@NotNull List<VirtualFile> hierarchy,
                                            VirtualFile moduleContentRoot,
                                            VirtualFile libraryClassRoot,
                                            VirtualFile librarySourceRoot) {
      for (VirtualFile root : hierarchy) {
        if (moduleContentRoot != null &&
            sourceRootOf.get(root).contains(contentRootOf.get(moduleContentRoot)) &&
            librarySourceRoot == null) {
          return root;
        }
        if (root.equals(libraryClassRoot) || root.equals(librarySourceRoot)) {
          return root;
        }
        if (root.equals(moduleContentRoot) && !sourceRootOf.containsKey(root) && librarySourceRoot == null && libraryClassRoot == null) {
          return null;
        }
      }
      return null;
    }

    @NotNull
    private LinkedHashSet<OrderEntry> getLibraryOrderEntries(@NotNull List<VirtualFile> hierarchy,
                                                             @Nullable VirtualFile libraryClassRoot,
                                                             @Nullable VirtualFile librarySourceRoot,
                                                             @NotNull MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                                                             @NotNull MultiMap<VirtualFile, OrderEntry> libSourceRootEntries) {
      LinkedHashSet<OrderEntry> orderEntries = ContainerUtil.newLinkedHashSet();
      for (VirtualFile root : hierarchy) {
        if (root.equals(libraryClassRoot) && !sourceRootOf.containsKey(root)) {
          orderEntries.addAll(libClassRootEntries.get(root));
        }
        if (root.equals(librarySourceRoot) && libraryClassRoot == null) {
          orderEntries.addAll(libSourceRootEntries.get(root));
        }
        if (libClassRootEntries.containsKey(root) || sourceRootOf.containsKey(root) && librarySourceRoot == null) {
          break;
        }
      }
      return orderEntries;
    }


    @Nullable
    private ModuleSourceOrderEntry getModuleSourceEntry(@NotNull List<VirtualFile> hierarchy,
                                                        @NotNull VirtualFile moduleContentRoot,
                                                        @NotNull MultiMap<VirtualFile, OrderEntry> libClassRootEntries) {
      Module module = contentRootOf.get(moduleContentRoot);
      for (VirtualFile root : hierarchy) {
        if (sourceRootOf.get(root).contains(module)) {
          return ContainerUtil.findInstance(ModuleRootManager.getInstance(module).getOrderEntries(), ModuleSourceOrderEntry.class);
        }
        if (libClassRootEntries.containsKey(root)) {
          return null;
        }
      }
      return null;
    }
  }

  @NotNull
  private static Pair<DirectoryInfo, String> calcDirectoryInfo(@NotNull final VirtualFile root,
                                                               @NotNull final List<VirtualFile> hierarchy,
                                                               @NotNull RootInfo info) {
    VirtualFile moduleContentRoot = info.findModuleRootInfo(hierarchy);
    VirtualFile libraryClassRoot = info.findLibraryRootInfo(hierarchy, false);
    VirtualFile librarySourceRoot = info.findLibraryRootInfo(hierarchy, true);
    boolean inProject = moduleContentRoot != null || libraryClassRoot != null || librarySourceRoot != null;
    VirtualFile nearestContentRoot;
    if (inProject) {
      nearestContentRoot = moduleContentRoot;
    }
    else {
      nearestContentRoot = info.findNearestContentRootForExcluded(hierarchy);
      if (nearestContentRoot == null) {
        return new Pair<>(NonProjectDirectoryInfo.EXCLUDED, null);
      }
    }

    VirtualFile sourceRoot = info.findPackageRootInfo(hierarchy, moduleContentRoot, null, librarySourceRoot);

    VirtualFile moduleSourceRoot = info.findPackageRootInfo(hierarchy, moduleContentRoot, null, null);
    boolean inModuleSources = moduleSourceRoot != null;
    boolean inLibrarySource = librarySourceRoot != null;
    int typeId = moduleSourceRoot != null ? info.rootTypeId.get(moduleSourceRoot) : 0;

    Module module = info.contentRootOf.get(nearestContentRoot);
    DirectoryInfo directoryInfo =
      new DirectoryInfoImpl(root, module, nearestContentRoot, sourceRoot, libraryClassRoot, inModuleSources, inLibrarySource, !inProject, typeId);

    String packagePrefix = info.calcPackagePrefix(root, hierarchy, moduleContentRoot, libraryClassRoot, librarySourceRoot);

    return Pair.create(directoryInfo, packagePrefix);
  }

  @NotNull
  public List<OrderEntry> getOrderEntries(@NotNull DirectoryInfo info) {
    if (!(info instanceof DirectoryInfoImpl)) return Collections.emptyList();
    return getOrderEntryGraph().getOrderEntries(((DirectoryInfoImpl)info).getRoot());
  }

  public interface InfoCache {
    @Nullable
    DirectoryInfo getCachedInfo(@NotNull VirtualFile dir);

    void cacheInfo(@NotNull VirtualFile dir, @NotNull DirectoryInfo info);
  }

  /**
   * An LRU cache with synchronization around the primary cache operations (get() and insertion
   * of a newly created value). Other map operations are not synchronized.
   */
  abstract static class SynchronizedSLRUCache<K, V> extends SLRUMap<K,V> {
    protected final Object myLock = new Object();

    protected SynchronizedSLRUCache(final int protectedQueueSize, final int probationalQueueSize) {
      super(protectedQueueSize, probationalQueueSize);
    }

    @NotNull
    public abstract V createValue(K key);

    @Override
    @NotNull
    public V get(K key) {
      V value;
      synchronized (myLock) {
        value = super.get(key);
        if (value != null) {
          return value;
        }
      }
      value = createValue(key);
      synchronized (myLock) {
        put(key, value);
      }
      return value;
    }
  }
}
