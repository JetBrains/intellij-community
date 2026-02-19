// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class JavaCoverageClassesEnumerator {
  protected final CoverageSuitesBundle mySuite;
  protected final Project myProject;
  protected final CoverageDataManager myCoverageManager;
  private int myRootsCount;
  private int myCurrentRootsCount;

  public JavaCoverageClassesEnumerator(final @NotNull CoverageSuitesBundle suite, final @NotNull Project project) {
    mySuite = suite;
    myProject = project;
    myCoverageManager = CoverageDataManager.getInstance(myProject);
  }

  /**
   * Visit classes with the same top level name.
   */
  protected void visitClassFiles(String topLevelClassName, List<File> files, String packageVMName) { }

  public void visitSuite() {
    Map<ModuleRequest, List<RequestRoot>> roots = AnalyseKt.collectOutputRoots(mySuite, myProject);
    myRootsCount = roots.values().stream().mapToInt(List::size).sum();
    myCurrentRootsCount = 0;
    updateProgress();

    for (var e : roots.entrySet()) {
      Module module = e.getKey().getModule();
      String packageVMName = AnalysisUtils.fqnToInternalName(e.getKey().getPackageName());
      visitSource(module, packageVMName, e.getValue());
    }
  }

  protected void visitSource(Module module, String rootPackageVMName, List<RequestRoot> roots) {
    for (RequestRoot request : roots) {
      visitRoot(request.getRoot(), rootPackageVMName, request.getSimpleName());
    }
  }

  protected void visitRoot(File packageOutputRoot, String rootPackageVMName, @Nullable String requestedSimpleName) {
    final Stack<PackageData> stack = new Stack<>(new PackageData(rootPackageVMName, packageOutputRoot.listFiles()));
    while (!stack.isEmpty()) {
      ProgressIndicatorProvider.checkCanceled();
      final PackageData packageData = stack.pop();
      final String packageVMName = packageData.packageVMName;
      final File[] children = packageData.children;
      if (children == null) continue;
      final Map<String, List<File>> topLevelClasses = new HashMap<>();
      for (File child : children) {
        if (AnalysisUtils.isClassFile(child)) {
          String simpleName = AnalysisUtils.getClassName(child);
          String classFqVMName = AnalysisUtils.buildVMName(packageVMName, simpleName);
          String toplevelClassSrcFQName = AnalysisUtils.getSourceToplevelFQName(classFqVMName);
          topLevelClasses.computeIfAbsent(toplevelClassSrcFQName, k -> new ArrayList<>()).add(child);
        }
        else if (requestedSimpleName == null && child.isDirectory()) {
          String childPackageVMName = AnalysisUtils.buildVMName(packageVMName, child.getName());
          stack.push(new PackageData(childPackageVMName, child.listFiles()));
        }
      }
      String requestedTopLevelName = requestedSimpleName == null ? null :
                                     AnalysisUtils.internalNameToFqn(AnalysisUtils.buildVMName(packageVMName, requestedSimpleName));
      for (Map.Entry<String, List<File>> entry : topLevelClasses.entrySet()) {
        String topLevelClassName = entry.getKey();
        if (requestedTopLevelName != null && !requestedTopLevelName.equals(topLevelClassName)) continue;
        visitClassFiles(topLevelClassName, entry.getValue(), packageVMName);
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

  private record PackageData(String packageVMName, File[] children) {
  }

  /**
   * Collect output roots for the specified module.
   *
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
}
