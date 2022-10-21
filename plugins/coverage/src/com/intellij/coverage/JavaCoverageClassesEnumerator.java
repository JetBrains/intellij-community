// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public abstract class JavaCoverageClassesEnumerator {
  protected final CoverageSuitesBundle mySuite;
  protected final Project myProject;
  protected final CoverageDataManager myCoverageManager;
  private final boolean[] myShouldVisitTestSource;
  private final int myRootsCount;
  private int myCurrentRootsCount;

  public JavaCoverageClassesEnumerator(@NotNull final CoverageSuitesBundle suite, @NotNull final Project project, final int totalRoots) {
    mySuite = suite;
    myProject = project;
    myCoverageManager = CoverageDataManager.getInstance(myProject);
    myShouldVisitTestSource = mySuite.isTrackTestFolders() ? new boolean[]{false, true} : new boolean[]{false};
    myRootsCount = totalRoots;
  }

  protected void visitClass(PsiClass psiClass) { }

  /**
   * Visit classes with the same top level name.
   */
  protected void visitClassFiles(String topLevelClassName, List<File> files, String packageVMName, GlobalSearchScope scope) { }

  protected void setJavaSuite(JavaCoverageSuite suite) { }

  public void visitSuite() {
    myCurrentRootsCount = 0;
    updateProgress();

    for (CoverageSuite coverageSuite : mySuite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;
      setJavaSuite(javaSuite);
      final List<PsiClass> classes = javaSuite.getCurrentSuiteClasses(myProject);
      final List<PsiPackage> packages = javaSuite.getCurrentSuitePackages(myProject);

      for (PsiPackage psiPackage : packages) {
        ProgressIndicatorProvider.checkCanceled();
        visitRootPackage(psiPackage);
      }
      for (final PsiClass psiClass : classes) {
        ProgressIndicatorProvider.checkCanceled();
        ApplicationManager.getApplication().runReadAction(() -> {
          final String packageName = ((PsiClassOwner)psiClass.getContainingFile()).getPackageName();
          final PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
          if (psiPackage == null) return;
          visitClass(psiClass);
        });
      }
    }
  }

  //get read lock myself when needed
  public void visitRootPackage(final PsiPackage psiPackage) {
    final ProjectData data = mySuite.getCoverageData();
    if (data == null) return;

    if (!isPackageFiltered(psiPackage)) return;

    final GlobalSearchScope scope = mySuite.getSearchScope(myProject);
    final Module[] modules = myCoverageManager.doInReadActionIfProjectOpen(() -> ModuleManager.getInstance(myProject).getModules());
    if (modules == null) return;

    final String qualifiedName = psiPackage.getQualifiedName();
    final String rootPackageVMName = qualifiedName.replace('.', '/');

    for (final Module module : modules) {
      if (!scope.isSearchInModuleContent(module)) continue;
      ProgressIndicatorProvider.checkCanceled();
      final Set<VirtualFile> seenRoots = new HashSet<>();
      for (boolean isTestSource : myShouldVisitTestSource) {
        visitSource(psiPackage, module, scope, rootPackageVMName, isTestSource, seenRoots);
      }
    }
  }

  protected void visitSource(final PsiPackage psiPackage,
                             final Module module,
                             final GlobalSearchScope scope,
                             final String rootPackageVMName,
                             final boolean isTestSource,
                             final Set<VirtualFile> seenRoots) {
    final VirtualFile[] roots = getRoots(myCoverageManager, module, isTestSource);

    for (VirtualFile output : roots) {
      if (!seenRoots.add(output)) continue;
      final File outputRoot = PackageAnnotator.findRelativeFile(rootPackageVMName, VfsUtilCore.virtualToIoFile(output));
      if (outputRoot.exists()) {
        visitRoot(outputRoot, rootPackageVMName, scope);
      }
    }
  }

  protected void visitRoot(final File packageOutputRoot, final String rootPackageVMName, final GlobalSearchScope scope) {
    final Stack<PackageData> stack = new Stack<>(new PackageData(rootPackageVMName, packageOutputRoot.listFiles()));
    while (!stack.isEmpty()) {
      ProgressIndicatorProvider.checkCanceled();
      final PackageData packageData = stack.pop();
      final String packageVMName = packageData.packageVMName;
      final File[] children = packageData.children;
      if (children == null) continue;
      final Map<String, List<File>> topLevelClasses = new HashMap<>();
      for (File child : children) {
        if (PackageAnnotator.isClassFile(child)) {
          final String childName = PackageAnnotator.getClassName(child);
          final String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
          final String toplevelClassSrcFQName = PackageAnnotator.getSourceToplevelFQName(classFqVMName);
          topLevelClasses.computeIfAbsent(toplevelClassSrcFQName, k -> new ArrayList<>()).add(child);
        }
        else if (child.isDirectory()) {
          final String childPackageVMName = getChildVMName(packageVMName, child);
          stack.push(new PackageData(childPackageVMName, child.listFiles()));
        }
      }
      for (Map.Entry<String, List<File>> entry : topLevelClasses.entrySet()) {
        visitClassFiles(entry.getKey(), entry.getValue(), packageVMName, scope);
      }
    }
    myCurrentRootsCount++;
    updateProgress();
  }

  private void updateProgress() {
    if (myRootsCount <= 1) return;
    final ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progressIndicator == null) return;
    progressIndicator.setIndeterminate(false);
    progressIndicator.setFraction(myCurrentRootsCount / (double)myRootsCount);
  }

  private static final class PackageData {
    private final String packageVMName;
    private final File[] children;

    private PackageData(String packageVMName, File[] children) {
      this.packageVMName = packageVMName;
      this.children = children;
    }
  }

  @NotNull
  protected static String getChildVMName(@NotNull String packageVMName, @NotNull File child) {
    final String childName = child.getName();
    return packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
  }


  private boolean isPackageFiltered(@NotNull PsiPackage psiPackage) {
    final String qualifiedName = psiPackage.getQualifiedName();
    for (CoverageSuite coverageSuite : mySuite.getSuites()) {
      if (coverageSuite instanceof JavaCoverageSuite && ((JavaCoverageSuite)coverageSuite).isPackageFiltered(qualifiedName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Collect output roots for the specified module.
   * @param includeTests if true, returns both production and test output roots; if false, returns only production roots
   */
  public static VirtualFile @NotNull [] getRoots(final CoverageDataManager manager, final Module module, final boolean includeTests) {
    final VirtualFile[] files = manager.doInReadActionIfProjectOpen(() -> {
      OrderEnumerator enumerator = OrderEnumerator.orderEntries(module)
        .withoutSdk()
        .withoutLibraries()
        .withoutDepModules();
      if (!includeTests) {
        enumerator = enumerator.productionOnly();
      }
      return enumerator.classes().getRoots();
    });
    if (files == null) {
      return VirtualFile.EMPTY_ARRAY;
    }
    return files;
  }

  public static class RootsCounter extends JavaCoverageClassesEnumerator {
    private int myRoots;

    public RootsCounter(@NotNull CoverageSuitesBundle suite, @NotNull Project project) {
      super(suite, project, 0);
      visitSuite();
    }

    @Override
    protected void visitRoot(File packageOutputRoot,
                             String rootPackageVMName,
                             GlobalSearchScope scope) {
      myRoots++;
    }

    public int getRoots() {
      return myRoots;
    }
  }
}
