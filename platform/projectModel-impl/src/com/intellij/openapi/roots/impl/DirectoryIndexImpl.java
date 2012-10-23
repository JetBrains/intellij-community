/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.*;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Stack;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class DirectoryIndexImpl extends DirectoryIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.DirectoryIndexImpl");

  protected final Project myProject;
  protected final DirectoryIndexExcludePolicy[] myExcludePolicies;
  protected volatile IndexState myState;

  private boolean myInitialized = false;
  private boolean myDisposed = false;

  public DirectoryIndexImpl(Project project) {
    myProject = project;
    myExcludePolicies = Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, myProject);
    myState = new IndexState();
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myDisposed = true;
      }
    });
  }

  @Override
  @TestOnly
  public void checkConsistency() {
    doCheckConsistency(false);
    doCheckConsistency(true);
  }

  @TestOnly
  private void doCheckConsistency(boolean reverseAllSets) {
    assert myInitialized;
    assert !myDisposed;

    final IndexState oldState = myState;
    myState = myState.copy();

    myState.doInitialize(reverseAllSets);

    Set<VirtualFile> keySet = myState.myDirToInfoMap.keySet();
    assert keySet.size() == oldState.myDirToInfoMap.keySet().size();
    for (VirtualFile file : keySet) {
      DirectoryInfo info1 = myState.myDirToInfoMap.get(file);
      DirectoryInfo info2 = oldState.myDirToInfoMap.get(file);
      assert info1.equals(info2);
    }

    assert myState.myPackageNameToDirsMap.keySet().size() == oldState.myPackageNameToDirsMap.keySet().size();
    for (Map.Entry<String, List<VirtualFile>> entry : myState.myPackageNameToDirsMap.entrySet()) {
      String packageName = entry.getKey();
      List<VirtualFile> dirs = entry.getValue();
      List<VirtualFile> dirs1 = oldState.myPackageNameToDirsMap.get(packageName);

      HashSet<VirtualFile> set1 = new HashSet<VirtualFile>();
      set1.addAll(dirs);
      HashSet<VirtualFile> set2 = new HashSet<VirtualFile>();
      set2.addAll(dirs1);
      assert set1.equals(set2);
    }
  }

  @Override
  public boolean isInitialized() {
    return myInitialized;
  }

  public void initialize() {
    if (myInitialized) {
      LOG.error("Directory index is already initialized.");
      return;
    }

    if (myDisposed) {
      LOG.error("Directory index is already disposed for this project");
      return;
    }

    myInitialized = true;
    long l = System.currentTimeMillis();
    doInitialize();
    LOG.info("Directory index initialized in " + (System.currentTimeMillis() - l) + " ms, indexed " + myState.myDirToInfoMap.size() + " directories");
  }

  protected void doInitialize() {
    IndexState newState = new IndexState();
    newState.doInitialize(false);
    myState = newState;
  }

  private boolean isExcludeRootForModule(Module module, VirtualFile excludeRoot) {
    for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
      if (policy.isExcludeRootForModule(module, excludeRoot)) return true;
    }
    return false;
  }

  protected static ContentEntry[] getContentEntries(Module module) {
    return ModuleRootManager.getInstance(module).getContentEntries();
  }

  private static OrderEntry[] getOrderEntries(Module module) {
    return ModuleRootManager.getInstance(module).getOrderEntries();
  }

  private static boolean isIgnored(@NotNull VirtualFile f) {
    return FileTypeRegistry.getInstance().isFileIgnored(f);
  }

  @Override
  public DirectoryInfo getInfoForDirectory(VirtualFile dir) {
    checkAvailability();
    dispatchPendingEvents();

    return myState.myDirToInfoMap.get(dir);
  }

  @Override
  public boolean isProjectExcludeRoot(VirtualFile dir) {
    checkAvailability();
    return myState.myProjectExcludeRoots.contains(dir);
  }

  private final PackageSink mySink = new PackageSink();

  private static final Condition<VirtualFile> IS_VALID = new Condition<VirtualFile>() {
    @Override
    public boolean value(final VirtualFile virtualFile) {
      return virtualFile.isValid();
    }
  };

  private class PackageSink extends QueryFactory<VirtualFile, Pair<IndexState, List<VirtualFile>>> {
    private PackageSink() {
      registerExecutor(new QueryExecutor<VirtualFile, Pair<IndexState, List<VirtualFile>>>() {
        @Override
        public boolean execute(@NotNull final Pair<IndexState, List<VirtualFile>> stateAndDirs,
                               @NotNull final Processor<VirtualFile> consumer) {
          for (VirtualFile dir : stateAndDirs.second) {
            DirectoryInfo info = stateAndDirs.first.myDirToInfoMap.get(dir);
            assert info != null;

            if (!info.isInLibrarySource || info.isInModuleSource || info.libraryClassRoot != null) {
              if (!consumer.process(dir)) return false;
            }
          }
          return true;
        }
      });
    }

    public Query<VirtualFile> search(@NotNull String packageName, boolean includeLibrarySources) {
      checkAvailability();
      dispatchPendingEvents();

      IndexState state = myState;
      List<VirtualFile> allDirs = state.myPackageNameToDirsMap.get(packageName);
      if (allDirs == null) allDirs = Collections.emptyList();

      Query<VirtualFile> query = includeLibrarySources ? new CollectionQuery<VirtualFile>(allDirs)
                                                       : createQuery(Pair.create(state, allDirs));
      return new FilteredQuery<VirtualFile>(query, IS_VALID);
    }
  }

  @Override
  @NotNull
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return mySink.search(packageName, includeLibrarySources);
  }

  @Override
  public String getPackageName(VirtualFile dir) {
    checkAvailability();
    return myState.myDirToPackageName.get(dir);
  }

  protected void dispatchPendingEvents() {
  }

  private void checkAvailability() {
    if (!myInitialized) {
      LOG.error("Directory index is not initialized yet for " + myProject);
    }

    if (myDisposed) {
      LOG.error("Directory index is already disposed for " + myProject);
    }
  }

  @Nullable
  protected static String getPackageNameForSubdir(String parentPackageName, String subdirName) {
    if (parentPackageName == null) return null;
    return parentPackageName.isEmpty() ? subdirName : parentPackageName + "." + subdirName;
  }

  protected class IndexState {
    protected final THashMap<VirtualFile, Set<String>> myExcludeRootsMap = new THashMap<VirtualFile, Set<String>>();
    protected final Set<VirtualFile> myProjectExcludeRoots = new THashSet<VirtualFile>();
    protected final Map<VirtualFile, DirectoryInfo> myDirToInfoMap = new THashMap<VirtualFile, DirectoryInfo>();
    protected final THashMap<String, List<VirtualFile>> myPackageNameToDirsMap = new THashMap<String, List<VirtualFile>>();
    protected final Map<VirtualFile, String> myDirToPackageName = new THashMap<VirtualFile, String>();

    public IndexState() {
    }

    private DirectoryInfo getOrCreateDirInfo(VirtualFile dir) {
      DirectoryInfo info = myDirToInfoMap.get(dir);
      if (info == null) {
        info = new DirectoryInfo();
        myDirToInfoMap.put(dir, info);
      }
      return info;
    }

    void fillMapWithModuleContent(VirtualFile root, final Module module, final VirtualFile contentRoot, @Nullable final ProgressIndicator progress) {
      VfsUtilCore.visitChildrenRecursively(root, new DirectoryVisitor() {
        @Override
        protected DirectoryInfo updateInfo(VirtualFile file) {
          if (progress != null) {
            progress.checkCanceled();
          }
          if (isExcluded(contentRoot, file)) return null;
          if (isIgnored(file)) return null;

          DirectoryInfo info = getOrCreateDirInfo(file);

          if (info.module != null) { // module contents overlap
            DirectoryInfo parentInfo = myDirToInfoMap.get(file.getParent());
            if (parentInfo == null || !info.module.equals(parentInfo.module)) return null;
          }

          return info;
        }

        @Override
        protected void afterChildrenVisited(DirectoryInfo info) {
          info.module = module;
          info.contentRoot = contentRoot;
        }
      });
    }

    private abstract class DirectoryVisitor extends VirtualFileVisitor {
      private final Stack<DirectoryInfo> myDirectoryInfoStack = new Stack<DirectoryInfo>();

      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory()) return false;
        DirectoryInfo info = updateInfo(file);
        if (info != null) {
          myDirectoryInfoStack.push(info);
          return true;
        }
        return false;
      }

      @Override
      public void afterChildrenVisited(@NotNull VirtualFile file) {
        afterChildrenVisited(myDirectoryInfoStack.pop());
      }

      @Nullable
      protected abstract DirectoryInfo updateInfo(VirtualFile file);

      protected void afterChildrenVisited(DirectoryInfo info) {}
    }

    private boolean isExcluded(VirtualFile root, VirtualFile dir) {
      Set<String> excludes = myExcludeRootsMap.get(root);
      return excludes != null && excludes.contains(dir.getUrl());
    }

    private void initModuleContents(Module module, boolean reverseAllSets, ProgressIndicator progress) {
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.processing.module.content.progress", module.getName()));

      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] contentRoots = rootManager.getContentRoots();
      if (reverseAllSets) {
        contentRoots = ArrayUtil.reverseArray(contentRoots);
      }

      for (final VirtualFile contentRoot : contentRoots) {
        fillMapWithModuleContent(contentRoot, module, contentRoot, progress);
      }
    }

    private void initModuleSources(Module module, boolean reverseAllSets, ProgressIndicator progress) {
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.processing.module.sources.progress", module.getName()));

      ContentEntry[] contentEntries = getContentEntries(module);

      if (reverseAllSets) {
        contentEntries = ArrayUtil.reverseArray(contentEntries);
      }

      for (ContentEntry contentEntry : contentEntries) {
        SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        if (reverseAllSets) {
          sourceFolders = ArrayUtil.reverseArray(sourceFolders);
        }
        for (SourceFolder sourceFolder : sourceFolders) {
          VirtualFile dir = sourceFolder.getFile();
          if (dir != null) {
            fillMapWithModuleSource(dir, module, sourceFolder.getPackagePrefix(), dir, sourceFolder.isTestSource(), progress);
          }
        }
      }
    }

    protected void fillMapWithModuleSource(final VirtualFile dir,
                                           final Module module,
                                           final String packageName,
                                           final VirtualFile sourceRoot,
                                           final boolean isTestSource, @Nullable final ProgressIndicator progress) {
      VfsUtilCore.visitChildrenRecursively(dir, new DirectoryVisitor() {

        private final Stack<String> myPackages = new Stack<String>();

        @Override
        protected DirectoryInfo updateInfo(VirtualFile file) {
          if (progress != null) {
            progress.checkCanceled();
          }
          DirectoryInfo info = myDirToInfoMap.get(file);
          if (info == null) return null;
          if (!module.equals(info.module)) return null;

          if (info.isInModuleSource) { // module sources overlap
            String definedPackage = myDirToPackageName.get(file);
            if (definedPackage != null && definedPackage.isEmpty()) return null; // another source root starts here
          }

          info.isInModuleSource = true;
          info.isTestSource = isTestSource;
          info.sourceRoot = sourceRoot;

          String currentPackage;
          if (myPackages.isEmpty()) {
            currentPackage = packageName;
          }
          else {
            currentPackage = getPackageNameForSubdir(myPackages.peek(), file.getName());
          }
          myPackages.push(currentPackage);
          setPackageName(file, currentPackage);
          return info;
        }

        @Override
        protected void afterChildrenVisited(DirectoryInfo info) {
          super.afterChildrenVisited(info);
          myPackages.pop();
        }
      });
    }

    private void initLibrarySources(Module module, ProgressIndicator progress) {
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.processing.library.sources.progress", module.getName()));

      for (OrderEntry orderEntry : getOrderEntries(module)) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          VirtualFile[] sourceRoots = ((LibraryOrSdkOrderEntry)orderEntry).getRootFiles(OrderRootType.SOURCES);
          for (final VirtualFile sourceRoot : sourceRoots) {
            fillMapWithLibrarySources(sourceRoot, "", sourceRoot, progress);
          }
        }
      }
    }

    protected void fillMapWithLibrarySources(final VirtualFile dir,
                                             final String packageName,
                                             final VirtualFile sourceRoot,
                                             @Nullable final ProgressIndicator progress) {
      VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<String>() {
        { setValueForChildren(packageName); }

        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (progress != null) progress.checkCanceled();
          if (!file.isDirectory() && file != dir || isIgnored(file)) return false;

          DirectoryInfo info = getOrCreateDirInfo(file);

          if (info.isInLibrarySource) { // library sources overlap
            String definedPackage = myDirToPackageName.get(file);
            if (definedPackage != null && definedPackage.isEmpty()) return false; // another library source root starts here
          }

          info.isInLibrarySource = true;
          info.sourceRoot = sourceRoot;

          final String packageName = getCurrentValue();
          final String newPackageName = file == dir ? packageName : getPackageNameForSubdir(packageName, file.getName());
          setPackageName(file, newPackageName);
          setValueForChildren(newPackageName);

          return true;
        }
      });
    }

    private void initLibraryClasses(Module module, ProgressIndicator progress) {
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.processing.library.classes.progress", module.getName()));

      for (OrderEntry orderEntry : getOrderEntries(module)) {
        if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          VirtualFile[] classRoots = ((LibraryOrSdkOrderEntry)orderEntry).getRootFiles(OrderRootType.CLASSES);
          for (final VirtualFile classRoot : classRoots) {
            fillMapWithLibraryClasses(classRoot, "", classRoot, progress);
          }
        }
      }
    }

    protected void fillMapWithLibraryClasses(final VirtualFile dir,
                                             final String packageName,
                                             final VirtualFile classRoot,
                                             @Nullable final ProgressIndicator progress) {
      VfsUtilCore.visitChildrenRecursively(dir, new VirtualFileVisitor<String>() {
        { setValueForChildren(packageName); }

        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          if (progress != null) progress.checkCanceled();
          if (!file.isDirectory() && file != dir || isIgnored(file)) return false;

          DirectoryInfo info = getOrCreateDirInfo(file);

          if (info.libraryClassRoot != null) { // library classes overlap
            String definedPackage = myDirToPackageName.get(file);
            if (definedPackage != null && definedPackage.isEmpty()) return false; // another library root starts here
          }

          info.libraryClassRoot = classRoot;

          final String packageName = getCurrentValue();
          final String newPackageName = Comparing.equal(file, dir) ? packageName : getPackageNameForSubdir(packageName, file.getName());
          if (!info.isInModuleSource && !info.isInLibrarySource) {
            setPackageName(file, newPackageName);
          }
          setValueForChildren(newPackageName);

          return true;
        }
      });
    }


    private void initOrderEntries(Module module,
                                  MultiMap<VirtualFile, OrderEntry> depEntries,
                                  MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                                  MultiMap<VirtualFile, OrderEntry> libSourceRootEntries, ProgressIndicator progress) {

      for (OrderEntry orderEntry : getOrderEntries(module)) {
        if (orderEntry instanceof ModuleOrderEntry) {
          final Module depModule = ((ModuleOrderEntry)orderEntry).getModule();
          if (depModule != null) {
            VirtualFile[] importedClassRoots =
              OrderEnumerator.orderEntries(depModule).exportedOnly().recursively().classes().usingCache().getRoots();
            for (VirtualFile importedClassRoot : importedClassRoots) {
              depEntries.putValue(importedClassRoot, orderEntry);
            }
          }
          VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (VirtualFile sourceRoot : sourceRoots) {
            depEntries.putValue(sourceRoot, orderEntry);
          }
        }
        else if (orderEntry instanceof ModuleSourceOrderEntry) {
          List<OrderEntry> oneEntryList = Arrays.asList(orderEntry);
          Module entryModule = orderEntry.getOwnerModule();

          VirtualFile[] sourceRoots = ((ModuleSourceOrderEntry)orderEntry).getRootModel().getSourceRoots();
          for (VirtualFile sourceRoot : sourceRoots) {
            fillMapWithOrderEntries(sourceRoot, oneEntryList, entryModule, null, null, null, progress);
          }
        }
        else if (orderEntry instanceof LibraryOrSdkOrderEntry) {
          final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
          VirtualFile[] classRoots = entry.getRootFiles(OrderRootType.CLASSES);
          for (VirtualFile classRoot : classRoots) {
            libClassRootEntries.putValue(classRoot, orderEntry);
          }
          VirtualFile[] sourceRoots = entry.getRootFiles(OrderRootType.SOURCES);
          for (VirtualFile sourceRoot : sourceRoots) {
            libSourceRootEntries.putValue(sourceRoot, orderEntry);
          }
        }
      }
    }

    private void fillMapWithOrderEntries(MultiMap<VirtualFile, OrderEntry> depEntries,
                                         MultiMap<VirtualFile, OrderEntry> libClassRootEntries,
                                         MultiMap<VirtualFile, OrderEntry> libSourceRootEntries, ProgressIndicator progress) {
      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : depEntries.entrySet()) {
        final VirtualFile vRoot = mapEntry.getKey();
        final Collection<OrderEntry> entries = mapEntry.getValue();
        fillMapWithOrderEntries(vRoot, entries, null, null, null, null, progress);
      }

      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : libClassRootEntries.entrySet()) {
        final VirtualFile vRoot = mapEntry.getKey();
        final Collection<OrderEntry> entries = mapEntry.getValue();
        fillMapWithOrderEntries(vRoot, entries, null, vRoot, null, null, progress);
      }

      for (Map.Entry<VirtualFile, Collection<OrderEntry>> mapEntry : libSourceRootEntries.entrySet()) {
        final VirtualFile vRoot = mapEntry.getKey();
        final Collection<OrderEntry> entries = mapEntry.getValue();
        fillMapWithOrderEntries(vRoot, entries, null, null, vRoot, null, progress);
      }
    }

    protected void setPackageName(VirtualFile dir, @Nullable String newPackageName) {
      assert dir != null;

      String oldPackageName = myDirToPackageName.get(dir);
      if (oldPackageName != null) {
        List<VirtualFile> oldPackageDirs = myPackageNameToDirsMap.get(oldPackageName);
        final boolean removed = oldPackageDirs.remove(dir);
        assert removed;

        if (oldPackageDirs.isEmpty()) {
          myPackageNameToDirsMap.remove(oldPackageName);
        }
      }

      if (newPackageName != null) {
        List<VirtualFile> newPackageDirs = myPackageNameToDirsMap.get(newPackageName);
        if (newPackageDirs == null) {
          newPackageDirs = new SmartList<VirtualFile>();
          myPackageNameToDirsMap.put(newPackageName, newPackageDirs);
        }
        newPackageDirs.add(dir);

        myDirToPackageName.put(dir, newPackageName);
      }
      else {
        myDirToPackageName.remove(dir);
      }
    }

    protected void fillMapWithOrderEntries(final VirtualFile root,
                                           @NotNull final Collection<OrderEntry> orderEntries,
                                           @Nullable final Module module,
                                           @Nullable final VirtualFile libraryClassRoot,
                                           @Nullable final VirtualFile librarySourceRoot,
                                           @Nullable final DirectoryInfo parentInfo, @Nullable final ProgressIndicator progress) {
      VfsUtilCore.visitChildrenRecursively(root, new DirectoryVisitor() {
        private final Stack<List<OrderEntry>> myEntries = new Stack<List<OrderEntry>>();

        @Override
        protected DirectoryInfo updateInfo(VirtualFile dir) {
          if (progress != null) {
            progress.checkCanceled();
          }
          if (isIgnored(dir)) return null;

          DirectoryInfo info = myDirToInfoMap.get(dir); // do not create it here!
          if (info == null) return null;

          if (module != null) {
            if (info.module != module) return null;
            if (!info.isInModuleSource) return null;
          }
          else if (libraryClassRoot != null) {
            if (info.libraryClassRoot != libraryClassRoot) return null;
            if (info.isInModuleSource) return null;
          }
          else if (librarySourceRoot != null) {
            if (!info.isInLibrarySource) return null;
            if (info.sourceRoot != librarySourceRoot) return null;
            if (info.libraryClassRoot != null) return null;
          }

          List<OrderEntry> oldParentEntries = myEntries.isEmpty() ? null : myEntries.peek();
          final List<OrderEntry> oldEntries = info.getOrderEntries();
          myEntries.push(oldEntries);
          info.addOrderEntries(orderEntries, parentInfo, oldParentEntries);
          return info;
        }

        @Override
        protected void afterChildrenVisited(DirectoryInfo info) {
          myEntries.pop();
        }
      });
    }

    protected void doInitialize(boolean reverseAllSets/* for testing order independence*/) {
      ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
      if (progress == null) progress = new EmptyProgressIndicator();

      progress.pushState();

      progress.checkCanceled();
      progress.setText(ProjectBundle.message("project.index.scanning.files.progress"));

      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      if (reverseAllSets) modules = ArrayUtil.reverseArray(modules);

      initExcludedDirMap(modules, progress);

      for (Module module : modules) {
        initModuleContents(module, reverseAllSets, progress);
      }
      // Important! Because module's contents may overlap,
      // first modules should be marked and only after that sources markup
      // should be added. (src markup depends on module markup)
      for (Module module : modules) {
        initModuleSources(module, reverseAllSets, progress);
        initLibrarySources(module, progress);
        initLibraryClasses(module, progress);
      }

      progress.checkCanceled();
      progress.setText2("");

      MultiMap<VirtualFile, OrderEntry> depEntries = new MultiMap<VirtualFile, OrderEntry>();
      MultiMap<VirtualFile, OrderEntry> libClassRootEntries = new MultiMap<VirtualFile, OrderEntry>();
      MultiMap<VirtualFile, OrderEntry> libSourceRootEntries = new MultiMap<VirtualFile, OrderEntry>();
      for (Module module : modules) {
        initOrderEntries(module, depEntries, libClassRootEntries, libSourceRootEntries, progress);
      }
      fillMapWithOrderEntries(depEntries, libClassRootEntries, libSourceRootEntries, progress);

      killOrderEntryArrayDuplicates();
    }

    private void killOrderEntryArrayDuplicates() {
      Map<List<OrderEntry>, List<OrderEntry>> interner = new HashMap<List<OrderEntry>, List<OrderEntry>>();
      for (DirectoryInfo info : myDirToInfoMap.values()) {
        List<OrderEntry> entries = info.getOrderEntries();
        if (!entries.isEmpty()) {
          List<OrderEntry> interned = interner.get(entries);
          if (interned == null) {
            interner.put(entries, interned = entries);
          }
          info.setInternedOrderEntries(interned);
        }
      }
    }

    private void initExcludedDirMap(Module[] modules, ProgressIndicator progress) {
      progress.checkCanceled();
      progress.setText2(ProjectBundle.message("project.index.building.exclude.roots.progress"));

      // exclude roots should be merged to prevent including excluded dirs of an inner module into the outer
      // exclude root should exclude from its content root and all outer content roots

      for (Module module : modules) {
        for (ContentEntry contentEntry : getContentEntries(module)) {
          VirtualFile contentRoot = contentEntry.getFile();
          if (contentRoot == null) continue;

          ExcludeFolder[] excludeRoots = contentEntry.getExcludeFolders();
          for (ExcludeFolder excludeRoot : excludeRoots) {
            // Output paths should be excluded (if marked as such) regardless if they're under corresponding module's content root
            if (excludeRoot.getFile() != null) {
              if (!FileUtil.startsWith(contentRoot.getUrl(), excludeRoot.getUrl())) {
                if (isExcludeRootForModule(module, excludeRoot.getFile())) {
                  putForFileAndAllAncestors(myExcludeRootsMap, excludeRoot.getFile(), excludeRoot.getUrl());
                }
              }
            }

            putForFileAndAllAncestors(myExcludeRootsMap, contentRoot, excludeRoot.getUrl());
          }
        }
      }

      for (DirectoryIndexExcludePolicy policy : myExcludePolicies) {
        for (VirtualFile file : policy.getExcludeRootsForProject()) {
          putForFileAndAllAncestors(myExcludeRootsMap, file, file.getUrl());
          myProjectExcludeRoots.add(file);
        }
      }
    }

    private void putForFileAndAllAncestors(Map<VirtualFile, Set<String>> map, VirtualFile file, String value) {
      while (true) {
        Set<String> set = map.get(file);
        if (set == null) {
          set = new THashSet<String>();
          map.put(file, set);
        }
        set.add(value);

        file = file.getParent();
        if (file == null) break;
      }
    }

    public IndexState copy() {
      final IndexState copy = new IndexState();

      myExcludeRootsMap.forEachEntry(new TObjectObjectProcedure<VirtualFile, Set<String>>() {
        @Override
        public boolean execute(VirtualFile key, Set<String> value) {
          copy.myExcludeRootsMap.put(key, new THashSet<String>(value));
          return true;
        }
      });

      copy.myProjectExcludeRoots.addAll(myProjectExcludeRoots);
      copy.myDirToInfoMap.putAll(myDirToInfoMap);

      myPackageNameToDirsMap.forEachEntry(new TObjectObjectProcedure<String, List<VirtualFile>>() {
        @Override
        public boolean execute(String key, List<VirtualFile> value) {
          copy.myPackageNameToDirsMap.put(key, new SmartList<VirtualFile>(value));
          return true;
        }
      });

      copy.myDirToPackageName.putAll(myDirToPackageName);

      return copy;
    }
  }
}