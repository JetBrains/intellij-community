// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.coverage.*;
import com.intellij.coverage.view.CoverageClassStructure;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageAnnotator extends BaseCoverageAnnotator implements Disposable.Default {
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverageInfos = new HashMap<>();
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlattenPackageCoverageInfos = new HashMap<>();
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirCoverageInfos =
    new HashMap<>();
  private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfos = new ConcurrentHashMap<>();
  private final Map<PsiElement, PackageAnnotator.SummaryCoverageInfo> myExtensionCoverageInfos = new WeakHashMap<>();
  protected CoverageClassStructure myStructure;

  public JavaCoverageAnnotator(final Project project) {
    super(project);
  }

  @Nullable
  public final CoverageClassStructure getStructure() {
    return myStructure;
  }

  public static JavaCoverageAnnotator getInstance(final Project project) {
    return project.getService(JavaCoverageAnnotator.class);
  }

  @Override
  @Nullable
  public final @Nls String getDirCoverageInformationString(@NotNull PsiDirectory psiDirectory,
                                                           @NotNull CoverageSuitesBundle currentSuite,
                                                           @NotNull CoverageDataManager coverageDataManager) {
    final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    if (psiPackage == null) return null;

    final VirtualFile virtualFile = psiDirectory.getVirtualFile();

    return getDirCoverageInformationString(psiDirectory.getProject(), virtualFile, currentSuite, coverageDataManager);
  }

  @Override
  @Nullable
  public final @Nls String getDirCoverageInformationString(@NotNull Project project, @NotNull VirtualFile virtualFile,
                                                           @NotNull CoverageSuitesBundle currentSuite,
                                                           @NotNull CoverageDataManager coverageDataManager) {
    if (!currentSuite.isTrackTestFolders() && TestSourcesFilter.isTestSources(virtualFile, project)) {
      return null;
    }
    return getCoverageInformationString(myDirCoverageInfos.get(virtualFile), coverageDataManager.isSubCoverageActive());
  }

  @Override
  @Nullable
  public final String getFileCoverageInformationString(@NotNull PsiFile psiFile,
                                                       @NotNull CoverageSuitesBundle currentSuite,
                                                       @NotNull CoverageDataManager manager) {
    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
      final PackageAnnotator.ClassCoverageInfo info = extension.getSummaryCoverageInfo(this, psiFile);
      if (info != null) {
        return getCoverageInformationString(info, manager.isSubCoverageActive());
      }
    }
    return null;
  }

  @Override
  public final void onSuiteChosen(CoverageSuitesBundle newSuite) {
    super.onSuiteChosen(newSuite);

    myPackageCoverageInfos.clear();
    myFlattenPackageCoverageInfos.clear();
    myDirCoverageInfos.clear();
    myClassCoverageInfos.clear();
    myExtensionCoverageInfos.clear();
    if (myStructure != null) {
      Disposer.dispose(myStructure);
    }
    myStructure = null;
  }

  public static class JavaCoverageInfoCollector implements CoverageInfoCollector {
    private final JavaCoverageAnnotator myAnnotator;

    public JavaCoverageInfoCollector(JavaCoverageAnnotator annotator) { myAnnotator = annotator; }

    @Override
    public void addPackage(String packageQualifiedName,
                           PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                           boolean flatten) {
      if (flatten) {
        myAnnotator.myFlattenPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
      }
      else {
        myAnnotator.myPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
      }
    }

    @Override
    public void addSourceDirectory(VirtualFile dir,
                                   PackageAnnotator.PackageCoverageInfo dirCoverageInfo) {
      myAnnotator.myDirCoverageInfos.put(dir, dirCoverageInfo);
    }

    @Override
    public void addClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
      myAnnotator.myClassCoverageInfos.put(classQualifiedName, classCoverageInfo);
    }
  }

  @Override
  protected Runnable createRenewRequest(@NotNull final CoverageSuitesBundle suite, @NotNull final CoverageDataManager dataManager) {
    final Project project = getProject();

    return () -> {
      long timeMs = TimeoutUtil.measureExecutionTime(() -> {
        collectSummaryInfo(suite, project);
        myStructure = new CoverageClassStructure(project, this, suite);
        Disposer.register(this, myStructure);
        dataManager.triggerPresentationUpdate();
      });

      int annotatedClasses = myClassCoverageInfos.size();
      ProjectData data = suite.getCoverageData();
      int loadedClasses = data == null ? 0 : data.getClassesNumber();
      CoverageLogger.logReportBuilding(project, timeMs, annotatedClasses, loadedClasses);
    };
  }

  @Nullable
  public static @Nls String getCoverageInformationString(PackageAnnotator.SummaryCoverageInfo info, boolean subCoverageActive) {
    if (info == null) return null;
    if (info.totalClassCount == 0 || info.totalLineCount == 0) return null;
    if (subCoverageActive) {
      return info.coveredClassCount + info.getCoveredLineCount() > 0 ? CoverageBundle.message("coverage.view.text.covered") : null;
    }
    return JavaCoverageBundle.message("coverage.view.text.classes.covered", (int)((double)info.coveredClassCount / info.totalClassCount * 100)) +  ", " +
           CoverageBundle.message("coverage.view.text.lines.covered", (int)((double)info.getCoveredLineCount() / info.totalLineCount * 100));
  }

  /**
   *
   * @param psiPackage qualified name of a package to obtain coverage information for
   * @param module optional parameter to restrict coverage to source directories of a certain module
   * @return human-readable coverage information
   */
  @Nullable
  public final String getPackageCoverageInformationString(final PsiPackage psiPackage,
                                                    @Nullable final Module module,
                                                    @NotNull final CoverageDataManager coverageDataManager) {
    return getPackageCoverageInformationString(psiPackage, module, coverageDataManager, false);
  }

  /**
   *
   *
   * @param psiPackage qualified name of a package to obtain coverage information for
   * @param module optional parameter to restrict coverage to source directories of a certain module
   * @return human-readable coverage information
   */
  @Nullable
  public final String getPackageCoverageInformationString(final PsiPackage psiPackage,
                                                    @Nullable final Module module,
                                                    @NotNull final CoverageDataManager coverageDataManager,
                                                    boolean flatten) {
    if (psiPackage == null) return null;
    final boolean subCoverageActive = coverageDataManager.isSubCoverageActive();
    if (module != null) {
      final PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
      PackageAnnotator.SummaryCoverageInfo result = null;
      for (PsiDirectory directory : directories) {
        final VirtualFile virtualFile = directory.getVirtualFile();
        result = merge(result, myDirCoverageInfos.get(virtualFile));
      }
      return getCoverageInformationString(result, subCoverageActive);
    }
    else {
      PackageAnnotator.PackageCoverageInfo info = getPackageCoverageInfo(psiPackage.getQualifiedName(), flatten);
      return getCoverageInformationString(info, subCoverageActive);
    }
  }

  public final PackageAnnotator.PackageCoverageInfo getPackageCoverageInfo(@NotNull String qualifiedName, boolean flattenPackages) {
    return flattenPackages ? myFlattenPackageCoverageInfos.get(qualifiedName) : myPackageCoverageInfos.get(qualifiedName);
  }

  public static String getLineCoveredPercentage(@Nullable PackageAnnotator.SummaryCoverageInfo info) {
    if (info == null) return null;
    return getPercentage(info.getCoveredLineCount(), info.totalLineCount);
  }

  public static String getMethodCoveredPercentage(@Nullable PackageAnnotator.SummaryCoverageInfo info) {
    if (info == null) return null;
    return getPercentage(info.coveredMethodCount, info.totalMethodCount);
  }

  public static String getClassCoveredPercentage(@Nullable PackageAnnotator.SummaryCoverageInfo info) {
    if (info == null) return null;
    return getPercentage(info.coveredClassCount, info.totalClassCount);
  }

  public static String getBranchCoveredPercentage(@Nullable PackageAnnotator.SummaryCoverageInfo info) {
    if (info == null) return null;
    return getPercentage(info.coveredBranchCount, info.totalBranchCount);
  }


  private static String getPercentage(int covered, int total) {
    final int percentage = total == 0 ? 100 : (int)((double)covered / total * 100);
    return percentage + "% (" + covered + "/" + total + ")";
  }

  public static PackageAnnotator.SummaryCoverageInfo merge(@Nullable final PackageAnnotator.SummaryCoverageInfo info,
                                                           @Nullable final PackageAnnotator.SummaryCoverageInfo testInfo) {
    if (info == null) return testInfo;
    if (testInfo == null) return info;
    final PackageAnnotator.PackageCoverageInfo coverageInfo = new PackageAnnotator.PackageCoverageInfo();
    coverageInfo.totalClassCount = info.totalClassCount + testInfo.totalClassCount;
    coverageInfo.coveredClassCount = info.coveredClassCount + testInfo.coveredClassCount;

    coverageInfo.totalLineCount = info.totalLineCount + testInfo.totalLineCount;
    coverageInfo.coveredLineCount = info.getCoveredLineCount() + testInfo.getCoveredLineCount();

    coverageInfo.totalBranchCount = info.totalBranchCount + testInfo.totalBranchCount;
    coverageInfo.coveredBranchCount = info.coveredBranchCount + testInfo.coveredBranchCount;
    return coverageInfo;
  }

  /**
   * @param classFQName to obtain coverage information for
   * @return human-readable coverage information
   */
  @Nullable
  public final @Nls String getClassCoverageInformationString(String classFQName, CoverageDataManager coverageDataManager) {
    final PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
    return getClassCoverageInformationString(info, coverageDataManager);
  }

  @Nullable
  public static @Nls String getClassCoverageInformationString(PackageAnnotator.ClassCoverageInfo info, CoverageDataManager coverageDataManager) {
    if (info == null) return null;
    if (info.totalMethodCount == 0 || info.totalLineCount == 0) return null;
    if (coverageDataManager.isSubCoverageActive()){
      return info.coveredMethodCount + info.fullyCoveredLineCount + info.partiallyCoveredLineCount > 0 ? CoverageBundle.message("coverage.view.text.covered") : null;
    }
    return JavaCoverageBundle.message("coverage.view.text.methods.covered", (int)((double)info.coveredMethodCount / info.totalMethodCount * 100)) +  ", " +
           CoverageBundle.message("coverage.view.text.lines.covered", (int)((double)(info.fullyCoveredLineCount + info.partiallyCoveredLineCount) / info.totalLineCount * 100));
  }

  @Nullable
  public final PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(@Nullable String classFQName) {
    if (classFQName == null) return null;
    return myClassCoverageInfos.get(classFQName);
  }

  public final Map<String, PackageAnnotator.ClassCoverageInfo> getClassesCoverage() {
    return myClassCoverageInfos;
  }

  private void collectSummaryInfo(@NotNull CoverageSuitesBundle suite, Project project) {
    var collector = new JavaCoverageInfoCollector(this);
    if (shouldSkipUnloadedClassesAnalysis(suite)) {
      JavaCoverageReportEnumerator.collectSummaryInReport(suite, project, collector);
    }
    else {
      new JavaCoverageClassesAnnotator(suite, project, collector).visitSuite();
    }
  }

  private static boolean shouldSkipUnloadedClassesAnalysis(CoverageSuitesBundle bundle) {
    return ContainerUtil.and(bundle.getSuites(), suite -> suite instanceof JavaCoverageSuite javaSuite && javaSuite.isSkipUnloadedClassesAnalysis());
  }

  @Nullable
  public final PackageAnnotator.SummaryCoverageInfo getExtensionCoverageInfo(@Nullable PsiNamedElement value) {
    if (value == null) return null;
    PackageAnnotator.SummaryCoverageInfo cachedInfo = myExtensionCoverageInfos.get(value);
    if (cachedInfo != null) {
      return cachedInfo;
    }

    return JavaCoverageEngineExtension.EP_NAME.computeSafeIfAny(extension -> {
      PackageAnnotator.SummaryCoverageInfo info = extension.getSummaryCoverageInfo(this, value);
      if (info != null) {
        myExtensionCoverageInfos.put(value, info);
        return info;
      }
      return null;
    });
  }
}
