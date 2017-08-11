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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.CollectionQuery;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SLRUMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;
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
        return getInfoForFile(dir).isInProject(dir) && packageName.equals(getPackageName(dir));
      }
    };
  }

  public void onLowMemory() {
    myPackageDirectoryCache.onLowMemory();
  }

  @NotNull
  private RootInfo buildRootInfo(@NotNull Project project) {
    final RootInfo info = new RootInfo();
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (final Module module : moduleManager.getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

      for (final VirtualFile contentRoot : moduleRootManager.getContentRoots()) {
        if (!info.contentRootOf.containsKey(contentRoot) && ensureValid(contentRoot, module)) {
          info.contentRootOf.put(contentRoot, module);
        }
      }

      for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
        if (!(contentEntry instanceof ContentEntryImpl) || !((ContentEntryImpl)contentEntry).isDisposed()) {
          for (VirtualFile excludeRoot : contentEntry.getExcludeFolderFiles()) {
            if (!ensureValid(excludeRoot, contentEntry)) continue;

            info.excludedFromModule.put(excludeRoot, module);
          }
          List<String> patterns = contentEntry.getExcludePatterns();
          if (!patterns.isEmpty()) {
            FileTypeAssocTable<Boolean> table = new FileTypeAssocTable<>();
            for (String pattern : patterns) {
              table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), Boolean.TRUE);
            }
            info.excludeFromContentRootTables.put(contentEntry.getFile(), table);
          }
        }

        // Init module sources
        for (final SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          final VirtualFile sourceFolderRoot = sourceFolder.getFile();
          if (sourceFolderRoot != null && ensureValid(sourceFolderRoot, sourceFolder)) {
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
            if (!ensureValid(sourceRoot, entry)) continue;

            info.classAndSourceRoots.add(sourceRoot);
            info.libraryOrSdkSources.add(sourceRoot);
            info.packagePrefix.put(sourceRoot, "");
          }

          // init library classes
          for (final VirtualFile classRoot : classRoots) {
            if (!ensureValid(classRoot, entry)) continue;

            info.classAndSourceRoots.add(classRoot);
            info.libraryOrSdkClasses.add(classRoot);
            info.packagePrefix.put(classRoot, "");
          }

          if (orderEntry instanceof LibraryOrderEntry) {
            Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
            if (library != null) {
              for (VirtualFile root : ((LibraryEx)library).getExcludedRoots()) {
                if (!ensureValid(root, library)) continue;

                info.excludedFromLibraries.putValue(root, library);
              }
              for (VirtualFile root : sourceRoots) {
                if (!ensureValid(root, library)) continue;

                info.sourceOfLibraries.putValue(root, library);
              }
              for (VirtualFile root : classRoots) {
                if (!ensureValid(root, library)) continue;

                info.classOfLibraries.putValue(root, library);
              }
            }
          }
        }
      }
    }

    for (AdditionalLibraryRootsProvider provider : Extensions.getExtensions(AdditionalLibraryRootsProvider.EP_NAME)) {
      Collection<SyntheticLibrary> libraries = provider.getAdditionalProjectLibraries(project);
      for (SyntheticLibrary descriptor : libraries) {
        for (VirtualFile root : descriptor.getSourceRoots()) {
          if (!ensureValid(root, project)) continue;

          info.libraryOrSdkSources.add(root);
          info.classAndSourceRoots.add(root);
          info.sourceOfLibraries.putValue(root, descriptor);
        }
        for (VirtualFile file : descriptor.getExcludedRoots()) {
          if (!ensureValid(file, project)) continue;
          info.excludedFromLibraries.putValue(file, descriptor);
        }
      }
    }
    for (DirectoryIndexExcludePolicy policy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, project)) {
      info.excludedFromProject.addAll(ContainerUtil.filter(policy.getExcludeRootsForProject(), file -> ensureValid(file, policy)));
    }
    for (UnloadedModuleDescription description : moduleManager.getUnloadedModuleDescriptions()) {
      for (VirtualFilePointer pointer : description.getContentRoots()) {
        VirtualFile contentRoot = pointer.getFile();
        if (contentRoot != null) {
          info.contentRootOfUnloaded.put(contentRoot, description.getName());
        }
      }
    }
    return info;
  }

  private static boolean ensureValid(@NotNull VirtualFile file, @NotNull Object container) {
    if (!(file instanceof VirtualFileWithId)) {
      //skip roots from unsupported file systems (e.g. http)
      return false;
    }
    if (!file.isValid()) {
      LOG.error("Invalid root " + file + " in " + container);
      return false;
    }
    return true;
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
      Set<String> myUnloadedDependentModules;

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
    final SynchronizedSLRUCache<Module, Set<String>> myDependentUnloadedModulesCache;
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
      int dependentUnloadedModulesCacheSize = ModuleManager.getInstance(project).getModules().length / 2;
      myDependentUnloadedModulesCache = new SynchronizedSLRUCache<Module, Set<String>>(dependentUnloadedModulesCacheSize, dependentUnloadedModulesCacheSize) {
        @NotNull
        @Override
        public Set<String> createValue(Module key) {
          return collectDependentUnloadedModules(key);
        }
      };
      initGraph();
      initLibraryRoots();
    }

    private void initGraph() {
      Graph graph = new Graph();

      MultiMap<VirtualFile, Node> roots = MultiMap.createSmart();

      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      for (final Module module : moduleManager.getModules()) {
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
      for (UnloadedModuleDescription description : moduleManager.getUnloadedModuleDescriptions()) {
        for (String depName : description.getDependencyModuleNames()) {
          Module depModule = moduleManager.findModuleByName(depName);
          if (depModule != null) {
            Node node = graph.myNodes.get(depModule);
            if (node == null) {
              node = new Node();
              node.myKey = depModule;
              graph.myNodes.put(depModule, node);
            }
            if (node.myUnloadedDependentModules == null) {
              node.myUnloadedDependentModules = new LinkedHashSet<>();
            }
            node.myUnloadedDependentModules.add(description.getName());
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

      @Nullable Pair<VirtualFile, Collection<Object>> libraryClassRootInfo = myRootInfo.findLibraryRootInfo(roots, false);
      @Nullable Pair<VirtualFile, Collection<Object>> librarySourceRootInfo = myRootInfo.findLibraryRootInfo(roots, true);
      result.addAll(myRootInfo.getLibraryOrderEntries(roots,
                                                      libraryClassRootInfo != null ? libraryClassRootInfo.first : null,
                                                      librarySourceRootInfo != null ? librarySourceRootInfo.first : null,
                                                      myLibClassRootEntries, myLibSourceRootEntries));

      VirtualFile moduleContentRoot = myRootInfo.findNearestContentRoot(roots);
      if (moduleContentRoot != null) {
        ContainerUtil.addIfNotNull(result, myRootInfo.getModuleSourceEntry(roots, moduleContentRoot, myLibClassRootEntries));
      }
      Collections.sort(result, BY_OWNER_MODULE);
      return result;
    }

    public Set<String> getDependentUnloadedModules(@NotNull Module module) {
      return myDependentUnloadedModulesCache.get(module);
    }

    /**
     * @return names of unloaded modules which directly or transitively via exported dependencies depend on the specified module
     */
    private Set<String> collectDependentUnloadedModules(@NotNull Module module) {
      ArrayDeque<OrderEntryGraph.Node> stack = new ArrayDeque<>();
      Node start = myGraph.myNodes.get(module);
      if (start == null) return Collections.emptySet();
      stack.push(start);
      Set<Node> seen = new HashSet<>();
      Set<String> result = null;
      while (!stack.isEmpty()) {
        Node node = stack.pop();
        if (!seen.add(node)) {
          continue;
        }
        if (node.myUnloadedDependentModules != null) {
          if (result == null) {
            result = new LinkedHashSet<>(node.myUnloadedDependentModules);
          }
          else {
            result.addAll(node.myUnloadedDependentModules);
          }
        }
        for (Edge edge : node.myEdges) {
          if (edge.myRecursive) {
            Node targetNode = myGraph.myNodes.get(edge.myKey);
            if (targetNode != null) {
              stack.push(targetNode);
            }
          }
        }
      }
      return result != null ? result : Collections.emptySet();
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
        return info.isInProject(file) && (!info.isInLibrarySource(file) || info.isInModuleSource(file) || info.hasLibraryClassRoot());
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
    @NotNull final Map<VirtualFile, String> contentRootOfUnloaded = ContainerUtil.newHashMap();
    @NotNull final MultiMap<VirtualFile, Module> sourceRootOf = MultiMap.createSet();
    @NotNull final TObjectIntHashMap<VirtualFile> rootTypeId = new TObjectIntHashMap<>();
    @NotNull final MultiMap<VirtualFile, /*Library|SyntheticLibrary*/ Object> excludedFromLibraries = MultiMap.createSmart();
    @NotNull final MultiMap<VirtualFile, Library> classOfLibraries = MultiMap.createSmart();
    @NotNull final MultiMap<VirtualFile, /*Library|SyntheticLibrary*/ Object> sourceOfLibraries = MultiMap.createSmart();
    @NotNull final Set<VirtualFile> excludedFromProject = ContainerUtil.newHashSet();
    @NotNull final Map<VirtualFile, Module> excludedFromModule = ContainerUtil.newHashMap();
    @NotNull final Map<VirtualFile, FileTypeAssocTable<Boolean>> excludeFromContentRootTables = ContainerUtil.newHashMap();
    @NotNull final Map<VirtualFile, String> packagePrefix = ContainerUtil.newHashMap();

    @NotNull
    Set<VirtualFile> getAllRoots() {
      LinkedHashSet<VirtualFile> result = ContainerUtil.newLinkedHashSet();
      result.addAll(classAndSourceRoots);
      result.addAll(contentRootOf.keySet());
      result.addAll(contentRootOfUnloaded.keySet());
      result.addAll(excludedFromLibraries.keySet());
      result.addAll(excludedFromModule.keySet());
      result.addAll(excludedFromProject);
      return result;
    }

    /**
     * Returns nearest content root for a file by its parent directories hierarchy. If the file is excluded (i.e. located under an excluded
     * root and there are no source roots on the path to the excluded root) returns {@code null}.
     */
    @Nullable
    private VirtualFile findNearestContentRoot(@NotNull List<VirtualFile> hierarchy) {
      Collection<Module> sourceRootOwners = null;
      boolean underExcludedSourceRoot = false;
      for (VirtualFile root : hierarchy) {
        Module module = contentRootOf.get(root);
        Module excludedFrom = excludedFromModule.get(root);
        if (module != null) {
          FileTypeAssocTable<Boolean> table = excludeFromContentRootTables.get(root);
          if (table != null && isExcludedByPattern(root, hierarchy, table)) {
            excludedFrom = module;
          }
        }
        if (module != null && (excludedFrom != module || underExcludedSourceRoot && sourceRootOwners.contains(module))) {
          return root;
        }
        if (excludedFrom != null || excludedFromProject.contains(root) || contentRootOfUnloaded.containsKey(root)) {
          if (sourceRootOwners != null) {
            underExcludedSourceRoot = true;
          }
          else {
            return null;
          }
        }

        if (!underExcludedSourceRoot && sourceRootOf.containsKey(root)) {
          Collection<Module> modulesForSourceRoot = sourceRootOf.get(root);
          if (!modulesForSourceRoot.isEmpty()) {
            if (sourceRootOwners == null) {
              sourceRootOwners = modulesForSourceRoot;
            }
            else {
              sourceRootOwners = ContainerUtil.union(sourceRootOwners, modulesForSourceRoot);
            }
          }
        }
      }
      return null;
    }

    private static boolean isExcludedByPattern(VirtualFile contentRoot, List<VirtualFile> hierarchy, FileTypeAssocTable<Boolean> table) {
      for (VirtualFile file : hierarchy) {
        if (table.findAssociatedFileType(file.getNameSequence()) != null) {
          return true;
        }
        if (file.equals(contentRoot)) {
          break;
        }
      }
      return false;
    }

    @Nullable
    private VirtualFile findNearestContentRootForExcluded(@NotNull List<VirtualFile> hierarchy) {
      for (VirtualFile root : hierarchy) {
        if (contentRootOf.containsKey(root) || contentRootOfUnloaded.containsKey(root)) {
          return root;
        }
      }
      return null;
    }

    /**
     * @return root and set of libraries that provided it
     */
    @Nullable
    private Pair<VirtualFile, Collection<Object>> findLibraryRootInfo(@NotNull List<VirtualFile> hierarchy, boolean source) {
      Set<Object> librariesToIgnore = ContainerUtil.newHashSet();
      for (VirtualFile root : hierarchy) {
        librariesToIgnore.addAll(excludedFromLibraries.get(root));
        if (source && libraryOrSdkSources.contains(root)) {
          if (!sourceOfLibraries.containsKey(root)) {
            return Pair.create(root, Collections.emptySet());
          }
          Collection<Object> rootProducers = findLibrarySourceRootProducers(librariesToIgnore, root);
          if (!rootProducers.isEmpty()) {
            return Pair.create(root, rootProducers);
          }
        }
        else if (!source && libraryOrSdkClasses.contains(root)) {
          if (!classOfLibraries.containsKey(root)) {
            return Pair.create(root, Collections.emptySet());
          }
          Collection<Object> rootProducers = findLibraryClassRootProducers(librariesToIgnore, root);
          if (!rootProducers.isEmpty()) {
            return Pair.create(root, rootProducers);
          }
        }
      }
      return null;
    }

    @NotNull
    private Collection<Object> findLibraryClassRootProducers(Set<Object> librariesToIgnore, VirtualFile root) {
      Set<Object> libraries = ContainerUtil.newHashSet(classOfLibraries.get(root));
      libraries.removeAll(librariesToIgnore);
      return libraries;
    }

    @NotNull
    private Collection<Object> findLibrarySourceRootProducers(Set<Object> librariesToIgnore, VirtualFile root) {
      Set<Object> libraries = ContainerUtil.newHashSet();
      for (Object library : sourceOfLibraries.get(root)) {
        if (librariesToIgnore.contains(library)) continue;
        if (library instanceof SyntheticLibrary) {
          Condition<VirtualFile> exclusion = ((SyntheticLibrary)library).getExcludeFileCondition();
          if (exclusion != null && exclusion.value(root)) {
            continue;
          }
        }
        libraries.add(library);
      }
      return libraries;
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
    VirtualFile moduleContentRoot = info.findNearestContentRoot(hierarchy);
    Pair<VirtualFile, Collection<Object>> librarySourceRootInfo = info.findLibraryRootInfo(hierarchy, true);
    VirtualFile librarySourceRoot = librarySourceRootInfo != null ? librarySourceRootInfo.first : null;

    Pair<VirtualFile, Collection<Object>> libraryClassRootInfo = info.findLibraryRootInfo(hierarchy, false);
    VirtualFile libraryClassRoot = libraryClassRootInfo != null ? libraryClassRootInfo.first : null;

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
    String unloadedModuleName = info.contentRootOfUnloaded.get(nearestContentRoot);
    FileTypeAssocTable<Boolean> contentExcludePatterns = moduleContentRoot != null ? info.excludeFromContentRootTables.get(moduleContentRoot) : null;
    Condition<VirtualFile> libraryExclusionPredicate = getLibraryExclusionPredicate(librarySourceRootInfo);

    DirectoryInfo directoryInfo = contentExcludePatterns != null || libraryExclusionPredicate != null
                                  ? new DirectoryInfoWithExcludePatterns(root, module, nearestContentRoot, sourceRoot, libraryClassRoot,
                                                                         inModuleSources, inLibrarySource, !inProject, typeId,
                                                                         contentExcludePatterns, libraryExclusionPredicate, unloadedModuleName)
                                  : new DirectoryInfoImpl(root, module, nearestContentRoot, sourceRoot, libraryClassRoot, inModuleSources,
                                                          inLibrarySource, !inProject, typeId, unloadedModuleName);

    String packagePrefix = info.calcPackagePrefix(root, hierarchy, moduleContentRoot, libraryClassRoot, librarySourceRoot);

    return Pair.create(directoryInfo, packagePrefix);
  }

  @Nullable
  private static Condition<VirtualFile> getLibraryExclusionPredicate(@Nullable Pair<VirtualFile, Collection<Object>> libraryRootInfo) {
    Condition<VirtualFile> result = Conditions.alwaysFalse();
    if (libraryRootInfo != null) {
      for (Object library : libraryRootInfo.second) {
        Condition<VirtualFile> exclusionPredicate = library instanceof SyntheticLibrary ? ((SyntheticLibrary)library).getExcludeFileCondition() : null;
        if (exclusionPredicate == null) continue;
        result = Conditions.or(result, exclusionPredicate);
      }
    }
    return result != Condition.FALSE ? result : null;
  }

  @NotNull
  public List<OrderEntry> getOrderEntries(@NotNull DirectoryInfo info) {
    if (!(info instanceof DirectoryInfoImpl)) return Collections.emptyList();
    return getOrderEntryGraph().getOrderEntries(((DirectoryInfoImpl)info).getRoot());
  }

  @NotNull
  public Set<String> getDependentUnloadedModules(@NotNull Module module) {
    return getOrderEntryGraph().getDependentUnloadedModules(module);
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
