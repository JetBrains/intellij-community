// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class JavaCoverageClassesAnnotator extends JavaCoverageClassesEnumerator {
  private static final Logger LOG = Logger.getInstance(JavaCoverageClassesAnnotator.class);

  private final PackageAnnotator.Annotator myAnnotator;
  private final ProjectData myProjectData;
  private Map<String, PackageAnnotator.AtomicPackageCoverageInfo> myFlattenPackages;
  private Map<VirtualFile, PackageAnnotator.AtomicPackageCoverageInfo> myFlattenDirectories;
  private ExecutorService myExecutor;
  private final PackageAnnotator myPackageAnnotator;

  public JavaCoverageClassesAnnotator(@NotNull CoverageSuitesBundle suite,
                                      @NotNull Project project,
                                      @NotNull PackageAnnotator.Annotator annotator) {
    this(suite, project, annotator, 0);
  }

  public JavaCoverageClassesAnnotator(@NotNull CoverageSuitesBundle suite,
                                      @NotNull Project project,
                                      @NotNull PackageAnnotator.Annotator annotator,
                                      int totalRoots) {
    super(suite, project, totalRoots);
    myAnnotator = annotator;
    myProjectData = mySuite.getCoverageData();

    final JavaCoverageOptionsProvider optionsProvider = JavaCoverageOptionsProvider.getInstance(myProject);
    myPackageAnnotator = new PackageAnnotator(suite, project, myProjectData, this,
                                              optionsProvider.ignoreEmptyPrivateConstructors(),
                                              optionsProvider.ignoreImplicitConstructors());
  }

  @Override
  protected void setJavaSuite(JavaCoverageSuite suite) {
    final CoverageRunner runner = suite.getRunner();
    final JavaCoverageRunner javaRunner;
    if (runner instanceof JavaCoverageRunner) {
      javaRunner = (JavaCoverageRunner)runner;
    }
    else {
      javaRunner = null;
    }
    myPackageAnnotator.setRunner(javaRunner);
  }

  @Override
  public void visitRootPackage(PsiPackage psiPackage) {
    if (myProjectData == null) return;
    myFlattenPackages = new ConcurrentHashMap<>();

    super.visitRootPackage(psiPackage);

    final Map<String, PackageAnnotator.PackageCoverageInfo> packages = new HashMap<>();
    for (Map.Entry<String, PackageAnnotator.AtomicPackageCoverageInfo> entry : myFlattenPackages.entrySet()) {
      String packageFQName = entry.getKey().replace('/', '.');
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
    myFlattenPackages = null;
    for (Map.Entry<String, PackageAnnotator.PackageCoverageInfo> entry : packages.entrySet()) {
      myAnnotator.annotatePackage(entry.getKey(), entry.getValue());
    }
  }

  @Override
  protected void visitSource(final PsiPackage psiPackage,
                             final Module module,
                             final GlobalSearchScope scope,
                             final String rootPackageVMName,
                             final boolean isTestSource,
                             final Set<VirtualFile> seenRoots) {
    myFlattenDirectories = new ConcurrentHashMap<>();
    super.visitSource(psiPackage, module, scope, rootPackageVMName, isTestSource, seenRoots);

    if (module.isDisposed()) {
      LOG.warn("Module is already disposed: " + module);
      myFlattenDirectories = null;
      return;
    }

    final List<VirtualFile> sourceRoots = ContainerUtil.filter(prepareRoots(module, rootPackageVMName, isTestSource), Objects::nonNull);
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
    myFlattenDirectories = null;

    for (PackageAnnotator.DirCoverageInfo dir : directories.values()) {
      if (isTestSource) {
        myAnnotator.annotateTestDirectory(dir.sourceRoot, dir, module);
      }
      else {
        myAnnotator.annotateSourceDirectory(dir.sourceRoot, dir, module);
      }
    }
  }

  @Override
  protected void visitRoot(File packageOutputRoot,
                           String rootPackageVMName,
                           GlobalSearchScope scope) {
    myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Coverage Loading", getWorkingThreads());
    super.visitRoot(packageOutputRoot, rootPackageVMName, scope);
    myExecutor.shutdown();
    try {
      myExecutor.awaitTermination(1, TimeUnit.HOURS);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    finally {
      myExecutor = null;
    }
  }

  @Override
  protected void visitClassFiles(final String toplevelClassSrcFQName,
                                 final List<File> files,
                                 final String packageVMName,
                                 final GlobalSearchScope scope) {
    if (files.isEmpty()) return;
    for (File file : files) {
      if (ignoreClass(mySuite, file, toplevelClassSrcFQName)) return;
    }
    myExecutor.execute(() -> myPackageAnnotator.visitFiles(toplevelClassSrcFQName, files, packageVMName, scope));
  }

  @Override
  protected void visitClass(PsiClass psiClass) {
    final String qualifiedName = psiClass.getQualifiedName();
    final PackageAnnotator.ClassCoverageInfo info = myPackageAnnotator.visitClass(psiClass);
    if (info != null && info.totalClassCount > 0) {
      myAnnotator.annotateClass(qualifiedName, info);
    }
  }

  public void annotateClass(String toplevelClassSrcFQName,
                            PackageAnnotator.ClassCoverageInfo info,
                            String packageVMName,
                            VirtualFile directory) {
    myAnnotator.annotateClass(toplevelClassSrcFQName, info);
    getOrCreateFlattenPackage(packageVMName).append(info);
    if (directory != null) {
      getOrCreateFlattenDirectory(directory).append(info);
    }
  }

  private PackageAnnotator.AtomicPackageCoverageInfo getOrCreateFlattenPackage(@NotNull String packageName) {
    return myFlattenPackages.computeIfAbsent(packageName, k -> new PackageAnnotator.AtomicPackageCoverageInfo());
  }

  private PackageAnnotator.AtomicPackageCoverageInfo getOrCreateFlattenDirectory(@NotNull VirtualFile file) {
    return myFlattenDirectories.computeIfAbsent(file, k -> new PackageAnnotator.AtomicPackageCoverageInfo());
  }


  private static boolean ignoreClass(CoverageSuitesBundle bundle, File child, String toplevelClassSrcFQName) {
    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
      if (extension.ignoreCoverageForClass(bundle, child)) {
        return true;
      }
    }
    for (CoverageSuite suite : bundle.getSuites()) {
      if (suite instanceof JavaCoverageSuite &&
          ((JavaCoverageSuite)suite).isClassFiltered(toplevelClassSrcFQName, ((JavaCoverageSuite)suite).getExcludedClassNames())) {
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
        final String prefix = folder.getPackagePrefix().replace('.', '/');
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
