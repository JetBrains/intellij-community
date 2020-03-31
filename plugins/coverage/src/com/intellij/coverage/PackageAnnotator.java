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
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.rt.coverage.data.*;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  private final CoverageDataManager myCoverageManager;
  private final boolean myIgnoreEmptyPrivateConstructors;
  private final boolean myIgnoreImplicitConstructor;

  public PackageAnnotator(final PsiPackage aPackage) {
    myPackage = aPackage;
    myProject = myPackage.getProject();
    myCoverageManager = CoverageDataManager.getInstance(myProject);
    JavaCoverageOptionsProvider optionsProvider = JavaCoverageOptionsProvider.getInstance(myProject);
    myIgnoreEmptyPrivateConstructors = optionsProvider.ignoreEmptyPrivateConstructors();
    myIgnoreImplicitConstructor = optionsProvider.ignoreImplicitConstructors();
  }

  public interface Annotator {
    default void annotateSourceDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module) {}

    default void annotateTestDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module) {}

    default void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo) {}

    default void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo, boolean flatten) {}

    default void annotateClass(String classQualifiedName, ClassCoverageInfo classCoverageInfo) {}
  }

  public static abstract class SummaryCoverageInfo {
    public int totalClassCount;
    public int coveredClassCount;

    public int totalMethodCount;
    public int coveredMethodCount;

    public int totalLineCount;

    public abstract int getCoveredLineCount();

    public int coveredBranchCount;
    public int totalBranchCount;
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
      totalBranchCount += info.totalBranchCount;
      coveredBranchCount += info.coveredBranchCount;
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
        Map<VirtualFile, DirCoverageInfo> dirsMap = new HashMap<>();
        RootWalker rootWalker = new RootWalker(packageCoverageMap, dirsMap, flattenPackageCoverageMap, annotator, data, suite,
                                               GlobalSearchScope.moduleScope(module));
        for (VirtualFile output : productionRoots) {
          productionRootsSet.add(output);
          File outputRoot = findRelativeFile(rootPackageVMName, output);
          if (outputRoot.exists()) {
            rootWalker.collectCoverageDataInRoot(outputRoot, rootPackageVMName, prepareRoots(module, rootPackageVMName, false));
          }

          for (DirCoverageInfo dir : dirsMap.values()) {
            annotator.annotateSourceDirectory(dir.sourceRoot, dir, module);
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
          Map<VirtualFile, DirCoverageInfo> dirsMap = new HashMap<>();
          RootWalker rootWalker = new RootWalker(packageCoverageMap, dirsMap, flattenPackageCoverageMap, annotator, data, suite,
                                                 GlobalSearchScope.moduleScope(module));
          for (VirtualFile root : allRoots) {
            if (productionRootsSet.contains(root)) continue;
            final File outputRoot = findRelativeFile(rootPackageVMName, root);
            if (outputRoot.exists()) {
              rootWalker.collectCoverageDataInRoot(outputRoot, rootPackageVMName, prepareRoots(module, rootPackageVMName, true));
            }
          }
          for (DirCoverageInfo dir : dirsMap.values()) {
            annotator.annotateTestDirectory(dir.sourceRoot, dir, module);
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

  private static VirtualFile[] prepareRoots(Module module, String rootPackageVMName, boolean isTestHierarchy) {
    List<VirtualFile> result = new ArrayList<>();
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      for (SourceFolder folder : contentEntry
        .getSourceFolders(isTestHierarchy ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE)) {
        final VirtualFile file = folder.getFile();
        if (file == null) continue;
        final String prefix = folder.getPackagePrefix().replaceAll("\\.", "/");
        final VirtualFile relativeSrcRoot = file.findFileByRelativePath(StringUtil.trimStart(rootPackageVMName, prefix));
        result.add(relativeSrcRoot);
      }
    }
    return result.toArray(VirtualFile.EMPTY_ARRAY);
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

  private class RootWalker {
    private final Map<String, PackageCoverageInfo> packageCoverageMap;
    private final Map<VirtualFile, DirCoverageInfo> dirsCoverageMap;
    private final Map<String, PackageCoverageInfo> flattenPackageCoverageMap;
    private final Annotator annotator;
    private final ProjectData projectInfo;
    private final CoverageSuitesBundle bundle;
    private final GlobalSearchScope globalSearchScope;

    private RootWalker(Map<String, PackageCoverageInfo> packageCoverageMap,
                       Map<VirtualFile, DirCoverageInfo> dirsCoverageMap,
                       Map<String, PackageCoverageInfo> flattenPackageCoverageMap,
                       Annotator annotator,
                       ProjectData projectInfo,
                       CoverageSuitesBundle bundle,
                       GlobalSearchScope globalSearchScope) {
      this.packageCoverageMap = packageCoverageMap;
      this.dirsCoverageMap = dirsCoverageMap;
      this.flattenPackageCoverageMap = flattenPackageCoverageMap;
      this.annotator = annotator;
      this.projectInfo = projectInfo;
      this.bundle = bundle;
      this.globalSearchScope = globalSearchScope;
    }

    private void collectCoverageDataInRoot(final File packageOutputRoot,
                                           final String packageVMName,
                                           final VirtualFile[] sourceRoots) {
      final File[] children = packageOutputRoot.listFiles();
      if (children == null) return;

      final PackageCoverageInfo classWithoutSourceCoverageInfo = new PackageCoverageInfo();

      Map<String, ClassCoverageInfo> toplevelClassCoverage = new HashMap<>();
      for (File child : children) {
        if (child.isDirectory()) {
          final String childName = child.getName();
          final String childPackageVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
          VirtualFile[] childSourceRoots = Arrays.stream(sourceRoots)
                                                 .map(root -> root != null ? root.findFileByRelativePath(childName) : null)
                                                 .toArray(VirtualFile[]::new);
          collectCoverageDataInRoot(child, childPackageVMName, childSourceRoots);
          for (int i = 0; i < sourceRoots.length; i++) {
            VirtualFile childRoot = childSourceRoots[i];
            PackageCoverageInfo coverageInfo = childRoot != null ? dirsCoverageMap.get(childRoot) : null;
            if (coverageInfo != null) {
              dirsCoverageMap.computeIfAbsent(sourceRoots[i], srcRoot -> new DirCoverageInfo(srcRoot))
                             .append(coverageInfo);
            }
          }
        }
        else {
          if (isClassFile(child)) {
            final String childName = getClassName(child);
            final String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
            final String toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName);

            if (ignoreClass(bundle, child, toplevelClassSrcFQName)) continue;

            final Ref<VirtualFile> containingFileRef = new Ref<>();
            final Ref<PsiClass> psiClassRef = new Ref<>();
            final Boolean isInSource = DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
              if (myProject.isDisposed()) return null;
              final PsiClass aClass =
                JavaPsiFacade.getInstance(myProject).findClass(toplevelClassSrcFQName, globalSearchScope);
              if (aClass == null || !aClass.isValid()) return Boolean.FALSE;
              psiClassRef.set(aClass);
              PsiElement element = aClass.getNavigationElement();
              containingFileRef.set(PsiUtilCore.getVirtualFile(element));
              if (containingFileRef.isNull()) {
                LOG.info("No virtual file found for: " + aClass);
                return null;
              }
              return bundle.getCoverageEngine().acceptedByFilters(element.getContainingFile(), bundle);
            });

            if (isInSource != null && isInSource.booleanValue()) {
              VirtualFile virtualFile = containingFileRef.get();
              PackageCoverageInfo coverageInfoForClass;
              if (virtualFile != null) {
                coverageInfoForClass = dirsCoverageMap.computeIfAbsent(virtualFile.getParent(), DirCoverageInfo::new);
              }
              else if (Arrays.stream(JavaCoverageEngineExtension.EP_NAME.getExtensions())
                             .anyMatch(extension -> extension.keepCoverageInfoForClassWithoutSource(bundle, child))) {
                coverageInfoForClass = classWithoutSourceCoverageInfo;
              }
              else {
                continue;
              }
              collectClassCoverageInformation(child, psiClassRef.get(), coverageInfoForClass, projectInfo, toplevelClassCoverage,
                                              classFqVMName.replace("/", "."), toplevelClassSrcFQName);
            }
          }
        }
      }

      PackageCoverageInfo flattenPackageCoverageInfo = flattenPackageCoverageMap.computeIfAbsent(packageVMName, k -> new PackageCoverageInfo());
      for (Map.Entry<String, ClassCoverageInfo> entry : toplevelClassCoverage.entrySet()) {
        final ClassCoverageInfo coverageInfo = entry.getValue();
        flattenPackageCoverageInfo.append(coverageInfo);
        annotator.annotateClass(entry.getKey(), coverageInfo);
      }

      PackageCoverageInfo packageCoverageInfo = packageCoverageMap.computeIfAbsent(packageVMName, k -> new PackageCoverageInfo());
      Arrays.stream(sourceRoots)
            .map(dirsCoverageMap::get)
            .filter(Objects::nonNull)
            .forEach(packageCoverageInfo::append);
      packageCoverageInfo.append(classWithoutSourceCoverageInfo);
    }

    private boolean ignoreClass(CoverageSuitesBundle bundle, File child, String toplevelClassSrcFQName) {
      for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
        if (extension.ignoreCoverageForClass(bundle, child)) {
          return true;
        }
      }
      for (CoverageSuite suite : bundle.getSuites()) {
        if (suite instanceof JavaCoverageSuite &&
            ((JavaCoverageSuite)suite).isClassFiltered(toplevelClassSrcFQName, ((JavaCoverageSuite)suite).getExcludedClassNames())) {
          return true;
        }
      }
      return false;
    }
  }

  private void collectClassCoverageInformation(final File classFile,
                                               @Nullable final PsiClass psiClass, 
                                               final PackageCoverageInfo packageCoverageInfo,
                                               ProjectData projectInfo,
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
          else if ((myIgnoreEmptyPrivateConstructors || myIgnoreImplicitConstructor) &&
                   isGeneratedDefaultConstructor(psiClass, lineData.getMethodSignature(), myIgnoreImplicitConstructor,
                                                 myIgnoreEmptyPrivateConstructors)) {
            continue;
          }
          toplevelClassCoverageInfo.totalLineCount++;
          packageCoverageInfo.totalLineCount++;
          BranchData branchData = lineData.getBranchData();
          if (branchData != null) {
            toplevelClassCoverageInfo.totalBranchCount += branchData.getTotalBranches();
            toplevelClassCoverageInfo.coveredBranchCount += branchData.getCoveredBranches();
          }
        }
      }
      boolean touchedClass = false;
      final Collection methodSigs = classData.getMethodSigs();
      for (final Object nameAndSig : methodSigs) {
        final int covered = classData.getStatus((String)nameAndSig);
        if (covered != LineCoverage.NONE) {
          touchedClass = true;
        }

        if ((myIgnoreEmptyPrivateConstructors || myIgnoreImplicitConstructor) &&
            isGeneratedDefaultConstructor(psiClass, (String)nameAndSig, myIgnoreImplicitConstructor, myIgnoreEmptyPrivateConstructors)) {
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
        packageCoverageInfo.coveredBranchCount += toplevelClassCoverageInfo.coveredBranchCount;
        packageCoverageInfo.totalBranchCount += toplevelClassCoverageInfo.totalBranchCount;
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

    classCoverageInfo.totalBranchCount += toplevelClassCoverageInfo.totalBranchCount;
    classCoverageInfo.coveredBranchCount += toplevelClassCoverageInfo.coveredBranchCount;
    if (toplevelClassCoverageInfo.getCoveredLineCount() > 0) {
      classCoverageInfo.coveredClassCount++;
    }
  }

  private static boolean isClassFile(File classFile) {
    return classFile.getName().endsWith(".class");
  }

  private static String getClassName(File classFile) {
    return StringUtil.trimEnd(classFile.getName(), ".class");
  }


  /**
   * Checks if the method is a default constructor generated by the compiler. Such constructors are not marked as synthetic
   * in the bytecode, so we need to look at the PSI to see if the class defines such a constructor.
   */
  public static boolean isGeneratedDefaultConstructor(@Nullable final PsiClass aClass, String nameAndSig,
                                                      boolean implicitConstructor, boolean privateEmpty) {
    if (aClass == null || !implicitConstructor && !privateEmpty) return false;
    if (DEFAULT_CONSTRUCTOR_NAME_SIGNATURE.equals(nameAndSig)) {
      return hasGeneratedOrEmptyPrivateConstructor(aClass, implicitConstructor, privateEmpty);
    }
    return false;
  }

  private static boolean hasGeneratedOrEmptyPrivateConstructor(@NotNull final PsiClass aClass,
                                                               boolean implicitConstructor,
                                                               boolean privateEmpty) {
    return ReadAction.compute(() -> {
      PsiMethod[] constructors = aClass.getConstructors();
      if (privateEmpty && constructors.length == 1 && constructors[0].hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiCodeBlock body = constructors[0].getBody();
        return body != null && body.isEmpty() &&
               Arrays.stream(aClass.getMethods()).allMatch(method -> method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC));
      }
      return implicitConstructor && constructors.length == 0;
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
                                  myIgnoreEmptyPrivateConstructors || myIgnoreImplicitConstructor 
                                  ? description -> !isGeneratedDefaultConstructor(psiClass, description, myIgnoreImplicitConstructor, myIgnoreEmptyPrivateConstructors) : Conditions.alwaysTrue());
  }
}
