// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSetFactory;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SLRUMap;
import com.intellij.workspaceModel.core.fileIndex.impl.FileTypeAssocTableUtil;
import kotlin.Lazy;
import kotlin.LazyKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ApiStatus.Internal
public final class RootIndex {
  static final Comparator<OrderEntry> BY_OWNER_MODULE = (o1, o2) -> {
    var name1 = o1.getOwnerModule().getName();
    var name2 = o2.getOwnerModule().getName();
    return name1.compareTo(name2);
  };

  private static final Logger LOG = Logger.getInstance(RootIndex.class);
  private static final FileTypeRegistry ourFileTypes = FileTypeRegistry.getInstance();

  private final Project myProject;
  private final Lazy<OrderEntryGraph> myOrderEntryGraphLazy;

  public RootIndex(@NotNull Project project, @Nullable OrderEntryGraph graph) {
    myProject = project;
    myOrderEntryGraphLazy = graph == null ? LazyKt.lazy(() -> calculateOrderEntryGraph()) : LazyKt.lazyOf(graph);
  }

  @SuppressWarnings("deprecation")
  RootIndex(@NotNull Project project) {
    myProject = project;

    ThreadingAssertions.assertReadAccess();
    if (project.isDefault()) {
      LOG.error("Directory index may not be queried for default project");
    }

    var workspaceModelTopics = project.getService(WorkspaceModelTopics.class);
    if (workspaceModelTopics != null) {
      LOG.assertTrue(workspaceModelTopics.getModulesAreLoaded(), "Directory index can only be queried after project initialization");
    }
    myOrderEntryGraphLazy = LazyKt.lazy(() -> calculateOrderEntryGraph());
  }

  private RootInfo buildRootInfo(Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Start building root info " + Thread.currentThread());
    }

    var info = new RootInfo();
    var moduleManager = ModuleManager.getInstance(project);
    var includeProjectJdk = true;

    for (var module : moduleManager.getModules()) {
      var moduleRootManager = ModuleRootManager.getInstance(module);

      for (var contentEntry : moduleRootManager.getContentEntries()) {
        for (var excludeRoot : contentEntry.getExcludeFolderFiles()) {
          if (!ensureValid(excludeRoot, contentEntry)) continue;

          info.excludedFromModule.put(excludeRoot, module);
        }
        var contentRoot = contentEntry.getFile();
        if (contentRoot != null && ensureValid(contentRoot, module)) {
          if (!info.contentRootOf.containsKey(contentRoot)) {
            info.contentRootOf.put(contentRoot, module);
          }
          var patterns = contentEntry.getExcludePatterns();
          if (!patterns.isEmpty()) {
            FileTypeAssocTable<Boolean> table = FileTypeAssocTableUtil.newScalableFileTypeAssocTable();
            for (var pattern : patterns) {
              table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), Boolean.TRUE);
            }
            info.excludeFromContentRootTables.put(contentRoot, table);
          }
        }

        // Init module sources
        for (var sourceFolder : contentEntry.getSourceFolders()) {
          var sourceFolderRoot = sourceFolder.getFile();
          if (sourceFolderRoot != null && ensureValid(sourceFolderRoot, sourceFolder)) {
            info.classAndSourceRoots.add(sourceFolderRoot);
            info.sourceRootOf.putValue(sourceFolderRoot, module);
          }
        }
      }

      for (var orderEntry : moduleRootManager.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry entry) {
          var sourceRoots = entry.getRootFiles(OrderRootType.SOURCES);
          var classRoots = entry.getRootFiles(OrderRootType.CLASSES);

          fillIndexWithLibraryRoots(info, entry, sourceRoots, classRoots);

          if (orderEntry instanceof LibraryOrderEntry) {
            var library = ((LibraryOrderEntry)orderEntry).getLibrary();
            if (library != null) {
              for (var root : ((LibraryEx)library).getExcludedRoots()) {
                if (ensureValid(root, library)) {
                  info.excludedFromLibraries.putValue(root, library);
                }
              }
              for (var root : sourceRoots) {
                if (ensureValid(root, library)) {
                  info.sourceOfLibraries.putValue(root, library);
                }
              }
              for (var root : classRoots) {
                if (ensureValid(root, library)) {
                  info.classOfLibraries.putValue(root, library);
                }
              }
            }
          }
          else {
            includeProjectJdk = false;
          }
        }
      }
    }

    if (includeProjectJdk) {
      var sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null) {
        fillIndexWithLibraryRoots(
          info, sdk, sdk.getRootProvider().getFiles(OrderRootType.SOURCES), sdk.getRootProvider().getFiles(OrderRootType.CLASSES)
        );
      }
    }

    for (var provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      var libraries = provider.getAdditionalProjectLibraries(project);
      for (var library : libraries) {
        for (var sourceRoot : library.getSourceRoots()) {
          sourceRoot = RootFileValidityChecker.correctRoot(sourceRoot, library, provider);
          if (sourceRoot != null) {
            info.libraryOrSdkSources.add(sourceRoot);
            info.classAndSourceRoots.add(sourceRoot);
            info.sourceOfLibraries.putValue(sourceRoot, library);
          }
        }
        for (var classRoot : library.getBinaryRoots()) {
          classRoot = RootFileValidityChecker.correctRoot(classRoot, library, provider);
          if (classRoot != null) {
            info.libraryOrSdkClasses.add(classRoot);
            info.classAndSourceRoots.add(classRoot);
            info.classOfLibraries.putValue(classRoot, library);
          }
        }
        for (var file : library.getExcludedRoots()) {
          file = RootFileValidityChecker.correctRoot(file, library, provider);
          if (file != null) {
            info.excludedFromLibraries.putValue(file, library);
          }
        }
      }
    }

    for (var policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
      var files = ContainerUtil.mapNotNull(policy.getExcludeUrlsForProject(), url -> VirtualFileManager.getInstance().findFileByUrl(url));
      info.excludedFromProject.addAll(ContainerUtil.filter(files, file -> RootFileValidityChecker.ensureValid(file, project, policy)));

      @SuppressWarnings("removal")
      var fun = policy.getExcludeSdkRootsStrategy();

      if (fun != null) {
        var sdks = collectSdks();

        var roots = collectSdkClasses(sdks);

        for (var sdk: sdks) {
          for (var file : fun.fun(sdk)) {
            if (!roots.contains(file)) {
              ContainerUtil.addIfNotNull(info.excludedFromSdkRoots, RootFileValidityChecker.correctRoot(file, sdk, policy));
            }
          }
        }
      }
    }
    for (var description : moduleManager.getUnloadedModuleDescriptions()) {
      for (var contentRootPointer : description.getContentRoots()) {
        var contentRoot = contentRootPointer.getFile();
        if (contentRoot != null && ensureValid(contentRoot, description)) {
          info.contentRootOfUnloaded.put(contentRoot, description.getName());
        }
      }
    }

    return info;
  }

  private static Set<VirtualFile> collectSdkClasses(Set<Sdk> sdks) {
    var roots = new HashSet<VirtualFile>();
    for (var sdk : sdks) {
      roots.addAll(Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES)));
    }
    return roots;
  }

  private Set<Sdk> collectSdks() {
    var sdks = new HashSet<Sdk>();
    for (var module : ModuleManager.getInstance(myProject).getModules()) {
      var sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null) {
        sdks.add(sdk);
      }
    }
    return sdks;
  }

  private static void fillIndexWithLibraryRoots(RootInfo info, Object container, VirtualFile[] sourceRoots, VirtualFile[] classRoots) {
    // Init library sources
    for (var sourceRoot : sourceRoots) {
      if (ensureValid(sourceRoot, container)) {
        info.classAndSourceRoots.add(sourceRoot);
        info.libraryOrSdkSources.add(sourceRoot);
      }
    }
    // init library classes
    for (var classRoot : classRoots) {
      if (ensureValid(classRoot, container)) {
        info.classAndSourceRoots.add(classRoot);
        info.libraryOrSdkClasses.add(classRoot);
      }
    }
  }

  private static boolean ensureValid(VirtualFile file, Object container) {
    return RootFileValidityChecker.ensureValid(file, container, null);
  }

  @ApiStatus.Internal
  public @NotNull OrderEntryGraph getOrderEntryGraph() {
    return myOrderEntryGraphLazy.getValue();
  }

  private OrderEntryGraph calculateOrderEntryGraph() {
    var rootInfo = buildRootInfo(myProject);
    var pair = initLibraryClassSourceRoots();
    return new OrderEntryGraph(myProject, rootInfo, pair.first, pair.second);
  }

  /// A reverse dependency graph of (library, jdk, module, module source) -> (module).
  ///
  /// Each edge carries with it the associated OrderEntry that caused the dependency.
  @ApiStatus.Internal
  public static final class OrderEntryGraph {
    private static class Edge {
      private final Module myKey;
      private final ModuleOrderEntry myOrderEntry; // Order entry from myKey -> the node containing the edge
      private final boolean myRecursive; // Whether this edge should be descended into during graph walk

      private Edge(Module key, ModuleOrderEntry orderEntry, boolean recursive) {
        myKey = key;
        myOrderEntry = orderEntry;
        myRecursive = recursive;
      }

      @Override
      public String toString() {
        return myOrderEntry.toString();
      }
    }

    private static final class Node {
      private final Module myKey;
      private final List<Edge> myEdges = new ArrayList<>();
      private Set<String> myUnloadedDependentModules;

      private Node(Module key) {
        myKey = key;
      }

      @Override
      public String toString() {
        return myKey.toString();
      }
    }

    private static class Graph {
      private final Map<Module, Node> myNodes;

      private Graph(int moduleCount) {
        myNodes = new HashMap<>(moduleCount);
      }
    }

    private final Project myProject;
    private final RootInfo myRootInfo;
    private final @Unmodifiable Set<VirtualFile> myAllRoots;
    private final Graph myGraph;
    private final MultiMap<VirtualFile, Node> myRoots; // Map of roots to their root nodes, e.g., library jar -> library node
    private final SynchronizedSLRUCache<VirtualFile, List<OrderEntry>> myCache;
    private final SynchronizedSLRUCache<Module, Set<String>> myDependentUnloadedModulesCache;
    private final MultiMap<VirtualFile, OrderEntry> myLibClassRootEntries;
    private final MultiMap<VirtualFile, OrderEntry> myLibSourceRootEntries;

    private OrderEntryGraph(
      Project project,
      RootInfo rootInfo,
      MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
      MultiMap<VirtualFile, OrderEntry> libSourceRootEntries
    ) {
      myProject = project;
      myRootInfo = rootInfo;
      myAllRoots = rootInfo.getAllRoots();
      var cacheSize = Math.max(100, myAllRoots.size() / 3);
      myCache = new SynchronizedSLRUCache<>(cacheSize, cacheSize) {
        @Override
        public @NotNull @Unmodifiable List<OrderEntry> createValue(@NotNull VirtualFile key) {
          return collectOrderEntries(key);
        }
      };
      var dependentUnloadedModulesCacheSize = ModuleManager.getInstance(project).getModules().length / 2;
      myDependentUnloadedModulesCache =
        new SynchronizedSLRUCache<>(dependentUnloadedModulesCacheSize, dependentUnloadedModulesCacheSize) {
          @Override
          public @NotNull Set<String> createValue(@NotNull Module key) {
            return collectDependentUnloadedModules(key);
          }
        };
      var pair = initGraphRoots();
      myGraph = pair.getFirst();
      myRoots = pair.getSecond();
      myLibClassRootEntries = libClassRootEntries;
      myLibSourceRootEntries = libSourceRootEntries;
    }

    private @NotNull Pair<Graph, MultiMap<VirtualFile, Node>> initGraphRoots() {
      var moduleManager = ModuleManager.getInstance(myProject);
      var modules = moduleManager.getModules();
      var graph = new Graph(modules.length);
      var roots = new MultiMap<VirtualFile, Node>();

      for (var module : modules) {
        var moduleRootManager = ModuleRootManager.getInstance(module);
        var handlers = OrderEnumeratorBase.getCustomHandlers(module);
        for (var orderEntry : moduleRootManager.getOrderEntries()) {
          if (orderEntry instanceof ModuleOrderEntry moduleOrderEntry) {
            var depModule = moduleOrderEntry.getModule();
            if (depModule != null) {
              var node = graph.myNodes.get(depModule);
              var en = OrderEnumerator.orderEntries(depModule).exportedOnly();
              if (node == null) {
                node = new Node(depModule);
                graph.myNodes.put(depModule, node);

                var importedClassRoots = en.classes().usingCache().getRoots();
                for (var importedClassRoot : importedClassRoots) {
                  roots.putValue(importedClassRoot, node);
                }

                var importedSourceRoots = en.sources().usingCache().getRoots();
                for (var sourceRoot : importedSourceRoots) {
                  roots.putValue(sourceRoot, node);
                }
              }
              var shouldRecurse = en.recursively().shouldRecurse(moduleOrderEntry, handlers);
              node.myEdges.add(new Edge(module, moduleOrderEntry, shouldRecurse));
            }
          }
        }
      }

      for (var description : moduleManager.getUnloadedModuleDescriptions()) {
        for (var depName : description.getDependencyModuleNames()) {
          var depModule = moduleManager.findModuleByName(depName);
          if (depModule != null) {
            var node = graph.myNodes.get(depModule);
            if (node == null) {
              node = new Node(depModule);
              graph.myNodes.put(depModule, node);
            }
            if (node.myUnloadedDependentModules == null) {
              node.myUnloadedDependentModules = new LinkedHashSet<>();
            }
            node.myUnloadedDependentModules.add(description.getName());
          }
        }
      }

      return new Pair<>(graph, roots);
    }

    private List<OrderEntry> getOrderEntries(VirtualFile file) {
      return myCache.get(file);
    }

    /// Traverses the graph from the given file, collecting all encountered order entries.
    private @Unmodifiable List<OrderEntry> collectOrderEntries(VirtualFile file) {
      var roots = getHierarchy(file, myAllRoots, myRootInfo);
      if (roots == null) {
        return List.of();
      }

      var stack = new ArrayDeque<Node>(roots.size());
      for (var root : roots) {
        var nodes = myRoots.get(root);
        for (var node : nodes) {
          stack.push(node);
        }
      }

      var seen = new HashSet<Node>(stack.size());
      var result = new ArrayList<OrderEntry>(stack.size());
      while (!stack.isEmpty()) {
        var node = stack.pop();
        if (!seen.add(node)) {
          continue;
        }

        for (var edge : node.myEdges) {
          result.add(edge.myOrderEntry);

          if (edge.myRecursive) {
            var targetNode = myGraph.myNodes.get(edge.myKey);
            if (targetNode != null) {
              stack.push(targetNode);
            }
          }
        }
      }

      var libraryClassRootInfo = myRootInfo.findLibraryRootInfo(roots, false);
      var librarySourceRootInfo = myRootInfo.findLibraryRootInfo(roots, true);
      result.addAll(myRootInfo.getLibraryOrderEntries(
        roots, Pair.getFirst(libraryClassRootInfo), Pair.getFirst(librarySourceRootInfo), myLibClassRootEntries, myLibSourceRootEntries)
      );

      var moduleContentRoot = myRootInfo.findNearestContentRoot(roots);
      if (moduleContentRoot != null) {
        ContainerUtil.addIfNotNull(result, myRootInfo.getModuleSourceEntry(roots, moduleContentRoot, myLibClassRootEntries));
      }
      result.sort(BY_OWNER_MODULE);
      return List.copyOf(result);
    }

    @NotNull Set<String> getDependentUnloadedModules(@NotNull Module module) {
      return myDependentUnloadedModulesCache.get(module);
    }

    /// @return names of unloaded modules that directly or transitively via exported dependencies depend on the specified module
    private @NotNull Set<String> collectDependentUnloadedModules(@NotNull Module module) {
      var start = myGraph.myNodes.get(module);
      if (start == null) return Set.of();
      var stack = new ArrayDeque<Node>();
      stack.push(start);
      var seen = new HashSet<Node>();
      var result = (Set<String>)null;
      while (!stack.isEmpty()) {
        var node = stack.pop();
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
        for (var edge : node.myEdges) {
          if (edge.myRecursive) {
            var targetNode = myGraph.myNodes.get(edge.myKey);
            if (targetNode != null) {
              stack.push(targetNode);
            }
          }
        }
      }
      return result != null ? result : Set.of();
    }
  }

  private Pair<MultiMap<VirtualFile, OrderEntry>, MultiMap<VirtualFile, OrderEntry>> initLibraryClassSourceRoots() {
    var libClassRootEntries = new MultiMap<VirtualFile, OrderEntry>();
    var libSourceRootEntries = new MultiMap<VirtualFile, OrderEntry>();

    for (var module : ModuleManager.getInstance(myProject).getModules()) {
      var moduleRootManager = ModuleRootManager.getInstance(module);
      for (var orderEntry : moduleRootManager.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry entry) {
          for (var sourceRoot : entry.getRootFiles(OrderRootType.SOURCES)) {
            libSourceRootEntries.putValue(sourceRoot, orderEntry);
          }
          for (var classRoot : entry.getRootFiles(OrderRootType.CLASSES)) {
            libClassRootEntries.putValue(classRoot, orderEntry);
          }
        }
      }
    }

    return new Pair<>(libClassRootEntries, libSourceRootEntries);
  }

  /// @return list of all super-directories that are marked as some kind of root, or `null` if `deepDir` is under the ignored folder (with no nested roots)
  private static @Nullable("returns null only if dir is under ignored folder") List<VirtualFile> getHierarchy(VirtualFile deepDir, @Unmodifiable Set<VirtualFile> allRoots, RootInfo info) {
    var hierarchy = new ArrayList<VirtualFile>();
    var hasContentRoots = false;
    for (var dir = deepDir; dir != null; dir = dir.getParent()) {
      hasContentRoots |= info.contentRootOf.get(dir) != null;
      if (!hasContentRoots && ourFileTypes.isFileIgnored(dir)) {
        return null;
      }
      if (allRoots.contains(dir)) {
        hierarchy.add(dir);
      }
    }
    return hierarchy;
  }

  private static class RootInfo {
    // getDirectoriesByPackageName used to be in this order, some clients might rely on that
    private final Set<VirtualFile> classAndSourceRoots = new LinkedHashSet<>();
    private final Set<VirtualFile> libraryOrSdkSources = new HashSet<>();
    private final Set<VirtualFile> libraryOrSdkClasses = new HashSet<>();
    private final Map<VirtualFile, Module> contentRootOf = new HashMap<>();
    private final Map<VirtualFile, String> contentRootOfUnloaded = new HashMap<>();
    private final MultiMap<VirtualFile, Module> sourceRootOf = MultiMap.createSet();
    private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> excludedFromLibraries = MultiMap.createSet();
    private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> classOfLibraries = MultiMap.createSet();
    private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> sourceOfLibraries = MultiMap.createSet();
    private final Map<WorkspaceEntity, Condition<VirtualFile>> customEntitiesExcludeConditions = new HashMap<>();
    private final Set<VirtualFile> excludedFromProject = new HashSet<>();
    private final Set<VirtualFile> excludedFromSdkRoots = new HashSet<>();
    private final Map<VirtualFile, Module> excludedFromModule = new HashMap<>();
    private final Map<VirtualFile, FileTypeAssocTable<Boolean>> excludeFromContentRootTables = new HashMap<>();

    private @Unmodifiable Set<VirtualFile> getAllRoots() {
      var result = VirtualFileSetFactory.getInstance().createCompactVirtualFileSet();
      result.addAll(classAndSourceRoots);
      result.addAll(contentRootOf.keySet());
      result.addAll(contentRootOfUnloaded.keySet());
      result.addAll(excludedFromLibraries.keySet());
      result.addAll(excludedFromModule.keySet());
      result.addAll(excludedFromProject);
      result.addAll(excludedFromSdkRoots);
      return result.freezed();
    }

    /// Returns the nearest content root for a file by its parent directories hierarchy. If the file is excluded
    /// (i.e., located under an excluded root and there are no source roots on the path to the excluded root) returns `null`.
    private @Nullable VirtualFile findNearestContentRoot(List<VirtualFile> hierarchy) {
      var sourceRootOwners = (Collection<Module>)null;
      var underExcludedSourceRoot = false;
      for (var root : hierarchy) {
        var module = contentRootOf.get(root);
        var excludedFrom = excludedFromModule.get(root);
        if (module != null) {
          var table = excludeFromContentRootTables.get(root);
          if (table != null && isExcludedByPattern(root, hierarchy, table)) {
            excludedFrom = module;
          }
        }
        if (module != null && (excludedFrom != module || underExcludedSourceRoot && sourceRootOwners.contains(module))) {
          return root;
        }
        if (excludedFrom != null || excludedFromProject.contains(root) || contentRootOfUnloaded.containsKey(root)) {
          if (sourceRootOwners == null) {
            return null;
          }
          underExcludedSourceRoot = true;
        }
        if (!underExcludedSourceRoot && sourceRootOf.containsKey(root)) {
          var modulesForSourceRoot = sourceRootOf.get(root);
          if (!modulesForSourceRoot.isEmpty()) {
            sourceRootOwners = sourceRootOwners == null ? modulesForSourceRoot : ContainerUtil.union(sourceRootOwners, modulesForSourceRoot);
          }
        }
      }
      return null;
    }

    private static boolean isExcludedByPattern(VirtualFile contentRoot, List<VirtualFile> hierarchy, FileTypeAssocTable<Boolean> table) {
      for (var file : hierarchy) {
        if (table.findAssociatedFileType(file.getNameSequence()) != null) {
          return true;
        }
        if (file.equals(contentRoot)) {
          break;
        }
      }
      return false;
    }

    /// @return root and set of libraries that provided it
    private @Nullable Pair<VirtualFile, List<Condition<VirtualFile>>> findLibraryRootInfo(List<VirtualFile> hierarchy, boolean source) {
      var librariesToIgnore = createLibrarySet();
      for (var root : hierarchy) {
        librariesToIgnore.addAll(excludedFromLibraries.get(root));
        if (source && libraryOrSdkSources.contains(root)) {
          var found = findInLibraryProducers(root, sourceOfLibraries, librariesToIgnore, customEntitiesExcludeConditions);
          if (found != null) return new Pair<>(root, found);
        }
        else if (!source && libraryOrSdkClasses.contains(root)) {
          var found = findInLibraryProducers(root, classOfLibraries, librariesToIgnore, customEntitiesExcludeConditions);
          if (found != null) return new Pair<>(root, found);
        }
      }
      return null;
    }

    private static Set</*Library|SyntheticLibrary*/ Object> createLibrarySet() {
      return CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
        @Override
        public int hashCode(Object object) {
          // reduce complexity of hashCode calculation to speed it up
          return Objects.hashCode(object instanceof Library lib ? lib.getName() : object);
        }

        @Override
        public boolean equals(Object o1, Object o2) {
          return Objects.equals(o1, o2);
        }
      });
    }

    private static List<Condition<VirtualFile>> findInLibraryProducers(
      VirtualFile root,
      MultiMap<VirtualFile, Object> libraryRoots,
      Set<Object> librariesToIgnore,
      Map<WorkspaceEntity, Condition<VirtualFile>> customEntitiesExcludeConditions
    ) {
      if (!libraryRoots.containsKey(root)) {
        return List.of();
      }

      var producers = libraryRoots.get(root);
      var libraries = new HashSet</*Library|SyntheticLibrary|WorkspaceEntity*/>(producers.size());
      var exclusions = new SmartList<Condition<VirtualFile>>();
      for (var library : producers) {
        if (librariesToIgnore.contains(library)) continue;
        if (library instanceof SyntheticLibrary sl) {
          var exclusion = sl.getUnitedExcludeCondition();
          if (exclusion != null) {
            @SuppressWarnings("unchecked") var condition = (Condition<VirtualFile>)exclusion;
            exclusions.add(condition);
            if (exclusion.value(root)) {
              continue;
            }
          }
        }
        else if (library instanceof WorkspaceEntity) {
          var condition = customEntitiesExcludeConditions.get(library);
          if (condition != null) {
            exclusions.add(condition);
            if (condition.value(root)) {
              continue;
            }
          }
        }
        libraries.add(library);
      }
      if (!libraries.isEmpty()) {
        return exclusions;
      }
      return null;
    }

    private Set<OrderEntry> getLibraryOrderEntries(
      List<VirtualFile> hierarchy,
      @Nullable VirtualFile libraryClassRoot,
      @Nullable VirtualFile librarySourceRoot,
      MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
      MultiMap<VirtualFile, OrderEntry> libSourceRootEntries
    ) {
      var orderEntries = new LinkedHashSet<OrderEntry>();
      for (var root : hierarchy) {
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


    private @Nullable ModuleSourceOrderEntry getModuleSourceEntry(
      List<VirtualFile> hierarchy,
      VirtualFile moduleContentRoot,
      MultiMap<VirtualFile, OrderEntry> libClassRootEntries
    ) {
      var module = contentRootOf.get(moduleContentRoot);
      for (var root : hierarchy) {
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

  @NotNull List<OrderEntry> getOrderEntries(@NotNull VirtualFile root) {
    return getOrderEntryGraph().getOrderEntries(root);
  }

  @NotNull Set<String> getDependentUnloadedModules(@NotNull Module module) {
    return getOrderEntryGraph().getDependentUnloadedModules(module);
  }

  /// An LRU cache with synchronization around the primary cache operations (get() and insertion of a newly created value).
  /// Other map operations are not synchronized.
  private abstract static class SynchronizedSLRUCache<K, V> extends SLRUMap<K, V> {
    private final Object myLock = ObjectUtils.sentinel("Root index lock");

    private SynchronizedSLRUCache(int protectedQueueSize, int probationalQueueSize) {
      super(protectedQueueSize, probationalQueueSize);
    }

    public abstract @NotNull V createValue(@NotNull K key);

    @Override
    public @NotNull V get(K key) {
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
