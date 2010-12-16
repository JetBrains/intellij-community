package com.intellij.coverage;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Roman.Chernyatchik
 */
public class JavaCoverageAnnotator extends BaseCoverageAnnotator {
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverageInfos = new HashMap<String, PackageAnnotator.PackageCoverageInfo>();
  private final Map<Pair<String, Module>, PackageAnnotator.PackageCoverageInfo> myDirCoverageInfos = new HashMap<Pair<String, Module>, PackageAnnotator.PackageCoverageInfo>();
  private final Map<Pair<String, Module>, PackageAnnotator.PackageCoverageInfo> myTestDirCoverageInfos = new HashMap<Pair<String, Module>, PackageAnnotator.PackageCoverageInfo>();
  private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfos = new HashMap<String, PackageAnnotator.ClassCoverageInfo>();

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

    final String packageFQName = psiPackage.getQualifiedName();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
    final Module module = projectFileIndex.getModuleForFile(directory.getVirtualFile());
    if (module == null) return null;

    final boolean isInTestContent = projectFileIndex.isInTestSourceContent(directory.getVirtualFile());

    if (!currentSuite.isTrackTestFolders() && isInTestContent) {
      return null;
    }
    final Pair<String, Module> qualifiedPair = new Pair<String, Module>(packageFQName, module);
    return isInTestContent ? getCoverageInformationString(myTestDirCoverageInfos.get(qualifiedPair), coverageDataManager.isSubCoverageActive())
                           : getCoverageInformationString(myDirCoverageInfos.get(qualifiedPair), coverageDataManager.isSubCoverageActive());

  }

  @Nullable
  public String getFileCoverageInformationString(@NotNull PsiFile file, @NotNull CoverageSuitesBundle currentSuite, @NotNull CoverageDataManager manager) {
    // N/A here we work with java classes
    return null;
  }

  public void onSuiteChosen(CoverageSuitesBundle newSuite) {
    super.onSuiteChosen(newSuite);

    myPackageCoverageInfos.clear();
    myDirCoverageInfos.clear();
    myTestDirCoverageInfos.clear();
    myClassCoverageInfos.clear();
  }

  protected Runnable createRenewRequest(@NotNull final CoverageSuitesBundle suite, @NotNull final CoverageDataManager dataManager) {


    final Project project = getProject();
    final List<PsiPackage> packages = new ArrayList<PsiPackage>();
    final List<PsiClass> classes = new ArrayList<PsiClass>();

    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;
      classes.addAll(javaSuite.getCurrentSuiteClasses(project));
      packages.addAll(javaSuite.getCurrentSuitePackages(project));
    }

    if (packages.isEmpty() && classes.isEmpty()) {
      return null;
    }

    return new Runnable() {
      public void run() {
        for (PsiPackage aPackage : packages) {
          new PackageAnnotator(aPackage).annotate(suite, new PackageAnnotator.Annotator() {
            public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
              myPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
            }

            public void annotateSourceDirectory(String packageQualifiedName,
                                                PackageAnnotator.PackageCoverageInfo dirCoverageInfo,
                                                Module module) {
              final Pair<String, Module> p = new Pair<String, Module>(packageQualifiedName, module);
              myDirCoverageInfos.put(p, dirCoverageInfo);
            }

            public void annotateTestDirectory(String packageQualifiedName,
                                              PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                                              Module module) {
              final Pair<String, Module> p = new Pair<String, Module>(packageQualifiedName, module);
              myTestDirCoverageInfos.put(p, packageCoverageInfo);
            }

            public void annotateClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
              myClassCoverageInfos.put(classQualifiedName, classCoverageInfo);
            }
          });
        }
        dataManager.triggerPresentationUpdate();
      }
    };
  }

  @Nullable
  private String getCoverageInformationString(PackageAnnotator.PackageCoverageInfo info, boolean subCoverageActive) {
    if (info == null) return null;
    if (info.totalClassCount == 0 || info.totalLineCount == 0) return null;
    if (subCoverageActive) {
      return info.coveredClassCount + info.coveredLineCount > 0 ? "covered" : null;
    }
    return (int)((double)info.coveredClassCount / info.totalClassCount * 100) +  "% classes, " +
           (int)((double)info.coveredLineCount / info.totalLineCount * 100) + "% lines covered";
  }

  /**
   * @param packageFQName qualified name of a package to obtain coverage information for
   * @param module optional parameter to restrict coverage to source directories of a certain module
   * @param coverageDataManager
   * @return human-readable coverage information
   */
  public String getPackageCoverageInformationString(final String packageFQName,
                                                    @Nullable final Module module,
                                                    @NotNull final CoverageDataManager coverageDataManager) {
    final boolean subCoverageActive = coverageDataManager.isSubCoverageActive();
    PackageAnnotator.PackageCoverageInfo info;
    if (module != null) {
      final Pair<String, Module> p = new Pair<String, Module>(packageFQName, module);
      info = myDirCoverageInfos.get(p);
      final PackageAnnotator.PackageCoverageInfo testInfo = myTestDirCoverageInfos.get(p);
      if (testInfo != null) {
        if (info == null) {
          return getCoverageInformationString(testInfo, subCoverageActive);
        } else {
          final PackageAnnotator.PackageCoverageInfo coverageInfo = new PackageAnnotator.PackageCoverageInfo();
          coverageInfo.totalClassCount = info.totalClassCount + testInfo.totalClassCount;
          coverageInfo.coveredClassCount = info.coveredClassCount + testInfo.coveredClassCount;

          coverageInfo.totalLineCount = info.totalLineCount + testInfo.totalLineCount;
          coverageInfo.coveredLineCount = info.coveredLineCount + testInfo.coveredLineCount;
          return getCoverageInformationString(coverageInfo, subCoverageActive);
        }
      }
    }
    else {
      info = myPackageCoverageInfos.get(packageFQName);
    }
    return getCoverageInformationString(info, subCoverageActive);
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
}
