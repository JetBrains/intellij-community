// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Roman.Chernyatchik
 */
public final class JavaCoverageAnnotator extends BaseCoverageAnnotator {
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverageInfos = new HashMap<>();
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlattenPackageCoverageInfos = new HashMap<>();
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirCoverageInfos =
    new HashMap<>();
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myTestDirCoverageInfos =
    new HashMap<>();
  private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfos = new ConcurrentHashMap<>();
  private final Map<PsiElement, PackageAnnotator.SummaryCoverageInfo> myExtensionCoverageInfos = CollectionFactory.createWeakMap();

  public JavaCoverageAnnotator(final Project project) {
    super(project);
  }

  public static JavaCoverageAnnotator getInstance(final Project project) {
    return project.getService(JavaCoverageAnnotator.class);
  }

  @Override
  @Nullable
  public String getDirCoverageInformationString(@NotNull final PsiDirectory directory,
                                                @NotNull final CoverageSuitesBundle currentSuite,
                                                @NotNull final CoverageDataManager coverageDataManager) {
    final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (psiPackage == null) return null;

    final VirtualFile virtualFile = directory.getVirtualFile();

    final boolean isInTestContent = TestSourcesFilter.isTestSources(virtualFile, directory.getProject());
    if (!currentSuite.isTrackTestFolders() && isInTestContent) {
      return null;
    }
    return getCoverageInformationString(isInTestContent ? myTestDirCoverageInfos.get(virtualFile) : myDirCoverageInfos.get(virtualFile), coverageDataManager.isSubCoverageActive());

  }

  @Override
  @Nullable
  public String getFileCoverageInformationString(@NotNull PsiFile file, @NotNull CoverageSuitesBundle currentSuite, @NotNull CoverageDataManager manager) {
    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
      final PackageAnnotator.ClassCoverageInfo info = extension.getSummaryCoverageInfo(this, file);
      if (info != null) {
        return getCoverageInformationString(info, manager.isSubCoverageActive());
      }
    }
    return null;
  }

  @Override
  public void onSuiteChosen(CoverageSuitesBundle newSuite) {
    super.onSuiteChosen(newSuite);

    myPackageCoverageInfos.clear();
    myFlattenPackageCoverageInfos.clear();
    myDirCoverageInfos.clear();
    myTestDirCoverageInfos.clear();
    myClassCoverageInfos.clear();
    myExtensionCoverageInfos.clear();
  }

  @Override
  protected Runnable createRenewRequest(@NotNull final CoverageSuitesBundle suite, @NotNull final CoverageDataManager dataManager) {
    final Project project = getProject();

    return () -> {
      final PackageAnnotator.Annotator annotator = new PackageAnnotator.Annotator() {
        @Override
        public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
          myPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
        }

        @Override
        public void annotatePackage(String packageQualifiedName,
                                    PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                                    boolean flatten) {
          if (flatten) {
            myFlattenPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
          }
          else {
            annotatePackage(packageQualifiedName, packageCoverageInfo);
          }
        }

        @Override
        public void annotateSourceDirectory(VirtualFile dir,
                                            PackageAnnotator.PackageCoverageInfo dirCoverageInfo,
                                            Module module) {
          myDirCoverageInfos.put(dir, dirCoverageInfo);
        }

        @Override
        public void annotateTestDirectory(VirtualFile virtualFile,
                                          PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                                          Module module) {
          myTestDirCoverageInfos.put(virtualFile, packageCoverageInfo);
        }

        @Override
        public void annotateClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
          myClassCoverageInfos.put(classQualifiedName, classCoverageInfo);
        }
      };
      final long startNs = System.nanoTime();

      final int totalRoots = new JavaCoverageClassesEnumerator.RootsCounter(suite, project).getRoots();
      new JavaCoverageClassesAnnotator(suite, project, annotator, totalRoots).visitSuite();
      dataManager.triggerPresentationUpdate();

      final long endNs = System.nanoTime();
      final int annotatedClasses = myClassCoverageInfos.size();
      final ProjectData data = suite.getCoverageData();
      final int loadedClasses = data == null ? 0 : data.getClassesNumber();
      CoverageLogger.logReportBuilding(project, TimeUnit.NANOSECONDS.toMillis(endNs - startNs), annotatedClasses, loadedClasses);
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
   * @param coverageDataManager
   * @return human-readable coverage information
   */
  @Nullable
  public String getPackageCoverageInformationString(final PsiPackage psiPackage,
                                                    @Nullable final Module module,
                                                    @NotNull final CoverageDataManager coverageDataManager) {
    return getPackageCoverageInformationString(psiPackage, module, coverageDataManager, false);
  }

  /**
   *
   *
   * @param psiPackage qualified name of a package to obtain coverage information for
   * @param module optional parameter to restrict coverage to source directories of a certain module
   * @param coverageDataManager
   * @param flatten
   * @return human-readable coverage information
   */
  @Nullable
  public String getPackageCoverageInformationString(final PsiPackage psiPackage,
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
        result = merge(result, myTestDirCoverageInfos.get(virtualFile));
      }
      return getCoverageInformationString(result, subCoverageActive);
    }
    else {
      PackageAnnotator.PackageCoverageInfo info = getPackageCoverageInfo(psiPackage.getQualifiedName(), flatten);
      return getCoverageInformationString(info, subCoverageActive);
    }
  }

  public PackageAnnotator.PackageCoverageInfo getPackageCoverageInfo(@NotNull String qualifiedName, boolean flattenPackages) {
    return flattenPackages ? myFlattenPackageCoverageInfos.get(qualifiedName) : myPackageCoverageInfos.get(qualifiedName);
  }

  public String getLineCoveredPercentage(@Nullable PackageAnnotator.SummaryCoverageInfo info) {
    if (info == null) return null;
    return getPercentage(info.getCoveredLineCount(), info.totalLineCount);
  }

  public String getMethodCoveredPercentage(@Nullable PackageAnnotator.SummaryCoverageInfo info) {
    if (info == null) return null;
    return getPercentage(info.coveredMethodCount, info.totalMethodCount);
  }

  public String getClassCoveredPercentage(@Nullable PackageAnnotator.SummaryCoverageInfo info) {
    if (info == null) return null;
    return getPercentage(info.coveredClassCount, info.totalClassCount);
  }

  public String getBranchCoveredPercentage(@Nullable PackageAnnotator.SummaryCoverageInfo info) {
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
  public @Nls String getClassCoverageInformationString(String classFQName, CoverageDataManager coverageDataManager) {
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
  public PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(@Nullable String classFQName) {
    if (classFQName == null) return null;
    return myClassCoverageInfos.get(classFQName);
  }

  @Nullable
  public PackageAnnotator.SummaryCoverageInfo getExtensionCoverageInfo(@Nullable PsiNamedElement value) {
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
