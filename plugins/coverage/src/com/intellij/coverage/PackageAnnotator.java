package com.intellij.coverage;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.util.SourceLineCounter;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.EmptyVisitor;

import java.io.IOException;
import java.util.Map;

/**
 * @author ven
 */
public class PackageAnnotator {

  private final PsiPackage myPackage;
  private final Project myProject;
  private final PsiManager myManager;
  private final CoverageDataManager myCoverageManager;

  public PackageAnnotator(final PsiPackage aPackage) {
    myPackage = aPackage;
    myProject = myPackage.getProject();
    myManager = PsiManager.getInstance(myProject);
    myCoverageManager = CoverageDataManager.getInstance(myProject);
  }

  public interface Annotator {
    void annotateSourceDirectory(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo, Module module);

    void annotateTestDirectory(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo, Module module);

    void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo);

    void annotateClass(String classQualifiedName, ClassCoverageInfo classCoverageInfo);
  }

  public static class ClassCoverageInfo {
    public int totalLineCount;
    public int fullyCoveredLineCount;
    public int partiallyCoveredLineCount;
    public int totalMethodCount;
    public int coveredMethodCount;
  }

  public static class PackageCoverageInfo {
    public int totalClassCount;
    public int coveredClassCount;
    public int totalLineCount;
    public int coveredLineCount;
  }

  //get read lock myself when needed
  public void annotate(CoverageSuiteImpl suite, Annotator annotator) {
    final ProjectData data = suite.getCoverageData(myCoverageManager);

    if (data == null) return;

    if (!suite.isPackageFiltered(myPackage.getQualifiedName())) return;

    final Module[] modules = myCoverageManager.doInReadActionIfProjectOpen(new Computable<Module[]>() {
      public Module[] compute() {
        return ModuleManager.getInstance(myProject).getModules();
      }
    });

    if (modules == null) return;

    Map<String, PackageCoverageInfo> packageCoverageMap = new HashMap<String, PackageCoverageInfo>();
    for (final Module module : modules) {
      final String rootPackageVMName = myPackage.getQualifiedName().replaceAll("\\.", "/");
      final VirtualFile packageRoot = myCoverageManager.doInReadActionIfProjectOpen(new Computable<VirtualFile>() {
        @Nullable
        public VirtualFile compute() {
          final VirtualFile outputPath = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
          if (outputPath != null) {
            return rootPackageVMName.length() > 0 ? outputPath.findFileByRelativePath(rootPackageVMName) : outputPath;
          }

          return null;
        }
      });

      if (packageRoot != null) {
        collectCoverageInformation(packageRoot, packageCoverageMap, data, rootPackageVMName, annotator, module,
                                     suite.isTrackTestFolders(), false);

      }

      if (suite.isTrackTestFolders()) {
        final VirtualFile testPackageRoot = myCoverageManager.doInReadActionIfProjectOpen(new Computable<VirtualFile>() {
          @Nullable
          public VirtualFile compute() {
            final VirtualFile outputPath = CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests();
            if (outputPath != null) {
              return rootPackageVMName.length() > 0 ? outputPath.findFileByRelativePath(rootPackageVMName) : outputPath;
            }

            return null;
          }
        });

        if (testPackageRoot != null) {
            collectCoverageInformation(testPackageRoot, packageCoverageMap, data, rootPackageVMName, annotator, module,
                                       suite.isTrackTestFolders(), true);

        }
      }
    }

    for (Map.Entry<String, PackageCoverageInfo> entry : packageCoverageMap.entrySet()) {
      final String packageFQName = entry.getKey().replaceAll("/", ".");
      final PackageCoverageInfo info = entry.getValue();
      annotator.annotatePackage(packageFQName, info);
    }
  }

  @Nullable
  private PackageCoverageInfo collectCoverageInformation(final VirtualFile packageRoot,
                                                         final Map<String, PackageCoverageInfo> packageCoverageMap,
                                                         final ProjectData projectInfo,
                                                         final String packageVMName,
                                                         final Annotator annotator,
                                                         final Module module, final boolean trackTestFolders, boolean isTestHierarchy) {
    PackageCoverageInfo dirCoverageInfo = new PackageCoverageInfo();

    final VirtualFile[] children = myCoverageManager.doInReadActionIfProjectOpen(new Computable<VirtualFile[]>() {
      public VirtualFile[] compute() {
        return packageRoot.getChildren();
      }
    });

    if (children == null) return null;

    Map<String, ClassCoverageInfo> toplevelClassCoverage = new HashMap<String, ClassCoverageInfo>();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        final String childName = child.getName();
        final String childPackageVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
        PackageCoverageInfo childCoverageInfo =
          collectCoverageInformation(child, packageCoverageMap, projectInfo, childPackageVMName, annotator, module,
                                     trackTestFolders, isTestHierarchy);
        if (childCoverageInfo != null) {
          dirCoverageInfo.totalClassCount += childCoverageInfo.totalClassCount;
          dirCoverageInfo.coveredClassCount += childCoverageInfo.coveredClassCount;
          dirCoverageInfo.totalLineCount += childCoverageInfo.totalLineCount;
          dirCoverageInfo.coveredLineCount += childCoverageInfo.coveredLineCount;
        }
      }
      else {
        if (child.getFileType().equals(StdFileTypes.CLASS)) {
          final String childName = child.getNameWithoutExtension();
          final String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
          final String toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName);
          final Boolean isInSource = myCoverageManager.doInReadActionIfProjectOpen(new Computable<Boolean>() {
            public Boolean compute() {
              final PsiClass aClass =
                JavaPsiFacade.getInstance(myManager.getProject()).findClass(toplevelClassSrcFQName, GlobalSearchScope.moduleScope(module));
              if (aClass == null || !aClass.isValid()) return Boolean.FALSE;
              final VirtualFile virtualFile = aClass.getContainingFile().getVirtualFile();
              assert virtualFile != null;
              final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
              return fileIndex.isInSourceContent(virtualFile) && (trackTestFolders || !fileIndex.isInTestSourceContent(virtualFile));
            }
          });
          if (isInSource != null && isInSource.booleanValue()) {
            collectClassCoverageInformation(child, dirCoverageInfo, projectInfo, toplevelClassCoverage, classFqVMName.replace("/", "."), toplevelClassSrcFQName);
          }
        }
      }
    }

    for (Map.Entry<String, ClassCoverageInfo> entry : toplevelClassCoverage.entrySet()) {
      final String toplevelClassName = entry.getKey();
      final ClassCoverageInfo coverageInfo = entry.getValue();
      annotator.annotateClass(toplevelClassName, coverageInfo);
      dirCoverageInfo.coveredLineCount += coverageInfo.fullyCoveredLineCount;
      dirCoverageInfo.coveredLineCount += coverageInfo.partiallyCoveredLineCount;
    }

    PackageCoverageInfo packageCoverageInfo = getOrCreateCoverageInfo(packageCoverageMap, packageVMName);
    packageCoverageInfo.totalClassCount += dirCoverageInfo.totalClassCount;
    packageCoverageInfo.totalLineCount += dirCoverageInfo.totalLineCount;
    packageCoverageInfo.coveredClassCount += dirCoverageInfo.coveredClassCount;
    packageCoverageInfo.coveredLineCount += dirCoverageInfo.coveredLineCount;

    if (isTestHierarchy) {
      annotator.annotateTestDirectory(packageVMName.replaceAll("/", "."), dirCoverageInfo, module);
    }
    else {
      annotator.annotateSourceDirectory(packageVMName.replaceAll("/", "."), dirCoverageInfo, module);
    }

    return dirCoverageInfo;
  }

  private static PackageCoverageInfo getOrCreateCoverageInfo(final Map<String, PackageCoverageInfo> packageCoverageMap,
                                                             final String packageVMName) {
    PackageCoverageInfo coverageInfo = packageCoverageMap.get(packageVMName);
    if (coverageInfo == null) {
      coverageInfo = new PackageCoverageInfo();
      packageCoverageMap.put(packageVMName, coverageInfo);
    }
    return coverageInfo;
  }

  private void collectClassCoverageInformation(final VirtualFile classFile, final PackageCoverageInfo packageCoverageInfo, final ProjectData projectInfo,
                                               final Map<String, ClassCoverageInfo> toplevelClassCoverage,
                                               final String className,
                                               final String toplevelClassSrcFQName) {
    final ClassCoverageInfo toplevelClassCoverageInfo = getOrCreateClassCoverageInfo(toplevelClassCoverage, toplevelClassSrcFQName);
    final ClassData classData = projectInfo.getClassData(className);

    if (classData != null) {
      final Object[] lines = classData.getLines();
      for (Object l : lines) {
        final LineData lineData = (LineData)l;
        if (lineData.getStatus() == LineCoverage.FULL) {
          toplevelClassCoverageInfo.fullyCoveredLineCount++;
        }
        else if (lineData.getStatus() == LineCoverage.PARTIAL) {
          toplevelClassCoverageInfo.partiallyCoveredLineCount++;
        }
      }
      boolean touchedClass = false;
      for (final Object nameAndSig : classData.getMethodSigs()) {
        final int covered = classData.getStatus((String)nameAndSig);
        if (covered != LineCoverage.NONE) {
          toplevelClassCoverageInfo.coveredMethodCount++;
          touchedClass = true;
        }
      }
      if (touchedClass) {
        packageCoverageInfo.coveredClassCount++;
      }
      toplevelClassCoverageInfo.totalLineCount += classData.getLines().length;
      toplevelClassCoverageInfo.totalMethodCount += classData.getMethodSigs().size();
      packageCoverageInfo.totalLineCount += classData.getLines().length;
      packageCoverageInfo.totalClassCount++;
    } else {
      collectNonCoveredClassInfo(classFile, classData, toplevelClassCoverageInfo, packageCoverageInfo);
    }
  }

  private static ClassCoverageInfo getOrCreateClassCoverageInfo(final Map<String, ClassCoverageInfo> toplevelClassCoverage,
                                                                final String sourceToplevelFQName) {
    ClassCoverageInfo toplevelClassCoverageInfo = toplevelClassCoverage.get(sourceToplevelFQName);
    if (toplevelClassCoverageInfo == null) {
      toplevelClassCoverageInfo = new ClassCoverageInfo();
      toplevelClassCoverage.put(sourceToplevelFQName, toplevelClassCoverageInfo);
    }
    return toplevelClassCoverageInfo;
  }

  private static String getSourceToplevelFQName(String classFQVMName) {
    final int index = classFQVMName.indexOf('$');
    if (index > 0) classFQVMName = classFQVMName.substring(0, index);
    if (classFQVMName.startsWith("/")) classFQVMName = classFQVMName.substring(1);
    return classFQVMName.replaceAll("/", ".");
  }



  /*
    return true if there is executable code in the class
   */
  private boolean collectNonCoveredClassInfo(final VirtualFile classFile,
                                             /* out */
                                             final ClassData classData, ClassCoverageInfo classCoverageInfo, final PackageCoverageInfo packageCoverageInfo) {
    final byte[] content = myCoverageManager.doInReadActionIfProjectOpen(new Computable<byte[]>() {
      public byte[] compute() {
        try {
          return classFile.contentsToByteArray();
        }
        catch (IOException e) {
          return null;
        }
      }
    });

    if (content == null) return false;
    ClassReader reader = new ClassReader(content, 0, content.length);
    final CoverageSuiteImpl coverageSuite = (CoverageSuiteImpl)CoverageDataManager.getInstance(myProject).getCurrentSuite();
    SourceLineCounter counter = new SourceLineCounter(new EmptyVisitor(), classData, coverageSuite.getRunner() instanceof IDEACoverageRunner && coverageSuite.isTracingEnabled());
    reader.accept(counter, 0);
    classCoverageInfo.totalLineCount += counter.getNSourceLines();
    classCoverageInfo.totalMethodCount += counter.getNMethodsWithCode();
    packageCoverageInfo.totalLineCount += counter.getNSourceLines();
    if (!counter.isInterface()) {
      packageCoverageInfo.totalClassCount++;
    }
    return counter.getNMethodsWithCode() > 0;
  }
}
