package com.intellij.coverage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
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
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    void annotateSourceDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module);

    void annotateTestDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module);

    void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo);
    
    void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo, boolean flatten);

    void annotateClass(String classQualifiedName, ClassCoverageInfo classCoverageInfo);
  }

  public static class ClassCoverageInfo {
    public int totalLineCount;
    public int fullyCoveredLineCount;
    public int partiallyCoveredLineCount;
    public int totalMethodCount;
    public int coveredMethodCount;

    public int totalClassCount = 1;
    public int coveredClassCount;
  }

  public static class PackageCoverageInfo {
    public int totalClassCount;
    public int coveredClassCount;
    public int totalLineCount;
    public int coveredLineCount;

    public int coveredMethodCount;
    public int totalMethodCount;
  }

  public static class DirCoverageInfo extends PackageCoverageInfo {
    public VirtualFile sourceRoot;

    public DirCoverageInfo(VirtualFile sourceRoot) {
      this.sourceRoot = sourceRoot;
    }
  }

  //get read lock myself when needed
  public void annotate(final CoverageSuitesBundle suite, Annotator annotator) {
    final ProjectData data = suite.getCoverageData();

    if (data == null) return;

    final String qualifiedName = myPackage.getQualifiedName();
    boolean filtered = false;
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      if (((JavaCoverageSuite)coverageSuite).isPackageFiltered(qualifiedName)) {
        filtered = true;
        break;
      }

    }
    if (!filtered) return;

    final GlobalSearchScope scope = suite.getSearchScope(myProject);
    final Module[] modules = myCoverageManager.doInReadActionIfProjectOpen(new Computable<Module[]>() {
      public Module[] compute() {
        return ModuleManager.getInstance(myProject).getModules();
      }
    });

    if (modules == null) return;

    Map<String, PackageCoverageInfo> packageCoverageMap = new HashMap<String, PackageCoverageInfo>();
    Map<String, PackageCoverageInfo> flattenPackageCoverageMap = new HashMap<String, PackageCoverageInfo>();
    for (final Module module : modules) {
      if (!scope.isSearchInModuleContent(module)) continue;
      final String rootPackageVMName = qualifiedName.replaceAll("\\.", "/");
      final VirtualFile output = myCoverageManager.doInReadActionIfProjectOpen(new Computable<VirtualFile>() {
        @Nullable
        public VirtualFile compute() {
          return CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
        }
      });


      if (output != null) {
        File outputRoot = findRelativeFile(rootPackageVMName, output);
        if (outputRoot.exists()) {
          collectCoverageInformation(outputRoot, packageCoverageMap, flattenPackageCoverageMap, data, rootPackageVMName, annotator, module,
                                     suite.isTrackTestFolders(), false);
        }

      }

      if (suite.isTrackTestFolders()) {
        final VirtualFile testPackageRoot = myCoverageManager.doInReadActionIfProjectOpen(new Computable<VirtualFile>() {
          @Nullable
          public VirtualFile compute() {
            return CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests();
          }
        });

        if (testPackageRoot != null) {
          final File outputRoot = findRelativeFile(rootPackageVMName, testPackageRoot);
          if (outputRoot.exists()) {
            collectCoverageInformation(outputRoot, packageCoverageMap, flattenPackageCoverageMap, data, rootPackageVMName, annotator, module,
                                         suite.isTrackTestFolders(), true);
          }
        }
      }
    }

    for (Map.Entry<String, PackageCoverageInfo> entry : packageCoverageMap.entrySet()) {
      final String packageFQName = entry.getKey().replaceAll("/", ".");
      final PackageCoverageInfo info = entry.getValue();
      annotator.annotatePackage(packageFQName, info);
    }

    for (Map.Entry<String, PackageCoverageInfo> entry : flattenPackageCoverageMap.entrySet()) {
      final String packageFQName = entry.getKey().replaceAll("/", ".");
      final PackageCoverageInfo info = entry.getValue();
      annotator.annotatePackage(packageFQName, info, true);
    }
  }

  private static File findRelativeFile(String rootPackageVMName, VirtualFile output) {
    File outputRoot = VfsUtilCore.virtualToIoFile(output);
    outputRoot = rootPackageVMName.length() > 0 ? new File(outputRoot, FileUtil.toSystemDependentName(rootPackageVMName)) : outputRoot;
    return outputRoot;
  }

  public void annotateFilteredClass(PsiClass psiClass, CoverageSuitesBundle bundle, Annotator annotator) {
    final ProjectData data = bundle.getCoverageData();
    if (data == null) return;
    final Module module = ModuleUtil.findModuleForPsiElement(psiClass);
    if (module != null) {
      final boolean isInTests = ProjectRootManager.getInstance(module.getProject()).getFileIndex()
        .isInTestSourceContent(psiClass.getContainingFile().getVirtualFile());
      final CompilerModuleExtension moduleExtension = CompilerModuleExtension.getInstance(module);
      final VirtualFile outputPath = isInTests ? moduleExtension.getCompilerOutputPathForTests() : moduleExtension.getCompilerOutputPath();
      
      if (outputPath != null) {
        final String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) return;
        final String packageVMName = StringUtil.getPackageName(qualifiedName).replace('.', '/');
        final File packageRoot = findRelativeFile(packageVMName, outputPath);
        if (packageRoot != null && packageRoot.exists()) {
          Map<String, ClassCoverageInfo> toplevelClassCoverage = new HashMap<String, ClassCoverageInfo>();
          final File[] files = packageRoot.listFiles();
          if (files != null) {
            for (File child : files) {
              if (isClassFile(child)) {
                final String childName = getClassName(child);
                final String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
                final String toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName);
                if (toplevelClassSrcFQName.equals(qualifiedName)) {
                  collectClassCoverageInformation(child, new PackageCoverageInfo(), data, toplevelClassCoverage, classFqVMName.replace("/", "."), toplevelClassSrcFQName);
                }
              }
            }
          }
          for (ClassCoverageInfo coverageInfo : toplevelClassCoverage.values()) {
            annotator.annotateClass(qualifiedName, coverageInfo);
          }
        }
      }
    }
  }
  
  @Nullable
  private DirCoverageInfo[] collectCoverageInformation(final File packageOutputRoot,
                                                       final Map<String, PackageCoverageInfo> packageCoverageMap,
                                                       Map<String, PackageCoverageInfo> flattenPackageCoverageMap,
                                                       final ProjectData projectInfo,
                                                       final String packageVMName,
                                                       final Annotator annotator,
                                                       final Module module,
                                                       final boolean trackTestFolders,
                                                       final boolean isTestHierarchy) {
    final List<DirCoverageInfo> dirs = new ArrayList<DirCoverageInfo>();
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      for (SourceFolder folder : contentEntry.getSourceFolders(isTestHierarchy ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE)) {
        final VirtualFile file = folder.getFile();
        if (file == null) continue;
        final String prefix = folder.getPackagePrefix().replaceAll("\\.", "/");
        final VirtualFile relativeSrcRoot = file.findFileByRelativePath(StringUtil.trimStart(packageVMName, prefix));
        dirs.add(new DirCoverageInfo(relativeSrcRoot));
      }
    }

    final File[] children = packageOutputRoot.listFiles();

    if (children == null) return null;

    Map<String, ClassCoverageInfo> toplevelClassCoverage = new HashMap<String, ClassCoverageInfo>();
    for (File child : children) {
      if (child.isDirectory()) {
        final String childName = child.getName();
        final String childPackageVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
        final DirCoverageInfo[] childCoverageInfo =
          collectCoverageInformation(child, packageCoverageMap, flattenPackageCoverageMap, projectInfo, childPackageVMName, annotator, module,
                                     trackTestFolders, isTestHierarchy);
        if (childCoverageInfo != null) {
          for (int i = 0; i < childCoverageInfo.length; i++) {
            DirCoverageInfo coverageInfo = childCoverageInfo[i];
            final DirCoverageInfo parentDir = dirs.get(i);
            parentDir.totalClassCount += coverageInfo.totalClassCount;
            parentDir.coveredClassCount += coverageInfo.coveredClassCount;
            parentDir.totalLineCount += coverageInfo.totalLineCount;
            parentDir.coveredLineCount += coverageInfo.coveredLineCount;
            parentDir.totalMethodCount += coverageInfo.totalMethodCount;
            parentDir.coveredMethodCount += coverageInfo.coveredMethodCount;
          }
        }
      }
      else {
        if (isClassFile(child)) {
          final String childName = getClassName(child);
          final String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
          final String toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName);
          final VirtualFile[] containingFile = new VirtualFile[1];
          final Boolean isInSource = myCoverageManager.doInReadActionIfProjectOpen(new Computable<Boolean>() {
            public Boolean compute() {
              final PsiClass aClass =
                JavaPsiFacade.getInstance(myManager.getProject()).findClass(toplevelClassSrcFQName, GlobalSearchScope.moduleScope(module));
              if (aClass == null || !aClass.isValid()) return Boolean.FALSE;
              containingFile[0] = aClass.getContainingFile().getVirtualFile();
              assert containingFile[0] != null : aClass;
              final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
              return fileIndex.isUnderSourceRootOfType(containingFile[0], JavaModuleSourceRootTypes.SOURCES)
                     && (trackTestFolders || !fileIndex.isInTestSourceContent(containingFile[0]));
            }
          });
          if (isInSource != null && isInSource.booleanValue()) {
            for (DirCoverageInfo dirCoverageInfo : dirs) {
              if (dirCoverageInfo.sourceRoot != null && VfsUtil.isAncestor(dirCoverageInfo.sourceRoot, containingFile[0], false)) {
                collectClassCoverageInformation(child, dirCoverageInfo, projectInfo, toplevelClassCoverage, classFqVMName.replace("/", "."), toplevelClassSrcFQName);
                break;
              }
            }
          }
        }
      }
    }

    for (Map.Entry<String, ClassCoverageInfo> entry : toplevelClassCoverage.entrySet()) {
      final String toplevelClassName = entry.getKey();
      final ClassCoverageInfo coverageInfo = entry.getValue();
      annotator.annotateClass(toplevelClassName, coverageInfo);
    }

    PackageCoverageInfo flattenPackageCoverageInfo = getOrCreateCoverageInfo(flattenPackageCoverageMap, packageVMName);
    for (Map.Entry<String, ClassCoverageInfo> entry : toplevelClassCoverage.entrySet()) {
      final ClassCoverageInfo coverageInfo = entry.getValue();
      flattenPackageCoverageInfo.coveredClassCount += coverageInfo.coveredClassCount;
      flattenPackageCoverageInfo.totalClassCount += coverageInfo.totalClassCount;

      flattenPackageCoverageInfo.coveredLineCount += coverageInfo.fullyCoveredLineCount + coverageInfo.partiallyCoveredLineCount;
      flattenPackageCoverageInfo.totalLineCount += coverageInfo.totalLineCount;
      
      flattenPackageCoverageInfo.coveredMethodCount += coverageInfo.coveredMethodCount;
      flattenPackageCoverageInfo.totalMethodCount += coverageInfo.totalMethodCount;
    }
    
    PackageCoverageInfo packageCoverageInfo = getOrCreateCoverageInfo(packageCoverageMap, packageVMName);
    for (DirCoverageInfo dir : dirs) {
      packageCoverageInfo.totalClassCount += dir.totalClassCount;
      packageCoverageInfo.totalLineCount += dir.totalLineCount;
      packageCoverageInfo.coveredClassCount += dir.coveredClassCount;
      packageCoverageInfo.coveredLineCount += dir.coveredLineCount;
      packageCoverageInfo.coveredMethodCount += dir.coveredMethodCount;
      packageCoverageInfo.totalMethodCount += dir.totalMethodCount;

      if (isTestHierarchy) {
        annotator.annotateTestDirectory(dir.sourceRoot, dir, module);
      }
      else {
        annotator.annotateSourceDirectory(dir.sourceRoot, dir, module);
      }
    }

    return dirs.toArray(new DirCoverageInfo[dirs.size()]);
  }

  private static boolean isClassFile(File classFile) {
    return classFile.getName().endsWith(".class");
  }
  
  private static String getClassName(File classFile) {
    return StringUtil.trimEnd(classFile.getName(), ".class");
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

  private void collectClassCoverageInformation(final File classFile, final PackageCoverageInfo packageCoverageInfo, final ProjectData projectInfo,
                                               final Map<String, ClassCoverageInfo> toplevelClassCoverage,
                                               final String className,
                                               final String toplevelClassSrcFQName) {
    final ClassCoverageInfo toplevelClassCoverageInfo = new ClassCoverageInfo();

    final ClassData classData = projectInfo.getClassData(className);

    if (classData != null && classData.getLines() != null) {
      final Object[] lines = classData.getLines();
      for (Object l : lines) {
        if (l instanceof LineData) {
          final LineData lineData = (LineData)l;
          if (lineData.getStatus() == LineCoverage.FULL) {
            toplevelClassCoverageInfo.fullyCoveredLineCount++;
          }
          else if (lineData.getStatus() == LineCoverage.PARTIAL) {
            toplevelClassCoverageInfo.partiallyCoveredLineCount++;
          }
          toplevelClassCoverageInfo.totalLineCount++;
          packageCoverageInfo.totalLineCount++;
        }
      }
      boolean touchedClass = false;
      final Collection methodSigs = classData.getMethodSigs();
      for (final Object nameAndSig : methodSigs) {
        final int covered = classData.getStatus((String)nameAndSig);
        if (covered != LineCoverage.NONE) {
          toplevelClassCoverageInfo.coveredMethodCount++;
          touchedClass = true;
        }
      }
      if (!methodSigs.isEmpty()) {
        if (touchedClass) {
          packageCoverageInfo.coveredClassCount++;
        }
        toplevelClassCoverageInfo.totalMethodCount += methodSigs.size();
        packageCoverageInfo.totalClassCount++;

        packageCoverageInfo.coveredLineCount += toplevelClassCoverageInfo.fullyCoveredLineCount;
        packageCoverageInfo.coveredLineCount += toplevelClassCoverageInfo.partiallyCoveredLineCount;
        packageCoverageInfo.coveredMethodCount += toplevelClassCoverageInfo.coveredMethodCount;
        packageCoverageInfo.totalMethodCount += toplevelClassCoverageInfo.totalMethodCount;
      } else {
        return;
      }
    } else {
      if (!collectNonCoveredClassInfo(classFile, toplevelClassCoverageInfo, packageCoverageInfo)) return;
    }

    ClassCoverageInfo classCoverageInfo = getOrCreateClassCoverageInfo(toplevelClassCoverage, toplevelClassSrcFQName);
    classCoverageInfo.totalLineCount += toplevelClassCoverageInfo.totalLineCount;
    classCoverageInfo.fullyCoveredLineCount += toplevelClassCoverageInfo.fullyCoveredLineCount;
    classCoverageInfo.partiallyCoveredLineCount += toplevelClassCoverageInfo.partiallyCoveredLineCount;

    classCoverageInfo.totalMethodCount += toplevelClassCoverageInfo.totalMethodCount;
    classCoverageInfo.coveredMethodCount += toplevelClassCoverageInfo.coveredMethodCount;
    if (toplevelClassCoverageInfo.coveredMethodCount > 0) {
      classCoverageInfo.coveredClassCount++;
    }
  }

  private static ClassCoverageInfo getOrCreateClassCoverageInfo(final Map<String, ClassCoverageInfo> toplevelClassCoverage,
                                                                final String sourceToplevelFQName) {
    ClassCoverageInfo toplevelClassCoverageInfo = toplevelClassCoverage.get(sourceToplevelFQName);
    if (toplevelClassCoverageInfo == null) {
      toplevelClassCoverageInfo = new ClassCoverageInfo();
      toplevelClassCoverage.put(sourceToplevelFQName, toplevelClassCoverageInfo);
    } else {
      toplevelClassCoverageInfo.totalClassCount++;
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
  private boolean collectNonCoveredClassInfo(final File classFile,
                                             final ClassCoverageInfo classCoverageInfo,
                                             final PackageCoverageInfo packageCoverageInfo) {
    final byte[] content = myCoverageManager.doInReadActionIfProjectOpen(new Computable<byte[]>() {
      public byte[] compute() {
        try {
          return FileUtil.loadFileBytes(classFile);
        }
        catch (IOException e) {
          return null;
        }
      }
    });
    final CoverageSuitesBundle coverageSuite = CoverageDataManager.getInstance(myProject).getCurrentSuitesBundle();
    if (coverageSuite == null) return false;
    return SourceLineCounterUtil
      .collectNonCoveredClassInfo(classCoverageInfo, packageCoverageInfo, content, coverageSuite.isTracingEnabled());
  }
}
