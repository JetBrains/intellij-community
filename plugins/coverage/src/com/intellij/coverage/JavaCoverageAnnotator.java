package com.intellij.coverage;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
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
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirCoverageInfos =
    new HashMap<VirtualFile, PackageAnnotator.PackageCoverageInfo>();
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myTestDirCoverageInfos =
    new HashMap<VirtualFile, PackageAnnotator.PackageCoverageInfo>();
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

    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
    final VirtualFile virtualFile = directory.getVirtualFile();

    final boolean isInTestContent = projectFileIndex.isInTestSourceContent(virtualFile);

    if (!currentSuite.isTrackTestFolders() && isInTestContent) {
      return null;
    }
    return isInTestContent ? getCoverageInformationString(myTestDirCoverageInfos.get(virtualFile), coverageDataManager.isSubCoverageActive())
                           : getCoverageInformationString(myDirCoverageInfos.get(virtualFile), coverageDataManager.isSubCoverageActive());

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
    final boolean subCoverageActive = coverageDataManager.isSubCoverageActive();
    PackageAnnotator.PackageCoverageInfo info;
    if (module != null) {
      final PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
      PackageAnnotator.PackageCoverageInfo result = null;
      for (PsiDirectory directory : directories) {
        final VirtualFile virtualFile = directory.getVirtualFile();
        result = merge(result, myDirCoverageInfos.get(virtualFile));
        result = merge(result, myTestDirCoverageInfos.get(virtualFile));
      }
      return getCoverageInformationString(result, subCoverageActive);
    }
    else {
      info = myPackageCoverageInfos.get(psiPackage.getQualifiedName());
    }
    return getCoverageInformationString(info, subCoverageActive);
  }

  private static PackageAnnotator.PackageCoverageInfo merge(final PackageAnnotator.PackageCoverageInfo info,
                                                            final PackageAnnotator.PackageCoverageInfo testInfo) {
    if (info == null) return testInfo;
    if (testInfo == null) return info;
    final PackageAnnotator.PackageCoverageInfo coverageInfo = new PackageAnnotator.PackageCoverageInfo();
    coverageInfo.totalClassCount = info.totalClassCount + testInfo.totalClassCount;
    coverageInfo.coveredClassCount = info.coveredClassCount + testInfo.coveredClassCount;

    coverageInfo.totalLineCount = info.totalLineCount + testInfo.totalLineCount;
    coverageInfo.coveredLineCount = info.coveredLineCount + testInfo.coveredLineCount;
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
}
