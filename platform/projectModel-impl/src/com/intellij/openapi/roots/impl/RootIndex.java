// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider.LibraryRoots;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.*;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.*;
import com.intellij.workspaceModel.ide.VirtualFileUrls;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
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

  private final Map<VirtualFile, String> myPackagePrefixByRoot;
  private final Map<VirtualFile, DirectoryInfo> myRootInfos;
  private final boolean myHasNonDirectoryRoots;
  private final ConcurrentBitSet myNonInterestingIds = ConcurrentBitSet.create();
  @NotNull private final Project myProject;
  private final RootFileSupplier myRootSupplier;
  final PackageDirectoryCacheImpl myPackageDirectoryCache;
  private volatile OrderEntryGraph myOrderEntryGraph;

  RootIndex(@NotNull Project project) {
    this(project, RootFileSupplier.INSTANCE);
  }

  RootIndex(@NotNull Project project,
            @NotNull RootFileSupplier rootSupplier) {
    myProject = project;
    myRootSupplier = rootSupplier;

    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (project.isDefault()) {
      LOG.error("Directory index may not be queried for default project");
    }
    ModuleManager manager = ModuleManager.getInstance(project);
    if (manager instanceof ModuleManagerEx) {
      LOG.assertTrue(((ModuleManagerEx)manager).areModulesLoaded(), "Directory index can only be queried after project initialization");
    }

    final RootInfo info = buildRootInfo(project);

    Set<VirtualFile> allRoots = info.getAllRoots();
    MultiMap<String, VirtualFile> rootsByPackagePrefix = MultiMap.create(allRoots.size(), 0.75f);
    myRootInfos = new HashMap<>(allRoots.size());
    myHasNonDirectoryRoots = ContainerUtil.exists(allRoots, r -> !r.isDirectory());
    myPackagePrefixByRoot = new HashMap<>(allRoots.size());
    List<List<VirtualFile>> hierarchies = new ArrayList<>(allRoots.size());
    for (VirtualFile root : allRoots) {
      List<VirtualFile> hierarchy = getHierarchy(root, allRoots, info);
      hierarchies.add(hierarchy);
      Pair<DirectoryInfo, String> pair = hierarchy != null
                                         ? calcDirectoryInfoAndPackagePrefix(root, hierarchy, info)
                                         : new Pair<>(NonProjectDirectoryInfo.IGNORED, null);
      myRootInfos.put(root, pair.first);
      String packagePrefix = pair.second;
      rootsByPackagePrefix.putValue(packagePrefix, root);
      myPackagePrefixByRoot.put(root, packagePrefix);
    }
    storeContentsBeneathExcluded(allRoots, hierarchies);
    storeOutsideProjectRootsButHasContentInside();

    myPackageDirectoryCache = new PackageDirectoryCacheImpl((packageName, result) -> {
      PackageDirectoryCacheImpl.addValidDirectories(rootsByPackagePrefix.get(packageName), result);
    }, this::isPackageDirectory);
  }

  private boolean isPackageDirectory(@NotNull VirtualFile dir, @NotNull String packageName) {
    return getInfoForFile(dir).isInProject(dir) && packageName.equals(getPackageName(dir));
  }

  private void storeOutsideProjectRootsButHasContentInside() {
    nextRoot:
    for (VirtualFile root : new ArrayList<>(myRootInfos.keySet())) {
      for (VirtualFile v = root.getParent(); v != null; v = v.getParent()) {
        DirectoryInfo info = myRootInfos.get(v);
        if (info == NonProjectDirectoryInfo.OUTSIDE_PROJECT_ROOTS_BUT_HAS_CONTENT_BENEATH) {
          break;
        }
        if (info != null) continue nextRoot;
      }
      // mark all [root.parent .. disk root] as OUTSIDE_PROJECT_ROOTS_BUT_HAS_CONTENT_BENEATH
      for (VirtualFile v = root.getParent(); v != null; v = v.getParent()) {
        DirectoryInfo info = myRootInfos.get(v);
        if (info == NonProjectDirectoryInfo.OUTSIDE_PROJECT_ROOTS_BUT_HAS_CONTENT_BENEATH) {
          break;
        }
        myRootInfos.put(v, NonProjectDirectoryInfo.OUTSIDE_PROJECT_ROOTS_BUT_HAS_CONTENT_BENEATH);
      }
    }
  }

  private void storeContentsBeneathExcluded(@NotNull Set<? extends VirtualFile> allRoots, @NotNull List<? extends List<VirtualFile>> hierarchies) {
    // exploit allRoots being LinkedHashSet
    int i = 0;
    for (VirtualFile root : allRoots) {
      List<VirtualFile> hierarchy = hierarchies.get(i++);
      if (hierarchy == null) continue;
      // calculate bits "hasContentBeneath" and "hasExcludedBeneath" for which we need all other DirectoryInfos built
      DirectoryInfo dirInfo = myRootInfos.get(root);
      assert dirInfo != null;
      boolean hasContent = !isExcluded(dirInfo) && dirInfo.getContentRoot() != null;
      if (hasContent) {
        // start with the strict parent and update parent excluded dir info
        VirtualFile parentRoot = hierarchy.size() >= 2 ? hierarchy.get(1) : null;
        if (parentRoot != null) {
          DirectoryInfo parentInfo = myRootInfos.get(parentRoot);
          if (isExcluded(parentInfo)) {
            addContentBeneathExcludedInfo(parentInfo, parentRoot, dirInfo);
          }
        }
      }
    }
  }

  private void addContentBeneathExcludedInfo(@NotNull DirectoryInfo parentExcludedInfo,
                                             @NotNull VirtualFile parentFile,
                                             @NotNull DirectoryInfo childInfo) {
    List<DirectoryInfoImpl> beneathInfo;
    if (parentExcludedInfo instanceof NonProjectDirectoryInfo.WithBeneathInfo) {
      beneathInfo = ((NonProjectDirectoryInfo.WithBeneathInfo)parentExcludedInfo).myContentInfosBeneath;
    }
    else if (parentExcludedInfo instanceof NonProjectDirectoryInfo) {
      NonProjectDirectoryInfo.WithBeneathInfo newInfo = new NonProjectDirectoryInfo.WithBeneathInfo((NonProjectDirectoryInfo)parentExcludedInfo);
      myRootInfos.put(parentFile, newInfo);
      beneathInfo = newInfo.myContentInfosBeneath;
    }
    else if (parentExcludedInfo instanceof DirectoryInfoImpl) {
      beneathInfo = ((DirectoryInfoImpl)parentExcludedInfo).myContentInfosBeneath;
    }
    else {
      throw new RuntimeException("unknown info: "+parentExcludedInfo);
    }
    beneathInfo.add((DirectoryInfoImpl)childInfo);
  }

  private static boolean isExcluded(@NotNull DirectoryInfo info) {
    return info instanceof DirectoryInfoImpl && info.isExcluded(((DirectoryInfoImpl)info).getRoot())
      || info instanceof NonProjectDirectoryInfo && ((NonProjectDirectoryInfo)info).isExcluded();
  }

  void onLowMemory() {
    myPackageDirectoryCache.onLowMemory();
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
        VirtualFile contentRoot = myRootSupplier.getContentRoot(contentEntry);//todo[lene] here!
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
          VirtualFile sourceFolderRoot = myRootSupplier.getSourceRoot(sourceFolder);
          if (sourceFolderRoot != null && ensureValid(sourceFolderRoot, sourceFolder)) {
            info.sourceFolders.put(sourceFolderRoot, sourceFolder);
            info.classAndSourceRoots.add(sourceFolderRoot);
            info.sourceRootOf.putValue(sourceFolderRoot, module);
            info.packagePrefix.put(sourceFolderRoot, sourceFolder.getPackagePrefix());
          }
        }
      }

      for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry entry) {
          VirtualFile[] sourceRoots = myRootSupplier.getLibraryRoots(entry, OrderRootType.SOURCES);
          VirtualFile[] classRoots = myRootSupplier.getLibraryRoots(entry, OrderRootType.CLASSES);

          fillIndexWithLibraryRoots(info, entry, sourceRoots, classRoots);

          if (orderEntry instanceof LibraryOrderEntry) {
            Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
            if (library != null) {
              for (VirtualFile root : myRootSupplier.getExcludedRoots((LibraryEx) library)) {
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
        fillIndexWithLibraryRoots(info, sdk, myRootSupplier.getSdkRoots(sdk, OrderRootType.SOURCES),
                                  myRootSupplier.getSdkRoots(sdk, OrderRootType.CLASSES));
      }
    }

    for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      Collection<SyntheticLibrary> libraries = provider.getAdditionalProjectLibraries(project);
      for (SyntheticLibrary library : libraries) {
        for (VirtualFile sourceRoot : library.getSourceRoots()) {
          sourceRoot = myRootSupplier.correctRoot(sourceRoot, library, provider);
          if (sourceRoot == null) continue;

          info.libraryOrSdkSources.add(sourceRoot);
          info.classAndSourceRoots.add(sourceRoot);
          if (library instanceof JavaSyntheticLibrary) {
            info.packagePrefix.put(sourceRoot, "");
          }
          info.sourceOfLibraries.putValue(sourceRoot, library);
        }
        for (VirtualFile classRoot : library.getBinaryRoots()) {
          classRoot = myRootSupplier.correctRoot(classRoot, library, provider);
          if (classRoot == null) continue;

          info.libraryOrSdkClasses.add(classRoot);
          info.classAndSourceRoots.add(classRoot);
          if (library instanceof JavaSyntheticLibrary) {
            info.packagePrefix.put(classRoot, "");
          }
          info.classOfLibraries.putValue(classRoot, library);
        }
        for (VirtualFile file : library.getExcludedRoots()) {
          file = myRootSupplier.correctRoot(file, library, provider);
          if (file == null) continue;

          info.excludedFromLibraries.putValue(file, library);
        }
      }
    }

    for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
      List<VirtualFile> files = ContainerUtil.mapNotNull(policy.getExcludeUrlsForProject(), myRootSupplier::findFileByUrl);
      info.excludedFromProject.addAll(ContainerUtil.filter(files, file -> RootFileSupplier.ensureValid(file, project, policy)));

      Function<Sdk, List<VirtualFile>> fun = policy.getExcludeSdkRootsStrategy();

      if (fun != null) {
        Set<Sdk> sdks = collectSdks();

        Set<VirtualFile> roots = collectSdkClasses(sdks);

        for (Sdk sdk: sdks) {
          for (VirtualFile file : fun.fun(sdk)) {
            if (!roots.contains(file)) {
              ContainerUtil.addIfNotNull(info.excludedFromSdkRoots, myRootSupplier.correctRoot(file, sdk, policy));
            }
          }
        }
      }
    }
    for (UnloadedModuleDescription description : moduleManager.getUnloadedModuleDescriptions()) {
      for (VirtualFile contentRoot : myRootSupplier.getUnloadedContentRoots(description)) {
        if (ensureValid(contentRoot, description)) {
          info.contentRootOfUnloaded.put(contentRoot, description.getName());
        }
      }
    }

    EntityStorage snapshot = WorkspaceModel.getInstance(project).getCurrentSnapshot();
    for (CustomEntityProjectModelInfoProvider<?> provider : CustomEntityProjectModelInfoProvider.EP.getExtensionList()) {
      handleCustomEntities(provider, info, snapshot);
    }

    return info;
  }

  private <T extends WorkspaceEntity> void handleCustomEntities(@NotNull CustomEntityProjectModelInfoProvider<T> provider,
                                                                @NotNull RootInfo info,
                                                                @NotNull EntityStorage snapshot) {
    Sequence<T> entities = snapshot.entities(provider.getEntityClass());

    for (CustomEntityProjectModelInfoProvider.CustomContentRoot<T> customContentRoot :
      SequencesKt.asIterable(provider.getContentRoots(entities, snapshot))) {
      VirtualFile root = myRootSupplier.correctRoot(customContentRoot.root, customContentRoot.generativeEntity, provider);
      if (root == null) {
        continue;
      }
      if (!info.contentRootOf.containsKey(root)) {
        Module module = ModuleEntityUtils.findModule(customContentRoot.parentModule, snapshot);
        if (module != null) {
          info.contentRootOf.put(root, module);
        }
      }
    }

    for (LibraryRoots<T> libraryRoots : SequencesKt.asIterable(provider.getLibraryRoots(entities, snapshot))) {
      T entity = libraryRoots.generativeEntity;
      for (VirtualFile root : libraryRoots.sources) {
        VirtualFile librarySource = myRootSupplier.correctRoot(root, entity, provider);
        if (librarySource == null) continue;

        info.libraryOrSdkSources.add(librarySource);
        info.classAndSourceRoots.add(librarySource);
        info.sourceOfLibraries.putValue(librarySource, entity);
        info.packagePrefix.put(librarySource, "");
      }

      for (VirtualFile root : libraryRoots.classes) {
        VirtualFile libraryClass = myRootSupplier.correctRoot(root, entity, provider);
        if (libraryClass == null) continue;

        info.libraryOrSdkClasses.add(libraryClass);
        info.classAndSourceRoots.add(libraryClass);
        info.classOfLibraries.putValue(libraryClass, entity);
        info.packagePrefix.put(libraryClass, "");
      }

      for (VirtualFile root : libraryRoots.excluded) {
        VirtualFile libraryExcluded = myRootSupplier.correctRoot(root, entity, provider);
        if (libraryExcluded == null) continue;

        info.excludedFromLibraries.putValue(libraryExcluded, entity);
      }

      SyntheticLibrary.ExcludeFileCondition excludeFileCondition = libraryRoots.excludeFileCondition;
      if (excludeFileCondition != null) {
        Set<VirtualFile> allRoots = ContainerUtil.union(libraryRoots.sources, libraryRoots.classes);
        info.customEntitiesExcludeConditions.put(entity, excludeFileCondition.transformToCondition(allRoots));
      }
    }
    for (CustomEntityProjectModelInfoProvider.@NotNull ExcludeStrategy<T> excludeStrategy :
      SequencesKt.asIterable(provider.getExcludeSdkRootStrategies(entities, snapshot))) {
      T entity = excludeStrategy.generativeEntity;
      List<VirtualFile> files = ContainerUtil.mapNotNull(excludeStrategy.excludeUrls, VirtualFileUrls::getVirtualFile);
      info.excludedFromProject.addAll(ContainerUtil.filter(files, file -> RootFileSupplier.ensureValid(file, entity, provider)));

      java.util.function.@Nullable Function<Sdk, List<VirtualFile>> fun = excludeStrategy.excludeSdkRootsStrategy;

      if (fun != null) {
        Set<Sdk> sdks = collectSdks();
        Set<VirtualFile> roots = collectSdkClasses(sdks);

        for (Sdk sdk : sdks) {
          for (VirtualFile file : fun.apply(sdk)) {
            if (!roots.contains(file)) {
              ContainerUtil.addIfNotNull(info.excludedFromSdkRoots, myRootSupplier.correctRoot(file, sdk, provider));
            }
          }
        }
      }
    }
  }

  @NotNull
  private static Set<VirtualFile> collectSdkClasses(Set<? extends Sdk> sdks) {
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

  private static void fillIndexWithLibraryRoots(RootInfo info, Object container, VirtualFile[] sourceRoots, VirtualFile[] classRoots) {
    // Init library sources
    for (final VirtualFile sourceRoot : sourceRoots) {
      if (!ensureValid(sourceRoot, container)) continue;

      info.classAndSourceRoots.add(sourceRoot);
      info.libraryOrSdkSources.add(sourceRoot);
      info.packagePrefix.put(sourceRoot, "");
    }

    // init library classes
    for (final VirtualFile classRoot : classRoots) {
      if (!ensureValid(classRoot, container)) continue;

      info.classAndSourceRoots.add(classRoot);
      info.libraryOrSdkClasses.add(classRoot);
      info.packagePrefix.put(classRoot, "");
    }
  }

  private static boolean ensureValid(@NotNull VirtualFile file, @NotNull Object container) {
    return RootFileSupplier.ensureValid(file, container, null);
  }

  @NotNull
  private OrderEntryGraph getOrderEntryGraph() {
    if (myOrderEntryGraph == null) {
      RootInfo rootInfo = buildRootInfo(myProject);
      Couple<MultiMap<VirtualFile, OrderEntry>> pair = initLibraryClassSourceRoots();
      myOrderEntryGraph = new OrderEntryGraph(myProject, rootInfo, pair.first, pair.second);
    }
    return myOrderEntryGraph;
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
    private final MultiMap<VirtualFile, Node> myRoots; // Map of roots to their root nodes, eg. library jar -> library node
    private final SynchronizedSLRUCache<VirtualFile, List<OrderEntry>> myCache;
    private final SynchronizedSLRUCache<Module, Set<String>> myDependentUnloadedModulesCache;
    private final MultiMap<VirtualFile, OrderEntry> myLibClassRootEntries;
    private final MultiMap<VirtualFile, OrderEntry> myLibSourceRootEntries;

    OrderEntryGraph(@NotNull Project project, @NotNull RootInfo rootInfo,
                    MultiMap<VirtualFile, OrderEntry> libClassRootEntries, MultiMap<VirtualFile, OrderEntry> libSourceRootEntries) {
      myProject = project;
      myRootInfo = rootInfo;
      myAllRoots = myRootInfo.getAllRoots();
      int cacheSize = Math.max(25, myAllRoots.size() / 100 * 2);
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
  private Couple<MultiMap<VirtualFile, OrderEntry>> initLibraryClassSourceRoots() {
    MultiMap<VirtualFile, OrderEntry> libClassRootEntries = new MultiMap<>();
    MultiMap<VirtualFile, OrderEntry> libSourceRootEntries = new MultiMap<>();

    for (final Module module : ModuleManager.getInstance(myProject).getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry entry) {
          for (final VirtualFile sourceRoot : myRootSupplier.getLibraryRoots(entry, OrderRootType.SOURCES)) {
            libSourceRootEntries.putValue(sourceRoot, orderEntry);
          }
          for (final VirtualFile classRoot : myRootSupplier.getLibraryRoots(entry, OrderRootType.CLASSES)) {
            libClassRootEntries.putValue(classRoot, orderEntry);
          }
        }
      }
    }

    return Couple.of(libClassRootEntries, libSourceRootEntries);
  }

  @NotNull
  DirectoryInfo getInfoForFile(@NotNull VirtualFile file) {
    if (!file.isValid()) {
      return NonProjectDirectoryInfo.INVALID;
    }

    if (!file.isDirectory()) {
      DirectoryInfo info = getOwnFileInfo(file);
      if (info != null) return info;

      file = file.getParent();
    }

    if (file instanceof VirtualFileWithId) {
      for (VirtualFile each = file; each != null; each = each.getParent()) {
        int id = ((VirtualFileWithId)each).getId();
        if (!myNonInterestingIds.get(id)) {
          DirectoryInfo info = handleInterestingId(id, each);
          if (info != null) return info;
        }
      }
    }
    else {
      for (VirtualFile each = file; each != null; each = each.getParent()) {
        DirectoryInfo info = getOwnInfo(each);
        if (info != null) return info;
      }
    }

    return NonProjectDirectoryInfo.NOT_UNDER_PROJECT_ROOTS;
  }

  @Nullable
  private DirectoryInfo getOwnFileInfo(@NotNull VirtualFile file) {
    if (myHasNonDirectoryRoots) {
      return file instanceof VirtualFileWithId
             ? getOwnInfo(((VirtualFileWithId)file).getId(), file)
             : getOwnInfo(file);
    }
    return ourFileTypes.isFileIgnored(file) ? NonProjectDirectoryInfo.IGNORED : null;
  }

  @Nullable
  private DirectoryInfo getOwnInfo(int id, VirtualFile file) {
    return myNonInterestingIds.get(id) ? null : handleInterestingId(id, file);
  }

  @Nullable
  private DirectoryInfo getOwnInfo(@NotNull VirtualFile file) {
    DirectoryInfo info = myRootInfos.get(file);
    if (info != null) {
      return info;
    }

    if (ourFileTypes.isFileIgnored(file)) {
      return NonProjectDirectoryInfo.IGNORED;
    }

    return null;
  }

  @Nullable
  private DirectoryInfo handleInterestingId(int id, @NotNull VirtualFile file) {
    DirectoryInfo info = myRootInfos.get(file);
    if (info == null && ourFileTypes.isFileIgnored(file)) {
      info = NonProjectDirectoryInfo.IGNORED;
    }

    if (info == null) {
      if ((id > 500_000_000 || id < 0) && LOG.isDebugEnabled()) {
        LOG.error("Invalid id: " + id + " for " + file + " of " + file.getClass());
      }

      myNonInterestingIds.set(id);
    }
    return info;
  }

  @NotNull
  Query<VirtualFile> getDirectoriesByPackageName(@NotNull final String packageName, final boolean includeLibrarySources) {
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
  String getPackageName(@NotNull final VirtualFile dir) {
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

  private static String getPackageNameForSubdir(@Nullable String parentPackageName, @NotNull String subdirName) {
    if (parentPackageName == null) return null;
    return parentPackageName.isEmpty() ? subdirName : parentPackageName + "." + subdirName;
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
    @NotNull private final Map<VirtualFile, SourceFolder> sourceFolders = new HashMap<>();
    @NotNull private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> excludedFromLibraries =
      MultiMap.createSet();
    @NotNull private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> classOfLibraries =
      MultiMap.createSet();
    @NotNull private final MultiMap<VirtualFile, /*Library|SyntheticLibrary|WorkspaceEntity*/ Object> sourceOfLibraries =
      MultiMap.createSet();
    @NotNull private final Map<WorkspaceEntity, Condition<VirtualFile>> customEntitiesExcludeConditions = new HashMap<>();
    @NotNull private final Set<VirtualFile> excludedFromProject = new HashSet<>();
    @NotNull private final Set<VirtualFile> excludedFromSdkRoots = new HashSet<>();
    @NotNull private final Map<VirtualFile, Module> excludedFromModule = new HashMap<>();
    @NotNull private final Map<VirtualFile, FileTypeAssocTable<Boolean>> excludeFromContentRootTables = new HashMap<>();
    @NotNull private final Map<VirtualFile, String> packagePrefix = new HashMap<>();

    @NotNull
    Set<VirtualFile> getAllRoots() {
      Set<VirtualFile> result = new LinkedHashSet<>();
      result.addAll(classAndSourceRoots);
      result.addAll(contentRootOf.keySet());
      result.addAll(contentRootOfUnloaded.keySet());
      result.addAll(excludedFromLibraries.keySet());
      result.addAll(excludedFromModule.keySet());
      result.addAll(excludedFromProject);
      result.addAll(excludedFromSdkRoots);
      return result;
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

    @Nullable
    private VirtualFile findNearestContentRootForExcluded(@NotNull List<? extends VirtualFile> hierarchy) {
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
          Condition<VirtualFile> exclusion = ((SyntheticLibrary)library).getUnitedExcludeCondition();
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

    private String calcPackagePrefix(@NotNull VirtualFile root, VirtualFile packageRoot) {
      String prefix = packagePrefix.get(packageRoot);
      if (prefix != null && !root.equals(packageRoot)) {
        assert packageRoot != null;
        String relative = VfsUtilCore.getRelativePath(root, packageRoot, '.');
        prefix = StringUtil.isEmpty(prefix) ? relative : prefix + '.' + relative;
      }
      return prefix;
    }

    @Nullable
    private VirtualFile findPackageRootInfo(@NotNull List<? extends VirtualFile> hierarchy,
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
  private static Pair<DirectoryInfo, String> calcDirectoryInfoAndPackagePrefix(@NotNull final VirtualFile root,
                                                                               @NotNull final List<? extends VirtualFile> hierarchy,
                                                                               @NotNull RootInfo info) {
    VirtualFile moduleContentRoot = info.findNearestContentRoot(hierarchy);
    Pair<VirtualFile, List<Condition<? super VirtualFile>>> librarySourceRootInfo = info.findLibraryRootInfo(hierarchy, true);
    VirtualFile librarySourceRoot = Pair.getFirst(librarySourceRootInfo);

    Pair<VirtualFile, List<Condition<? super VirtualFile>>> libraryClassRootInfo = info.findLibraryRootInfo(hierarchy, false);
    VirtualFile libraryClassRoot = Pair.getFirst(libraryClassRootInfo);

    boolean inProject = moduleContentRoot != null ||
                        (libraryClassRoot != null || librarySourceRoot != null) && !info.excludedFromSdkRoots.contains(root);

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
    VirtualFile moduleSourceRoot = librarySourceRoot == null ? sourceRoot :
                                   info.findPackageRootInfo(hierarchy, moduleContentRoot, null, null);
    boolean inModuleSources = moduleSourceRoot != null;
    boolean inLibrarySource = librarySourceRoot != null;
    SourceFolder sourceFolder = moduleSourceRoot != null ? info.sourceFolders.get(moduleSourceRoot) : null;

    Module module = info.contentRootOf.get(nearestContentRoot);
    String unloadedModuleName = info.contentRootOfUnloaded.get(nearestContentRoot);
    FileTypeAssocTable<Boolean> contentExcludePatterns =
      moduleContentRoot != null ? info.excludeFromContentRootTables.get(moduleContentRoot) : null;
    Condition<? super VirtualFile> libraryExclusionPredicate = getLibraryExclusionPredicate(Pair.getSecond(librarySourceRootInfo));

    DirectoryInfo directoryInfo = contentExcludePatterns != null || libraryExclusionPredicate != null
                                  ? new DirectoryInfoWithExcludePatterns(root, module, nearestContentRoot, sourceRoot, sourceFolder,
                                                                         libraryClassRoot, inModuleSources, inLibrarySource, !inProject,
                                                                         contentExcludePatterns, libraryExclusionPredicate, unloadedModuleName)
                                  : new DirectoryInfoImpl(root, module, nearestContentRoot, sourceRoot, sourceFolder,
                                                          libraryClassRoot, inModuleSources, inLibrarySource,
                                                          !inProject, unloadedModuleName);

    VirtualFile packageRoot = libraryClassRoot == null ? sourceRoot :
                              info.findPackageRootInfo(hierarchy, moduleContentRoot, libraryClassRoot, librarySourceRoot);
    String packagePrefix = info.calcPackagePrefix(root, packageRoot);

    return Pair.create(directoryInfo, packagePrefix);
  }

  @Nullable
  private static Condition<? super VirtualFile> getLibraryExclusionPredicate(@Nullable List<? extends Condition<? super VirtualFile>> exclusions) {
    if (exclusions != null) {
      Condition<VirtualFile> result = Conditions.alwaysFalse();
      for (Condition<? super VirtualFile> exclusion : exclusions) {
        result = Conditions.or(result, exclusion);
      }
      return result == Conditions.<VirtualFile>alwaysFalse() ? null : result;
    }
    return null;
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
