// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.module.impl.ModuleManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSet;
import com.intellij.openapi.vfs.VirtualFileSetFactory;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.*;
import kotlin.Lazy;
import kotlin.LazyKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.*;

class RootIndex {
  static final Comparator<OrderEntry> BY_OWNER_MODULE = (o1, o2) -> {
    String name1 = o1.getOwnerModule().getName();
    String name2 = o2.getOwnerModule().getName();
    return name1.compareTo(name2);
  };

  private static final Logger LOG = Logger.getInstance(RootIndex.class);
  private static final FileTypeRegistry ourFileTypes = FileTypeRegistry.getInstance();

  @NotNull private final Project myProject;
  private final Lazy<OrderEntryGraph> myOrderEntryGraphLazy = LazyKt.lazy(() -> calculateOrderEntryGraph());

  RootIndex(@NotNull Project project) {
    myProject = project;

    ThreadingAssertions.assertReadAccess();
    if (project.isDefault()) {
      LOG.error("Directory index may not be queried for default project");
    }
    ModuleManager manager = ModuleManager.getInstance(project);
    if (manager instanceof ModuleManagerEx) {
      LOG.assertTrue(((ModuleManagerEx)manager).areModulesLoaded(), "Directory index can only be queried after project initialization");
    }
  }

  @NotNull
  private RootInfo buildRootInfo(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Start building root info " + Thread.currentThread());
    }

    final RootInfo info = new RootInfo();
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    boolean includeProjectJdk = true;

    for (final Module module : moduleManager.getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

      for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
        for (VirtualFile excludeRoot : contentEntry.getExcludeFolderFiles()) {
          if (!ensureValid(excludeRoot, contentEntry)) continue;

          info.excludedFromModule.put(excludeRoot, module);
        }
        VirtualFile contentRoot = contentEntry.getFile();
        if (contentRoot != null && ensureValid(contentRoot, module)) {
          if (!info.contentRootOf.containsKey(contentRoot)) {
            info.contentRootOf.put(contentRoot, module);
          }
          List<String> patterns = contentEntry.getExcludePatterns();
          if (!patterns.isEmpty()) {
            FileTypeAssocTable<Boolean> table = new FileTypeAssocTable<>();
            for (String pattern : patterns) {
              table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), Boolean.TRUE);
            }
            info.excludeFromContentRootTables.put(contentRoot, table);
          }
        }

        // Init module sources
        for (final SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          VirtualFile sourceFolderRoot = sourceFolder.getFile();
          if (sourceFolderRoot != null && ensureValid(sourceFolderRoot, sourceFolder)) {
            info.classAndSourceRoots.add(sourceFolderRoot);
            info.sourceRootOf.putValue(sourceFolderRoot, module);
          }
        }
      }

      for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry entry) {
          VirtualFile[] sourceRoots = entry.getRootFiles(OrderRootType.SOURCES);
          VirtualFile[] classRoots = entry.getRootFiles(OrderRootType.CLASSES);

          fillIndexWithLibraryRoots(info, entry, sourceRoots, classRoots);

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
          else {
            includeProjectJdk = false;
          }
        }
      }
    }

    if (includeProjectJdk) {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null) {
        fillIndexWithLibraryRoots(info, sdk, sdk.getRootProvider().getFiles(OrderRootType.SOURCES),
                                  sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
      }
    }

    for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      Collection<SyntheticLibrary> libraries = provider.getAdditionalProjectLibraries(project);
      for (SyntheticLibrary library : libraries) {
        for (VirtualFile sourceRoot : library.getSourceRoots()) {
          sourceRoot = RootFileValidityChecker.correctRoot(sourceRoot, library, provider);
          if (sourceRoot == null) continue;

          info.libraryOrSdkSources.add(sourceRoot);
          info.classAndSourceRoots.add(sourceRoot);
          info.sourceOfLibraries.putValue(sourceRoot, library);
        }
        for (VirtualFile classRoot : library.getBinaryRoots()) {
          classRoot = RootFileValidityChecker.correctRoot(classRoot, library, provider);
          if (classRoot == null) continue;

          info.libraryOrSdkClasses.add(classRoot);
          info.classAndSourceRoots.add(classRoot);
          info.classOfLibraries.putValue(classRoot, library);
        }
        for (VirtualFile file : library.getExcludedRoots()) {
          file = RootFileValidityChecker.correctRoot(file, library, provider);
          if (file == null) continue;

          info.excludedFromLibraries.putValue(file, library);
        }
      }
    }

    for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
      List<VirtualFile> files = ContainerUtil.mapNotNull(policy.getExcludeUrlsForProject(),
                                                         url -> VirtualFileManager.getInstance().findFileByUrl(url));
      info.excludedFromProject.addAll(ContainerUtil.filter(files, file -> RootFileValidityChecker.ensureValid(file, project, policy)));

      Function<Sdk, List<VirtualFile>> fun = policy.getExcludeSdkRootsStrategy();

      if (fun != null) {
        Set<Sdk> sdks = collectSdks();

        Set<VirtualFile> roots = collectSdkClasses(sdks);

        for (Sdk sdk: sdks) {
          for (VirtualFile file : fun.fun(sdk)) {
            if (!roots.contains(file)) {
              ContainerUtil.addIfNotNull(info.excludedFromSdkRoots, RootFileValidityChecker.correctRoot(file, sdk, policy));
            }
          }
        }
      }
    }
    for (UnloadedModuleDescription description : moduleManager.getUnloadedModuleDescriptions()) {
      for (VirtualFilePointer contentRootPointer : description.getContentRoots()) {
        VirtualFile contentRoot = contentRootPointer.getFile();
        if (contentRoot != null && ensureValid(contentRoot, description)) {
          info.contentRootOfUnloaded.put(contentRoot, description.getName());
        }
      }
    }

    return info;
  }

  @NotNull
  private static Set<VirtualFile> collectSdkClasses(@NotNull Set<? extends Sdk> sdks) {
    Set<VirtualFile> roots = new HashSet<>();

    for (Sdk sdk : sdks) {
      roots.addAll(Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES)));
    }
    return roots;
  }

  @NotNull
  private Set<Sdk> collectSdks() {
    Set<Sdk> sdks = new HashSet<>();

    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      Sdk sdk = ModuleRootManager.getInstance(m).getSdk();
      if (sdk != null) {
        sdks.add(sdk);
      }
    }
    return sdks;
  }

  private static void fillIndexWithLibraryRoots(@NotNull RootInfo info,
                                                @NotNull Object container,
                                                VirtualFile @NotNull [] sourceRoots,
                                                VirtualFile @NotNull [] classRoots) {
    // Init library sources
    for (final VirtualFile sourceRoot : sourceRoots) {
      if (!ensureValid(sourceRoot, container)) continue;

      info.classAndSourceRoots.add(sourceRoot);
      info.libraryOrSdkSources.add(sourceRoot);
    }

    // init library classes
    for (final VirtualFile classRoot : classRoots) {
      if (!ensureValid(classRoot, container)) continue;

      info.classAndSourceRoots.add(classRoot);
      info.libraryOrSdkClasses.add(classRoot);
    }
  }

  private static boolean ensureValid(@NotNull VirtualFile file, @NotNull Object container) {
    return RootFileValidityChecker.ensureValid(file, container, null);
  }

  @NotNull
  private OrderEntryGraph getOrderEntryGraph() {
    return myOrderEntryGraphLazy.getValue();
  }

  @NotNull
  private OrderEntryGraph calculateOrderEntryGraph() {
    RootInfo rootInfo = buildRootInfo(myProject);
    Couple<@NotNull MultiMap<VirtualFile, OrderEntry>> pair = initLibraryClassSourceRoots();
    return new OrderEntryGraph(myProject, rootInfo, pair.first, pair.second);
  }

  /**
   * A reverse dependency graph of (library, jdk, module, module source) -> (module).
   * <p>
   * <p>Each edge carries with it the associated OrderEntry that caused the dependency.
   */
  private static class OrderEntryGraph {
    private static class Edge {
      private final Module myKey;
      @NotNull
      private final ModuleOrderEntry myOrderEntry; // Order entry from myKey -> the node containing the edge
      private final boolean myRecursive; // Whether this edge should be descended into during graph walk

      Edge(@NotNull Module key, @NotNull ModuleOrderEntry orderEntry, boolean recursive) {
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

      private Node(@NotNull Module key) {
        myKey = key;
      }

      @Override
      public String toString() {
        return myKey.toString();
      }
    }

    private static class Graph {
      private final Map<Module, Node> myNodes;

      Graph(int moduleCount) {
        myNodes = new HashMap<>(moduleCount);
      }
    }

    private final Project myProject;
    private final RootInfo myRootInfo;
    private final Set<VirtualFile> myAllRoots;
    private final Graph myGraph;
    private final MultiMap<VirtualFile, Node> myRoots; // Map of roots to their root nodes, e.g., library jar -> library node
    private final SynchronizedSLRUCache<VirtualFile, List<OrderEntry>> myCache;
    private final SynchronizedSLRUCache<Module, Set<String>> myDependentUnloadedModulesCache;
    private final MultiMap<VirtualFile, OrderEntry> myLibClassRootEntries;
    private final MultiMap<VirtualFile, OrderEntry> myLibSourceRootEntries;

    OrderEntryGraph(@NotNull Project project,
                    @NotNull RootInfo rootInfo,
                    @NotNull MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                    @NotNull MultiMap<VirtualFile, OrderEntry> libSourceRootEntries) {
      myProject = project;
      myRootInfo = rootInfo;
      myAllRoots = rootInfo.getAllRoots();
      int cacheSize = Math.max(100, myAllRoots.size() / 3);
      myCache = new SynchronizedSLRUCache<>(cacheSize, cacheSize) {
        @NotNull
        @Override
        public List<OrderEntry> createValue(@NotNull VirtualFile key) {
          return collectOrderEntries(key);
        }
      };
      int dependentUnloadedModulesCacheSize = ModuleManager.getInstance(project).getModules().length / 2;
      myDependentUnloadedModulesCache =
        new SynchronizedSLRUCache<>(dependentUnloadedModulesCacheSize, dependentUnloadedModulesCacheSize) {
          @NotNull
          @Override
          public Set<String> createValue(@NotNull Module key) {
            return collectDependentUnloadedModules(key);
          }
        };
      Pair<Graph, MultiMap<VirtualFile, Node>> pair = initGraphRoots();
      myGraph = pair.getFirst();
      myRoots = pair.getSecond();
      myLibClassRootEntries = libClassRootEntries;
      myLibSourceRootEntries = libSourceRootEntries;
    }

    @NotNull
    private Pair<Graph, MultiMap<VirtualFile, Node>> initGraphRoots() {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      Module[] modules = moduleManager.getModules();
      Graph graph = new Graph(modules.length);

      MultiMap<VirtualFile, Node> roots = new MultiMap<>();

      for (final Module module : modules) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        List<OrderEnumerationHandler> handlers = OrderEnumeratorBase.getCustomHandlers(module);
        for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
          if (orderEntry instanceof ModuleOrderEntry moduleOrderEntry) {
            final Module depModule = moduleOrderEntry.getModule();
            if (depModule != null) {
              Node node = graph.myNodes.get(depModule);
              OrderEnumerator en = OrderEnumerator.orderEntries(depModule).exportedOnly();
              if (node == null) {
                node = new Node(depModule);
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

      return Pair.create(graph, roots);
    }

    @NotNull
    private List<OrderEntry> getOrderEntries(@NotNull VirtualFile file) {
      return myCache.get(file);
    }

    /**
     * Traverses the graph from the given file, collecting all encountered order entries.
     */
    @NotNull
    @Unmodifiable
    private List<OrderEntry> collectOrderEntries(@NotNull VirtualFile file) {
      List<VirtualFile> roots = getHierarchy(file, myAllRoots, myRootInfo);
      if (roots == null) {
        return Collections.emptyList();
      }
      Stack<Node> stack = new Stack<>(roots.size());
      for (VirtualFile root : roots) {
        Collection<Node> nodes = myRoots.get(root);
        for (Node node : nodes) {
          stack.push(node);
        }
      }

      Set<Node> seen = new HashSet<>(stack.size());
      List<OrderEntry> result = new ArrayList<>(stack.size());
      while (!stack.isEmpty()) {
        Node node = stack.pop();
        if (!seen.add(node)) {
          continue;
        }

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

      Pair<VirtualFile, List<Condition<? super VirtualFile>>> libraryClassRootInfo = myRootInfo.findLibraryRootInfo(roots, false);
      Pair<VirtualFile, List<Condition<? super VirtualFile>>> librarySourceRootInfo = myRootInfo.findLibraryRootInfo(roots, true);
      result.addAll(myRootInfo.getLibraryOrderEntries(roots,
                                                      Pair.getFirst(libraryClassRootInfo),
                                                      Pair.getFirst(librarySourceRootInfo),
                                                      myLibClassRootEntries, myLibSourceRootEntries));

      VirtualFile moduleContentRoot = myRootInfo.findNearestContentRoot(roots);
      if (moduleContentRoot != null) {
        ContainerUtil.addIfNotNull(result, myRootInfo.getModuleSourceEntry(roots, moduleContentRoot, myLibClassRootEntries));
      }
      result.sort(BY_OWNER_MODULE);
      return List.copyOf(result);
    }

    @NotNull
    Set<String> getDependentUnloadedModules(@NotNull Module module) {
      return myDependentUnloadedModulesCache.get(module);
    }

    /**
     * @return names of unloaded modules which directly or transitively via exported dependencies depend on the specified module
     */
    @NotNull
    private Set<String> collectDependentUnloadedModules(@NotNull Module module) {
      Node start = myGraph.myNodes.get(module);
      if (start == null) return Collections.emptySet();
      Deque<Node> stack = new ArrayDeque<>();
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

  @NotNull
  private Couple<@NotNull MultiMap<VirtualFile, OrderEntry>> initLibraryClassSourceRoots() {
    MultiMap<VirtualFile, OrderEntry> libClassRootEntries = new MultiMap<>();
    MultiMap<VirtualFile, OrderEntry> libSourceRootEntries = new MultiMap<>();

    for (final Module module : ModuleManager.getInstance(myProject).getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry entry) {
          for (final VirtualFile sourceRoot : entry.getRootFiles(OrderRootType.SOURCES)) {
            libSourceRootEntries.putValue(sourceRoot, orderEntry);
          }
          for (final VirtualFile classRoot : entry.getRootFiles(OrderRootType.CLASSES)) {
            libClassRootEntries.putValue(classRoot, orderEntry);
          }
        }
      }
    }

    return Couple.of(libClassRootEntries, libSourceRootEntries);
  }

  /**
   * @return list of all super-directories which are marked as some kind of root, or {@code null} if {@code deepDir} is under the ignored folder (with no nested roots)
   */
  @Nullable("returns null only if dir is under ignored folder")
  private static List<VirtualFile> getHierarchy(@NotNull VirtualFile deepDir, @NotNull Set<? extends VirtualFile> allRoots, @NotNull RootInfo info) {
    List<VirtualFile> hierarchy = new ArrayList<>();
    boolean hasContentRoots = false;
    for (VirtualFile dir = deepDir; dir != null; dir = dir.getParent()) {
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
    @NotNull private final Set<VirtualFile> classAndSourceRoots = new LinkedHashSet<>();

    @NotNull private final Set<VirtualFile> libraryOrSdkSources = new HashSet<>();
    @NotNull private final Set<VirtualFile> libraryOrSdkClasses = new HashSet<>();
    @NotNull private final Map<VirtualFile, Module> contentRootOf = new HashMap<>();
    @NotNull private final Map<VirtualFile, String> contentRootOfUnloaded = new HashMap<>();
    @NotNull private final MultiMap<VirtualFile, Module> sourceRootOf = MultiMap.createSet();
    @NotNull private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> excludedFromLibraries = MultiMap.createSet();
    @NotNull private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> classOfLibraries = MultiMap.createSet();
    @NotNull private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> sourceOfLibraries = MultiMap.createSet();
    @NotNull private final Map<WorkspaceEntity, Condition<VirtualFile>> customEntitiesExcludeConditions = new HashMap<>();
    @NotNull private final Set<VirtualFile> excludedFromProject = new HashSet<>();
    @NotNull private final Set<VirtualFile> excludedFromSdkRoots = new HashSet<>();
    @NotNull private final Map<VirtualFile, Module> excludedFromModule = new HashMap<>();
    @NotNull private final Map<VirtualFile, FileTypeAssocTable<Boolean>> excludeFromContentRootTables = new HashMap<>();

    @NotNull
    private Set<VirtualFile> getAllRoots() {
      VirtualFileSet result = VirtualFileSetFactory.getInstance().createCompactVirtualFileSet();
      result.addAll(classAndSourceRoots);
      result.addAll(contentRootOf.keySet());
      result.addAll(contentRootOfUnloaded.keySet());
      result.addAll(excludedFromLibraries.keySet());
      result.addAll(excludedFromModule.keySet());
      result.addAll(excludedFromProject);
      result.addAll(excludedFromSdkRoots);
      return result.freezed();
    }

    /**
     * Returns nearest content root for a file by its parent directories hierarchy. If the file is excluded (i.e. located under an excluded
     * root and there are no source roots on the path to the excluded root) returns {@code null}.
     */
    @Nullable
    private VirtualFile findNearestContentRoot(@NotNull List<? extends VirtualFile> hierarchy) {
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
          if (sourceRootOwners == null) {
            return null;
          }
          underExcludedSourceRoot = true;
        }

        if (!underExcludedSourceRoot && sourceRootOf.containsKey(root)) {
          Collection<Module> modulesForSourceRoot = sourceRootOf.get(root);
          if (!modulesForSourceRoot.isEmpty()) {
            sourceRootOwners = sourceRootOwners == null ? modulesForSourceRoot : ContainerUtil.union(sourceRootOwners, modulesForSourceRoot);
          }
        }
      }
      return null;
    }

    private static boolean isExcludedByPattern(@NotNull VirtualFile contentRoot,
                                               @NotNull List<? extends VirtualFile> hierarchy,
                                               @NotNull FileTypeAssocTable<Boolean> table) {
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

    /**
     * @return root and set of libraries that provided it
     */
    @Nullable
    private Pair<VirtualFile, List<Condition<? super VirtualFile>>> findLibraryRootInfo(@NotNull List<? extends VirtualFile> hierarchy,
                                                                                        boolean source) {
      Set</*Library|SyntheticLibrary|WorkspaceEntity*/ Object> librariesToIgnore = createLibrarySet();
      for (VirtualFile root : hierarchy) {
        librariesToIgnore.addAll(excludedFromLibraries.get(root));
        if (source && libraryOrSdkSources.contains(root)) {
          List<Condition<? super VirtualFile>> found = findInLibraryProducers(root, sourceOfLibraries, librariesToIgnore,
                                                                              customEntitiesExcludeConditions);
          if (found != null) return Pair.create(root, found);
        }
        else if (!source && libraryOrSdkClasses.contains(root)) {
          List<Condition<? super VirtualFile>> found = findInLibraryProducers(root, classOfLibraries, librariesToIgnore,
                                                                              customEntitiesExcludeConditions);
          if (found != null) return Pair.create(root, found);
        }
      }
      return null;
    }

    @NotNull
    private static Set</*Library|SyntheticLibrary*/ Object> createLibrarySet() {
      return CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
        @Override
        public int hashCode(Object object) {
          // reduce complexity of hashCode calculation to speed it up
          return Objects.hashCode(object instanceof Library ? ((Library)object).getName() : object);
        }

        @Override
        public boolean equals(Object o1, Object o2) {
          return Objects.equals(o1, o2);
        }
      });
    }

    private static List<Condition<? super VirtualFile>> findInLibraryProducers(@NotNull VirtualFile root,
                                                                               @NotNull MultiMap<VirtualFile, Object> libraryRoots,
                                                                               @NotNull Set<Object> librariesToIgnore,
                                                                               @NotNull Map<WorkspaceEntity, Condition<VirtualFile>> customEntitiesExcludeConditions) {
      if (!libraryRoots.containsKey(root)) {
        return Collections.emptyList();
      }
      Collection<Object> producers = libraryRoots.get(root);
      Set</*Library|SyntheticLibrary|WorkspaceEntity*/ Object> libraries = new HashSet<>(producers.size());
      List<Condition<? super VirtualFile>> exclusions = new SmartList<>();
      for (Object library : producers) {
        if (librariesToIgnore.contains(library)) continue;
        if (library instanceof SyntheticLibrary) {
          Condition<? super VirtualFile> exclusion = ((SyntheticLibrary)library).getUnitedExcludeCondition();
          if (exclusion != null) {
            exclusions.add(exclusion);
            if (exclusion.value(root)) {
              continue;
            }
          }
        }
        else if (library instanceof WorkspaceEntity) {
          Condition<VirtualFile> condition = customEntitiesExcludeConditions.get(library);
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

    @NotNull
    private Set<OrderEntry> getLibraryOrderEntries(@NotNull List<? extends VirtualFile> hierarchy,
                                                   @Nullable VirtualFile libraryClassRoot,
                                                   @Nullable VirtualFile librarySourceRoot,
                                                   @NotNull MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                                                   @NotNull MultiMap<VirtualFile, OrderEntry> libSourceRootEntries) {
      Set<OrderEntry> orderEntries = new LinkedHashSet<>();
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
    private ModuleSourceOrderEntry getModuleSourceEntry(@NotNull List<? extends VirtualFile> hierarchy,
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
  List<OrderEntry> getOrderEntries(@NotNull VirtualFile root) {
    return getOrderEntryGraph().getOrderEntries(root);
  }

  @NotNull
  Set<String> getDependentUnloadedModules(@NotNull Module module) {
    return getOrderEntryGraph().getDependentUnloadedModules(module);
  }

  /**
   * An LRU cache with synchronization around the primary cache operations (get() and insertion
   * of a newly created value). Other map operations are not synchronized.
   */
  abstract static class SynchronizedSLRUCache<K, V> extends SLRUMap<K, V> {
    private final Object myLock = ObjectUtils.sentinel("Root index lock");

    SynchronizedSLRUCache(final int protectedQueueSize, final int probationalQueueSize) {
      super(protectedQueueSize, probationalQueueSize);
    }

    @NotNull
    public abstract V createValue(@NotNull K key);

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
