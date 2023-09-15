// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.JavaCoverageEngineExtension;
import com.intellij.coverage.JavaCoverageRunner;
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class JavaCoverageClassesAnnotator extends JavaCoverageClassesEnumerator {
  private static final Logger LOG = Logger.getInstance(JavaCoverageClassesAnnotator.class);

  private final Annotator myAnnotator;
  private final ProjectData myProjectData;
  private final Map<String, PackageAnnotator.AtomicPackageCoverageInfo> myFlattenPackages = new ConcurrentHashMap<>();
  private final Map<VirtualFile, PackageAnnotator.AtomicPackageCoverageInfo> myFlattenDirectories = new ConcurrentHashMap<>();
  private ExecutorService myExecutor;
  private int myThreadsCount;
  private final PackageAnnotator myPackageAnnotator;

  public JavaCoverageClassesAnnotator(@NotNull CoverageSuitesBundle suite,
                                      @NotNull Project project,
                                      @NotNull Annotator annotator) {
    this(suite, project, annotator, 0);
  }

  public JavaCoverageClassesAnnotator(@NotNull CoverageSuitesBundle suite,
                                      @NotNull Project project,
                                      @NotNull Annotator annotator,
                                      int totalRoots) {
    super(suite, project, totalRoots);
    myAnnotator = annotator;
    myProjectData = mySuite.getCoverageData();
    myPackageAnnotator = new PackageAnnotator(suite, project, myProjectData);
  }

  @Override
  public void visitSuite() {
    var created = initExecutor();
    super.visitSuite();
    if (created) stopExecutor();
  }

  @Override
  public void visitRootPackage(PsiPackage psiPackage, JavaCoverageSuite suite) {
    if (myProjectData == null) return;
    myFlattenPackages.clear();
    var created = initExecutor();
    super.visitRootPackage(psiPackage, suite);

    final Map<String, PackageAnnotator.PackageCoverageInfo> packages = new HashMap<>();
    for (Map.Entry<String, PackageAnnotator.AtomicPackageCoverageInfo> entry : myFlattenPackages.entrySet()) {
      String packageFQName = AnalysisUtils.internalNameToFqn(entry.getKey());
      final PackageAnnotator.PackageCoverageInfo info = entry.getValue().toPackageCoverageInfo();
      myAnnotator.annotatePackage(packageFQName, info, true);

      while (!packageFQName.isEmpty()) {
        packages.computeIfAbsent(packageFQName, k -> new PackageAnnotator.PackageCoverageInfo()).append(info);
        final int index = packageFQName.lastIndexOf('.');
        if (index < 0) break;
        packageFQName = packageFQName.substring(0, index);
      }
      packages.computeIfAbsent("", k -> new PackageAnnotator.PackageCoverageInfo()).append(info);
    }
    myFlattenPackages.clear();
    for (Map.Entry<String, PackageAnnotator.PackageCoverageInfo> entry : packages.entrySet()) {
      myAnnotator.annotatePackage(entry.getKey(), entry.getValue());
    }
    if (created) stopExecutor();
  }

  @Override
  protected void visitSource(final PsiPackage psiPackage,
                             final Module module,
                             final String rootPackageVMName,
                             final boolean isTestSource,
                             final Set<VirtualFile> seenRoots) {
    myFlattenDirectories.clear();
    if (module.isDisposed()) {
      LOG.warn("Module is already disposed: " + module);
      return;
    }
    super.visitSource(psiPackage, module, rootPackageVMName, isTestSource, seenRoots);
    final List<VirtualFile> sourceRoots = ContainerUtil.filter(prepareRoots(module, rootPackageVMName, isTestSource), Objects::nonNull);
    syncPoolThreads();
    final Map<VirtualFile, PackageAnnotator.DirCoverageInfo> directories = new HashMap<>();
    for (Map.Entry<VirtualFile, PackageAnnotator.AtomicPackageCoverageInfo> entry : myFlattenDirectories.entrySet()) {
      final PackageAnnotator.PackageCoverageInfo info = entry.getValue().toPackageCoverageInfo();
      VirtualFile dir = entry.getKey();
      while (dir != null) {
        directories.computeIfAbsent(dir, PackageAnnotator.DirCoverageInfo::new).append(info);
        if (sourceRoots.contains(dir)) break;
        dir = dir.getParent();
      }
    }
    myFlattenDirectories.clear();

    for (PackageAnnotator.DirCoverageInfo dir : directories.values()) {
      myAnnotator.annotateSourceDirectory(dir.sourceRoot, dir);
    }
  }

  @Override
  protected void visitClassFiles(final String toplevelClassSrcFQName,
                                 final List<File> files,
                                 final String packageVMName) {
    if (JavaCoverageSuite.isClassFiltered(toplevelClassSrcFQName, myCurrentSuite.getExcludedClassNames())) return;

    var runner = myCurrentSuite.getRunner();
    boolean processUnloaded = runner instanceof JavaCoverageRunner && ((JavaCoverageRunner)runner).shouldProcessUnloadedClasses();
    var children = new HashMap<String, File>();
    for (File file : files) {
      if (ignoreClass(file)) continue;
      children.put(AnalysisUtils.getClassName(file), processUnloaded ? file : null);
    }
    if (children.isEmpty()) return;
    myExecutor.execute(() -> {
      PackageAnnotator.Result result = myPackageAnnotator.visitFiles(toplevelClassSrcFQName, children, packageVMName);
      if (result != null) {
        annotateClass(toplevelClassSrcFQName, result.info, packageVMName, result.directory);
      }
    });
  }

  @Override
  protected void visitClass(PsiClass psiClass) {
    final String qualifiedName = psiClass.getQualifiedName();
    final PackageAnnotator.ClassCoverageInfo info = myPackageAnnotator.visitClass(psiClass);
    if (info != null && info.totalClassCount > 0) {
      myAnnotator.annotateClass(qualifiedName, info);
    }
  }

  private void annotateClass(String toplevelClassSrcFQName,
                            PackageAnnotator.ClassCoverageInfo info,
                            String packageVMName,
                            VirtualFile directory) {
    myAnnotator.annotateClass(toplevelClassSrcFQName, info);
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

  private static VirtualFile[] prepareRoots(Module module, String rootPackageVMName, boolean isTestHierarchy) {
    List<VirtualFile> result = new ArrayList<>();
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      for (SourceFolder folder : contentEntry
        .getSourceFolders(isTestHierarchy ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE)) {
        final VirtualFile file = folder.getFile();
        if (file == null) continue;
        final String prefix = AnalysisUtils.fqnToInternalName(folder.getPackagePrefix());
        final VirtualFile relativeSrcRoot = file.findFileByRelativePath(StringUtil.trimStart(rootPackageVMName, prefix));
        result.add(relativeSrcRoot);
      }
    }
    return result.toArray(VirtualFile.EMPTY_ARRAY);
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
