/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.coverage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageAnnotator extends BaseCoverageAnnotator {
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverageInfos = new HashMap<>();
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlattenPackageCoverageInfos = new HashMap<>();
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirCoverageInfos =
    new HashMap<>();
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myTestDirCoverageInfos =
    new HashMap<>();
  private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfos = new HashMap<>();
  private final WeakHashMap<PsiElement, PackageAnnotator.SummaryCoverageInfo> myExtensionCoverageInfos =
    new WeakHashMap<>();

  public JavaCoverageAnnotator(final Project project) {
    super(project);
  }

  public static JavaCoverageAnnotator getInstance(final Project project) {
    return ServiceManager.getService(project, JavaCoverageAnnotator.class);
  }

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

  @Nullable
  public String getFileCoverageInformationString(@NotNull PsiFile file, @NotNull CoverageSuitesBundle currentSuite, @NotNull CoverageDataManager manager) {
    // N/A here we work with java classes
    return null;
  }

  public void onSuiteChosen(CoverageSuitesBundle newSuite) {
    super.onSuiteChosen(newSuite);

    myPackageCoverageInfos.clear();
    myFlattenPackageCoverageInfos.clear();
    myDirCoverageInfos.clear();
    myTestDirCoverageInfos.clear();
    myClassCoverageInfos.clear();
    myExtensionCoverageInfos.clear();
  }

  protected Runnable createRenewRequest(@NotNull final CoverageSuitesBundle suite, @NotNull final CoverageDataManager dataManager) {


    final Project project = getProject();
    final List<PsiPackage> packages = new ArrayList<>();
    final List<PsiClass> classes = new ArrayList<>();

    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;
      classes.addAll(javaSuite.getCurrentSuiteClasses(project));
      packages.addAll(javaSuite.getCurrentSuitePackages(project));
    }

    if (packages.isEmpty() && classes.isEmpty()) {
      return null;
    }

    return () -> {
      final PackageAnnotator.Annotator annotator = new PackageAnnotator.Annotator() {
        public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
          myPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
        }

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

        public void annotateSourceDirectory(VirtualFile dir,
                                            PackageAnnotator.PackageCoverageInfo dirCoverageInfo,
                                            Module module) {
          myDirCoverageInfos.put(dir, dirCoverageInfo);
        }

        public void annotateTestDirectory(VirtualFile virtualFile,
                                          PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                                          Module module) {
          myTestDirCoverageInfos.put(virtualFile, packageCoverageInfo);
        }

        public void annotateClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
          myClassCoverageInfos.put(classQualifiedName, classCoverageInfo);
        }
      };
      for (PsiPackage aPackage : packages) {
        new PackageAnnotator(aPackage).annotate(suite, annotator);
      }
      for (final PsiClass aClass : classes) {
        Runnable runnable = () -> {
          final String packageName = ((PsiClassOwner)aClass.getContainingFile()).getPackageName();
          final PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
          if (psiPackage == null) return;
          new PackageAnnotator(psiPackage).annotateFilteredClass(aClass, suite, annotator);
        };
        ApplicationManager.getApplication().runReadAction(runnable);
      }
      dataManager.triggerPresentationUpdate();
    };
  }

  @Nullable
  public static String getCoverageInformationString(PackageAnnotator.SummaryCoverageInfo info, boolean subCoverageActive) {
    if (info == null) return null;
    if (info.totalClassCount == 0 || info.totalLineCount == 0) return null;
    if (subCoverageActive) {
      return info.coveredClassCount + info.getCoveredLineCount() > 0 ? "covered" : null;
    }
    return (int)((double)info.coveredClassCount / info.totalClassCount * 100) +  "% classes, " +
           (int)((double)info.getCoveredLineCount() / info.totalLineCount * 100) + "% lines covered";
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
      PackageAnnotator.PackageCoverageInfo info = getPackageCoverageInfo(psiPackage, flatten);
      return getCoverageInformationString(info, subCoverageActive);
    }
  }

  public PackageAnnotator.PackageCoverageInfo getPackageCoverageInfo(@NotNull PsiPackage psiPackage, boolean flattenPackages) {
    final String qualifiedName = psiPackage.getQualifiedName();
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

  private static String getPercentage(int covered, int total) {
    return (int)((double)covered /total * 100) +"% (" + covered + "/" + total + ")";
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
    return coverageInfo;
  }

  /**
   * @param classFQName to obtain coverage information for
   * @return human-readable coverage information
   */
  @Nullable
  public String getClassCoverageInformationString(String classFQName, CoverageDataManager coverageDataManager) {
    final PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
    if (info == null) return null;
    if (info.totalMethodCount == 0 || info.totalLineCount == 0) return null;
    if (coverageDataManager.isSubCoverageActive()){
      return info.coveredMethodCount + info.fullyCoveredLineCount + info.partiallyCoveredLineCount > 0 ? "covered" : null;
    }
    return (int)((double)info.coveredMethodCount / info.totalMethodCount * 100) +  "% methods, " +
           (int)((double)(info.fullyCoveredLineCount + info.partiallyCoveredLineCount) / info.totalLineCount * 100) + "% lines covered";
  }

  @Nullable
  public PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(String classFQName) {
    return myClassCoverageInfos.get(classFQName);
  }

  public PackageAnnotator.SummaryCoverageInfo getExtensionCoverageInfo(PsiNamedElement value) {
    PackageAnnotator.SummaryCoverageInfo cachedInfo = myExtensionCoverageInfos.get(value);
    if (cachedInfo != null) {
      return cachedInfo;
    }
    for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
      PackageAnnotator.SummaryCoverageInfo info = extension.getSummaryCoverageInfo(this, value);
      if (info != null) {
        myExtensionCoverageInfos.put(value, info);
        return info;
      }
    }

    return null;
  }
}
