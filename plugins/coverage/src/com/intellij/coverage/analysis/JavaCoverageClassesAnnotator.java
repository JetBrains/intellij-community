// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.JavaCoverageEngineExtension;
import com.intellij.coverage.JavaCoverageSuite;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class JavaCoverageClassesAnnotator extends JavaCoverageClassesEnumerator {
  private static final Logger LOG = Logger.getInstance(JavaCoverageClassesAnnotator.class);

  private final CoverageInfoCollector myCollector;
  private final ProjectData myProjectData;
  private final Map<String, PackageAnnotator.AtomicPackageCoverageInfo> myFlattenPackages = new ConcurrentHashMap<>();
  private final Map<VirtualFile, PackageAnnotator.AtomicPackageCoverageInfo> myFlattenDirectories = new ConcurrentHashMap<>();
  private ExecutorService myExecutor;
  private int myThreadsCount;
  private final PackageAnnotator myPackageAnnotator;

  public JavaCoverageClassesAnnotator(@NotNull CoverageSuitesBundle suite,
                                      @NotNull Project project,
                                      @NotNull CoverageInfoCollector collector) {
    super(suite, project);
    myCollector = collector;
    myProjectData = mySuite.getCoverageData();
    myPackageAnnotator = new PackageAnnotator(suite, project, myProjectData);
  }

  @Override
  public void visitSuite() {
    if (myProjectData == null) return;
    myFlattenPackages.clear();
    var created = initExecutor();
    super.visitSuite();
    if (created) stopExecutor();
    collectPackageCoverage();
  }

  private void collectPackageCoverage() {
    Map<String, PackageAnnotator.PackageCoverageInfo> flattenPackages = new HashMap<>();
    for (var entry : myFlattenPackages.entrySet()) {
      String packageFQName = AnalysisUtils.internalNameToFqn(entry.getKey());
      var info = entry.getValue().toPackageCoverageInfo();
      flattenPackages.put(packageFQName, info);
    }
    myFlattenPackages.clear();

    annotatePackages(flattenPackages, myCollector);
  }

  /**
   * Collect coverage stats for all packages, based on flatten packages coverage
   * @param flattenPackages fqn to package coverage mapping
   */
  public static void annotatePackages(Map<String, PackageAnnotator.PackageCoverageInfo> flattenPackages, CoverageInfoCollector collector) {
    Map<String, PackageAnnotator.PackageCoverageInfo> packages = new HashMap<>();
    for (var entry : flattenPackages.entrySet()) {
      String packageFQName = entry.getKey();
      var info = entry.getValue();
      collector.addPackage(packageFQName, info, true);

      while (!packageFQName.isEmpty()) {
        packages.computeIfAbsent(packageFQName, k -> new PackageAnnotator.PackageCoverageInfo()).append(info);
        final int index = packageFQName.lastIndexOf('.');
        if (index < 0) break;
        packageFQName = packageFQName.substring(0, index);
      }
      packages.computeIfAbsent("", k -> new PackageAnnotator.PackageCoverageInfo()).append(info);
    }
    for (Map.Entry<String, PackageAnnotator.PackageCoverageInfo> entry : packages.entrySet()) {
      collector.addPackage(entry.getKey(), entry.getValue(), false);
    }
  }

  @Override
  protected void visitSource(Module module, String rootPackageVMName, List<RequestRoot> roots) {
    myFlattenDirectories.clear();
    if (module.isDisposed()) {
      LOG.warn("Module is already disposed: " + module);
      return;
    }
    super.visitSource(module, rootPackageVMName, roots);
    syncPoolThreads();
    collectDirectoryCoverage(module, rootPackageVMName);
  }

  private void collectDirectoryCoverage(Module module, String packageVMName) {
    Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> flattenDirectories = new HashMap<>();
    for (var entry : myFlattenDirectories.entrySet()) {
      flattenDirectories.put(entry.getKey(), entry.getValue().toPackageCoverageInfo());
    }
    myFlattenDirectories.clear();

    annotateDirectories(flattenDirectories, myCollector, getPackageRoots(module, packageVMName));
  }

  /**
   * Collect coverage stats for all directories, based on flatten directories coverage
   * @param sourceRoots Set of root directories, where the calculation should stop
   */
  public static void annotateDirectories(Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> flattenDirectories,
                                  CoverageInfoCollector collector,
                                  Set<VirtualFile> sourceRoots) {
    Map<VirtualFile, PackageAnnotator.DirCoverageInfo> directories = new HashMap<>();
    for (var entry : flattenDirectories.entrySet()) {
      var info = entry.getValue();
      VirtualFile dir = entry.getKey();
      while (dir != null) {
        directories.computeIfAbsent(dir, PackageAnnotator.DirCoverageInfo::new).append(info);
        if (sourceRoots.contains(dir)) break;
        dir = dir.getParent();
      }
    }

    for (PackageAnnotator.DirCoverageInfo dir : directories.values()) {
      collector.addSourceDirectory(dir.sourceRoot, dir);
    }
  }

  @Override
  protected void visitClassFiles(final String toplevelClassSrcFQName,
                                 final List<File> files,
                                 final String packageVMName) {

    if (isClassExcluded(toplevelClassSrcFQName)) return;
    var children = files.stream()
      .filter(Predicate.not(this::ignoreClass))
      .collect(Collectors.toMap(AnalysisUtils::getClassName, Function.identity()));
    if (children.isEmpty()) return;
    myExecutor.execute(() -> {
      PackageAnnotator.Result result = myPackageAnnotator.visitFiles(toplevelClassSrcFQName, children, packageVMName);
      if (result != null) {
        annotateClass(toplevelClassSrcFQName, result.info, packageVMName, result.directory);
      }
    });
  }

  private void annotateClass(String toplevelClassSrcFQName,
                             PackageAnnotator.ClassCoverageInfo info,
                             String packageVMName,
                             VirtualFile directory) {
    myCollector.addClass(toplevelClassSrcFQName, info);
    getOrCreateFlattenPackage(packageVMName).append(info);
    if (directory != null) {
      getOrCreateFlattenDirectory(directory).append(info);
    }
  }

  private void syncPoolThreads() {
    var barrier = new CyclicBarrier(myThreadsCount + 1);
    for (int i = 0; i < myThreadsCount; i++) {
      myExecutor.execute(() -> waitBarrier(barrier));
    }
    waitBarrier(barrier);
  }

  private static void waitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await();
    }
    catch (InterruptedException | BrokenBarrierException ignore) {
    }
  }

  private void stopExecutor() {
    myExecutor.shutdown();
    try {
      myExecutor.awaitTermination(1, TimeUnit.HOURS);
    }
    catch (InterruptedException ignored) {
    }
    finally {
      myExecutor = null;
    }
  }

  private boolean initExecutor() {
    if (myExecutor != null) return false;
    myThreadsCount = getWorkingThreads();
    myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Coverage Loading", myThreadsCount);
    return true;
  }

  private PackageAnnotator.AtomicPackageCoverageInfo getOrCreateFlattenPackage(@NotNull String packageName) {
    return myFlattenPackages.computeIfAbsent(packageName, k -> new PackageAnnotator.AtomicPackageCoverageInfo());
  }

  private PackageAnnotator.AtomicPackageCoverageInfo getOrCreateFlattenDirectory(@NotNull VirtualFile file) {
    return myFlattenDirectories.computeIfAbsent(file, k -> new PackageAnnotator.AtomicPackageCoverageInfo());
  }


  private boolean ignoreClass(File child) {
    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
      if (extension.ignoreCoverageForClass(mySuite, child)) {
        return true;
      }
    }
    return false;
  }

  private boolean isClassExcluded(String fqn) {
    return ContainerUtil.all(mySuite.getSuites(), suite -> suite instanceof JavaCoverageSuite javaSuite && !javaSuite.isClassFiltered(fqn));
  }

  private static Set<VirtualFile> getPackageRoots(Module module, String rootPackageVMName) {
    Set<VirtualFile> result = new HashSet<>();
    for (SourceFolder folder : getSourceFolders(module)) {
      final VirtualFile file = folder.getFile();
      if (file == null) continue;
      final String prefix = AnalysisUtils.fqnToInternalName(folder.getPackagePrefix());
      final VirtualFile relativeSrcRoot = file.findFileByRelativePath(StringUtil.trimStart(rootPackageVMName, prefix));
      if (relativeSrcRoot == null) continue;
      result.add(relativeSrcRoot);
    }
    return result;
  }

  private static Set<SourceFolder> getSourceFolders(Module module) {
    Set<SourceFolder> result = new HashSet<>();
    ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      for (SourceFolder folder : contentEntry.getSourceFolders()) {
        if (folder.getFile() == null) continue;
        result.add(folder);
      }
    }
    return result;
  }

  public static Set<VirtualFile> getSourceRoots(Module module) {
    return getSourceFolders(module).stream().map(SourceFolder::getFile).collect(Collectors.toSet());
  }

  private static int getWorkingThreads() {
    int threads = Registry.intValue("idea.coverage.loading.threads");
    final int maxThreads = Runtime.getRuntime().availableProcessors() - 1;
    if (threads == 0) {
      threads = maxThreads;
    }
    threads = Math.min(threads, maxThreads);
    threads = Math.max(threads, 1);
    return threads;
  }
}
