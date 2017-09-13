/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author ven
 */
public class PackageAnnotator {

  private static final Logger LOG = Logger.getInstance(PackageAnnotator.class);
  private static final String DEFAULT_CONSTRUCTOR_NAME_SIGNATURE = "<init>()V";

  private final PsiPackage myPackage;
  private final Project myProject;
  private final PsiManager myManager;
  private final CoverageDataManager myCoverageManager;
  private final boolean myIgnoreEmptyPrivateConstructors;

  public PackageAnnotator(final PsiPackage aPackage) {
    myPackage = aPackage;
    myProject = myPackage.getProject();
    myManager = PsiManager.getInstance(myProject);
    myCoverageManager = CoverageDataManager.getInstance(myProject);
    myIgnoreEmptyPrivateConstructors = JavaCoverageOptionsProvider.getInstance(myProject).ignoreEmptyPrivateConstructors();
  }

  public interface Annotator {
    void annotateSourceDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module);

    void annotateTestDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module);

    void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo);
    
    void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo, boolean flatten);

    void annotateClass(String classQualifiedName, ClassCoverageInfo classCoverageInfo);
  }

  public static abstract class SummaryCoverageInfo {
    public int totalClassCount;
    public int coveredClassCount;

    public int totalMethodCount;
    public int coveredMethodCount;

    public int totalLineCount;

    public abstract int getCoveredLineCount();
  }

  public static class ClassCoverageInfo extends SummaryCoverageInfo {
    public int fullyCoveredLineCount;
    public int partiallyCoveredLineCount;

    public ClassCoverageInfo() {
      totalClassCount = 1;
    }

    @Override
    public int getCoveredLineCount() {
      return fullyCoveredLineCount + partiallyCoveredLineCount;
    }
  }

  public static class PackageCoverageInfo extends SummaryCoverageInfo {
    public int coveredLineCount;

    @Override
    public int getCoveredLineCount() {
      return coveredLineCount;
    }

    public void append(SummaryCoverageInfo info) {
      totalClassCount += info.totalClassCount;
      totalLineCount += info.totalLineCount;
      coveredClassCount += info.coveredClassCount;
      coveredLineCount += info.getCoveredLineCount();
      coveredMethodCount += info.coveredMethodCount;
      totalMethodCount += info.totalMethodCount;
    }
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
      if (coverageSuite instanceof JavaCoverageSuite && ((JavaCoverageSuite)coverageSuite).isPackageFiltered(qualifiedName)) {
        filtered = true;
        break;
      }

    }
    if (!filtered) return;

    final GlobalSearchScope scope = suite.getSearchScope(myProject);
    final Module[] modules = myCoverageManager.doInReadActionIfProjectOpen(() -> ModuleManager.getInstance(myProject).getModules());

    if (modules == null) return;

    Map<String, PackageCoverageInfo> packageCoverageMap = new HashMap<>();
    Map<String, PackageCoverageInfo> flattenPackageCoverageMap = new HashMap<>();
    for (final Module module : modules) {
      if (!scope.isSearchInModuleContent(module)) continue;
      final String rootPackageVMName = qualifiedName.replaceAll("\\.", "/");
      final VirtualFile[] productionRoots = myCoverageManager.doInReadActionIfProjectOpen(
        () -> OrderEnumerator.orderEntries(module)
          .withoutSdk()
          .withoutLibraries()
          .withoutDepModules()
          .productionOnly()
          .classes()
          .getRoots());
      final Set<VirtualFile> productionRootsSet = new SmartHashSet<>();

      if (productionRoots != null) {
        for (VirtualFile output : productionRoots) {
          productionRootsSet.add(output);
          File outputRoot = findRelativeFile(rootPackageVMName, output);
          if (outputRoot.exists()) {
            collectCoverageInformation(outputRoot, packageCoverageMap, flattenPackageCoverageMap, data, rootPackageVMName, annotator,
                                       module,
                                       suite, false);
          }
        }
      }

      if (suite.isTrackTestFolders()) {
        final VirtualFile[] allRoots = myCoverageManager.doInReadActionIfProjectOpen(
          () -> OrderEnumerator.orderEntries(module)
            .withoutSdk()
            .withoutLibraries()
            .withoutDepModules()
            .classes()
            .getRoots());

        if (allRoots != null) {
          for (VirtualFile root : allRoots) {
            if (productionRootsSet.contains(root)) continue;
            final File outputRoot = findRelativeFile(rootPackageVMName, root);
            if (outputRoot.exists()) {
              collectCoverageInformation(outputRoot, packageCoverageMap, flattenPackageCoverageMap, data, rootPackageVMName, annotator,
                                         module,
                                         suite, true);
            }
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
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
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
          Map<String, ClassCoverageInfo> toplevelClassCoverage = new HashMap<>();
          final File[] files = packageRoot.listFiles();
          if (files != null) {
            for (File child : files) {
              if (isClassFile(child)) {
                final String childName = getClassName(child);
                final String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
                final String toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName);
                if (toplevelClassSrcFQName.equals(qualifiedName)) {
                  collectClassCoverageInformation(child, psiClass, new PackageCoverageInfo(), data, toplevelClassCoverage, classFqVMName.replace("/", "."), toplevelClassSrcFQName);
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
                                                       final CoverageSuitesBundle bundle,
                                                       final boolean isTestHierarchy) {
    final List<DirCoverageInfo> dirs = new ArrayList<>();
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
    final PackageCoverageInfo classWithoutSourceCoverageInfo = new PackageCoverageInfo();

    final File[] children = packageOutputRoot.listFiles();

    if (children == null) return null;

    Map<String, ClassCoverageInfo> toplevelClassCoverage = new HashMap<>();
    for (File child : children) {
      if (child.isDirectory()) {
        final String childName = child.getName();
        final String childPackageVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
        final DirCoverageInfo[] childCoverageInfo =
          collectCoverageInformation(child, packageCoverageMap, flattenPackageCoverageMap, projectInfo, childPackageVMName, annotator, module,
                                     bundle, isTestHierarchy);
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
          final Ref<VirtualFile> containingFileRef = new Ref<>();
          final Ref<PsiClass> psiClassRef = new Ref<>();
          final CoverageSuitesBundle suitesBundle = myCoverageManager.getCurrentSuitesBundle();
          if (suitesBundle == null) continue;
          final Boolean isInSource = DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
            if (myProject.isDisposed()) return null;
            final PsiClass aClass =
              JavaPsiFacade.getInstance(myManager.getProject()).findClass(toplevelClassSrcFQName, GlobalSearchScope.moduleScope(module));
            if (aClass == null || !aClass.isValid()) return Boolean.FALSE;
            psiClassRef.set(aClass);
            containingFileRef.set(PsiUtilCore.getVirtualFile(aClass.getNavigationElement()));
            if (containingFileRef.isNull()) {
              LOG.info("No virtual file found for: " + aClass);
              return null;
            }
            final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
            return fileIndex.isUnderSourceRootOfType(containingFileRef.get(), JavaModuleSourceRootTypes.SOURCES)
                   && (bundle.isTrackTestFolders() || !fileIndex.isInTestSourceContent(containingFileRef.get()));
          });
          PackageCoverageInfo coverageInfoForClass = null;
          String classCoverageKey = classFqVMName.replace('/', '.');
          boolean ignoreClass = false;
          boolean keepWithoutSource = false;
          for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
            if (extension.ignoreCoverageForClass(suitesBundle, child)) {
              ignoreClass = true;
              break;
            }
            if (extension.keepCoverageInfoForClassWithoutSource(suitesBundle, child)) {
              keepWithoutSource = true;
            }
          }
          if (!ignoreClass) {
            for (CoverageSuite suite : bundle.getSuites()) {
              if (suite instanceof JavaCoverageSuite &&
                  ((JavaCoverageSuite)suite).isClassFiltered(classCoverageKey, ((JavaCoverageSuite)suite).getExcludedClassNames())) {
                ignoreClass = true;
                break;
              }
            }
          }
          if (ignoreClass) {
            continue;
          }

          if (isInSource != null && isInSource.booleanValue()) {
            for (DirCoverageInfo dirCoverageInfo : dirs) {
              if (dirCoverageInfo.sourceRoot != null && VfsUtilCore.isAncestor(dirCoverageInfo.sourceRoot, containingFileRef.get(), false)) {
                coverageInfoForClass = dirCoverageInfo;
                classCoverageKey = toplevelClassSrcFQName;
                break;
              }
            }
          }
          if (coverageInfoForClass == null && keepWithoutSource) {
            coverageInfoForClass = classWithoutSourceCoverageInfo;
          }
          if (coverageInfoForClass != null) {
            collectClassCoverageInformation(child, psiClassRef.get(), coverageInfoForClass, projectInfo, toplevelClassCoverage,
                                            classFqVMName.replace("/", "."), classCoverageKey);
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
      flattenPackageCoverageInfo.append(coverageInfo);
    }
    
    PackageCoverageInfo packageCoverageInfo = getOrCreateCoverageInfo(packageCoverageMap, packageVMName);
    for (DirCoverageInfo dir : dirs) {
      packageCoverageInfo.append(dir);

      if (isTestHierarchy) {
        annotator.annotateTestDirectory(dir.sourceRoot, dir, module);
      }
      else {
        annotator.annotateSourceDirectory(dir.sourceRoot, dir, module);
      }
    }
    packageCoverageInfo.append(classWithoutSourceCoverageInfo);

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

  private void collectClassCoverageInformation(final File classFile,
                                               @Nullable final PsiClass psiClass, final PackageCoverageInfo packageCoverageInfo,
                                               final ProjectData projectInfo,
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
          touchedClass = true;
        }

        if (myIgnoreEmptyPrivateConstructors && isGeneratedDefaultConstructor(psiClass, (String)nameAndSig)) {
          continue;
        }

        if (covered != LineCoverage.NONE) {
          toplevelClassCoverageInfo.coveredMethodCount++;
        }
        toplevelClassCoverageInfo.totalMethodCount++;
      }
      if (!methodSigs.isEmpty()) {
        if (touchedClass) {
          packageCoverageInfo.coveredClassCount++;
        }
        packageCoverageInfo.totalClassCount++;

        packageCoverageInfo.coveredLineCount += toplevelClassCoverageInfo.fullyCoveredLineCount;
        packageCoverageInfo.coveredLineCount += toplevelClassCoverageInfo.partiallyCoveredLineCount;
        packageCoverageInfo.coveredMethodCount += toplevelClassCoverageInfo.coveredMethodCount;
        packageCoverageInfo.totalMethodCount += toplevelClassCoverageInfo.totalMethodCount;
      }
      else {
        LOG.debug("Did not find any method signatures in " + classFile.getName());
        return;
      }
    }
    else {
      if (!collectNonCoveredClassInfo(classFile, psiClass, toplevelClassCoverageInfo, packageCoverageInfo)) {
        LOG.debug("Did not collect non-covered class info for " + classFile.getName());
        return;
      }
    }

    ClassCoverageInfo classCoverageInfo = getOrCreateClassCoverageInfo(toplevelClassCoverage, toplevelClassSrcFQName);
    LOG.debug("Adding coverage of " + classFile.getName() + " to top-level class " + toplevelClassSrcFQName);
    classCoverageInfo.totalLineCount += toplevelClassCoverageInfo.totalLineCount;
    classCoverageInfo.fullyCoveredLineCount += toplevelClassCoverageInfo.fullyCoveredLineCount;
    classCoverageInfo.partiallyCoveredLineCount += toplevelClassCoverageInfo.partiallyCoveredLineCount;

    classCoverageInfo.totalMethodCount += toplevelClassCoverageInfo.totalMethodCount;
    classCoverageInfo.coveredMethodCount += toplevelClassCoverageInfo.coveredMethodCount;
    if (toplevelClassCoverageInfo.getCoveredLineCount() > 0) {
      classCoverageInfo.coveredClassCount++;
    }
  }

  /**
   * Checks if the method is a default constructor generated by the compiler. Such constructors are not marked as synthetic
   * in the bytecode, so we need to look at the PSI to see if the class defines such a constructor.
   */
  public static boolean isGeneratedDefaultConstructor(@Nullable final PsiClass aClass, String nameAndSig) {
    if (aClass == null) {
      return false;
    }
    if (DEFAULT_CONSTRUCTOR_NAME_SIGNATURE.equals(nameAndSig)) {
      return hasGeneratedOrEmptyPrivateConstructor(aClass);
    }
    return false;
  }

  private static boolean hasGeneratedOrEmptyPrivateConstructor(@NotNull final PsiClass aClass) {
    return ReadAction.compute(() -> {
      PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 1 && constructors[0].hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiCodeBlock body = constructors[0].getBody();
        return body != null && body.getStatements().length == 0;
      }
      return constructors.length == 0;
    });
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
    classFQVMName = StringUtil.trimStart(classFQVMName, "/");
    return classFQVMName.replaceAll("/", ".");
  }

  /**
   * Return true if there is executable code in the class
   */
  private boolean collectNonCoveredClassInfo(final File classFile,
                                             @Nullable PsiClass psiClass,
                                             final ClassCoverageInfo classCoverageInfo,
                                             final PackageCoverageInfo packageCoverageInfo) {
    final byte[] content = myCoverageManager.doInReadActionIfProjectOpen(() -> {
      try {
        return FileUtil.loadFileBytes(classFile);
      }
      catch (IOException e) {
        return null;
      }
    });
    final CoverageSuitesBundle coverageSuite = CoverageDataManager.getInstance(myProject).getCurrentSuitesBundle();
    if (coverageSuite == null) return false;
    return SourceLineCounterUtil
      .collectNonCoveredClassInfo(classCoverageInfo, packageCoverageInfo, content, coverageSuite.isTracingEnabled(),
                                  myIgnoreEmptyPrivateConstructors ? description -> !isGeneratedDefaultConstructor(psiClass, description) : Condition.TRUE);
  }
}
