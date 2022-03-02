// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.rt.coverage.data.*;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.coverage.org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ven
 */
public final class PackageAnnotator {
  private static final Logger LOG = Logger.getInstance(PackageAnnotator.class);
  private static final @NonNls String DEFAULT_CONSTRUCTOR_NAME_SIGNATURE = "<init>()V";

  private final CoverageSuitesBundle mySuite;
  private final Project myProject;
  private final ProjectData myProjectData;
  private final JavaCoverageClassesAnnotator myAnnotator;
  private final boolean myIgnoreEmptyPrivateConstructors;
  private final boolean myIgnoreImplicitConstructor;
  private final ProjectData myUnloadedClassesProjectData = new ProjectData();

  public PackageAnnotator(CoverageSuitesBundle suite,
                          Project project,
                          ProjectData projectData,
                          JavaCoverageClassesAnnotator annotator,
                          boolean ignoreEmptyPrivateConstructors,
                          boolean ignoreImplicitConstructor) {
    mySuite = suite;
    myProject = project;
    myProjectData = projectData;
    myAnnotator = annotator;
    myIgnoreEmptyPrivateConstructors = ignoreEmptyPrivateConstructors;
    myIgnoreImplicitConstructor = ignoreImplicitConstructor;
  }

  public interface Annotator {
    default void annotateSourceDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module) {}

    default void annotateTestDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module) {}

    default void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo) {}

    default void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo, boolean flatten) {}

    default void annotateClass(String classQualifiedName, ClassCoverageInfo classCoverageInfo) {}
  }

  public abstract static class SummaryCoverageInfo {
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

    public void append(ClassCoverageInfo info) {
      totalClassCount += info.totalClassCount;
      coveredClassCount += info.coveredClassCount;
      totalLineCount += info.totalLineCount;
      fullyCoveredLineCount += info.fullyCoveredLineCount;
      partiallyCoveredLineCount += info.partiallyCoveredLineCount;
      totalMethodCount += info.totalMethodCount;
      coveredMethodCount += info.coveredMethodCount;
      totalBranchCount += info.totalBranchCount;
      coveredBranchCount += info.coveredBranchCount;
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

  public static class AtomicPackageCoverageInfo {
    private final AtomicInteger myTotalClassCount = new AtomicInteger(0);
    private final AtomicInteger myCoveredClassCount = new AtomicInteger(0);
    private final AtomicInteger myTotalMethodCount = new AtomicInteger(0);
    private final AtomicInteger myCoveredMethodCount = new AtomicInteger(0);
    private final AtomicInteger myTotalLineCount = new AtomicInteger(0);
    private final AtomicInteger myCoveredLineCount = new AtomicInteger(0);
    private final AtomicInteger myTotalBranchCount = new AtomicInteger(0);
    private final AtomicInteger myCoveredBranchCount = new AtomicInteger(0);

    public void append(SummaryCoverageInfo info) {
      myTotalClassCount.addAndGet(info.totalClassCount);
      myCoveredClassCount.addAndGet(info.coveredClassCount);
      myTotalMethodCount.addAndGet(info.totalMethodCount);
      myCoveredMethodCount.addAndGet(info.coveredMethodCount);
      myTotalLineCount.addAndGet(info.totalLineCount);
      myCoveredLineCount.addAndGet(info.getCoveredLineCount());
      myTotalBranchCount.addAndGet(info.totalBranchCount);
      myCoveredBranchCount.addAndGet(info.coveredBranchCount);
    }

    public PackageCoverageInfo toPackageCoverageInfo() {
      final PackageCoverageInfo info = new PackageCoverageInfo();
      info.totalClassCount = myTotalClassCount.get();
      info.coveredClassCount = myCoveredClassCount.get();
      info.totalMethodCount = myTotalMethodCount.get();
      info.coveredMethodCount = myCoveredMethodCount.get();
      info.totalLineCount = myTotalLineCount.get();
      info.coveredLineCount = myCoveredLineCount.get();
      info.totalBranchCount = myTotalBranchCount.get();
      info.coveredBranchCount = myCoveredBranchCount.get();
      return info;
    }
  }

  public static @NotNull File findRelativeFile(@NotNull String rootPackageVMName, VirtualFile output) {
    File outputRoot = VfsUtilCore.virtualToIoFile(output);
    outputRoot = rootPackageVMName.length() > 0 ? new File(outputRoot, FileUtil.toSystemDependentName(rootPackageVMName)) : outputRoot;
    return outputRoot;
  }

  public PackageAnnotator.ClassCoverageInfo visitClass(PsiClass psiClass) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module != null) {
      final boolean isInTests = ProjectRootManager.getInstance(module.getProject()).getFileIndex()
        .isInTestSourceContent(psiClass.getContainingFile().getVirtualFile());
      final CompilerModuleExtension moduleExtension = CompilerModuleExtension.getInstance(module);
      if (moduleExtension == null) return null;
      final VirtualFile outputPath = isInTests ? moduleExtension.getCompilerOutputPathForTests() : moduleExtension.getCompilerOutputPath();

      if (outputPath != null) {
        final String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) return null;
        final String packageVMName = StringUtil.getPackageName(qualifiedName).replace('.', '/');
        final File packageRoot = findRelativeFile(packageVMName, outputPath);
        if (packageRoot.exists()) {
          final File[] files = packageRoot.listFiles();
          if (files != null) {
            final PackageAnnotator.ClassCoverageInfo result = new PackageAnnotator.ClassCoverageInfo();
            for (File child : files) {
              if (isClassFile(child)) {
                final String childName = getClassName(child);
                final String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
                final String toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName);
                if (toplevelClassSrcFQName.equals(qualifiedName)) {
                  final String className = classFqVMName.replace('/', '.');
                  final PackageAnnotator.ClassCoverageInfo coverageInfo = collectClassCoverageInformation(child, psiClass, className);
                  if (coverageInfo != null) {
                    result.append(coverageInfo);
                  }
                }
              }
            }
            return result;
          }
        }
      }
    }
    return null;
  }

  public void visitFiles(final String toplevelClassSrcFQName,
                         final List<File> files,
                         final String packageVMName,
                         final GlobalSearchScope scope) {
    final Ref<VirtualFile> containingFileRef = new Ref<>();
    final Ref<PsiClass> psiClassRef = new Ref<>();
    final Boolean isInSource = DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
      if (myProject.isDisposed()) return null;
      final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(toplevelClassSrcFQName, scope);
      if (aClass == null || !aClass.isValid()) return Boolean.FALSE;
      psiClassRef.set(aClass);
      PsiElement element = aClass.getNavigationElement();
      containingFileRef.set(PsiUtilCore.getVirtualFile(element));
      if (containingFileRef.isNull()) {
        LOG.info("No virtual file found for: " + aClass);
        return null;
      }
      return mySuite.getCoverageEngine().acceptedByFilters(element.getContainingFile(), mySuite);
    });

    if (isInSource == null || !isInSource.booleanValue()) return;
    VirtualFile virtualFile = containingFileRef.get();
    PackageAnnotator.ClassCoverageInfo topLevelClassCoverageInfo = new PackageAnnotator.ClassCoverageInfo();
    VirtualFile parent = null;
    for (File file : files) {
      if (virtualFile == null && !ContainerUtil.exists(JavaCoverageEngineExtension.EP_NAME.getExtensions(),
                                                       extension -> extension.keepCoverageInfoForClassWithoutSource(mySuite, file))) {
        return;
      }
      parent = virtualFile == null ? null : virtualFile.getParent();
      final String childName = getClassName(file);
      final String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
      final PackageAnnotator.ClassCoverageInfo
        info = collectClassCoverageInformation(file, psiClassRef.get(), classFqVMName.replace('/', '.'));
      if (info == null) continue;
      topLevelClassCoverageInfo.append(info);
    }
    myAnnotator.annotateClass(toplevelClassSrcFQName, topLevelClassCoverageInfo, packageVMName, parent);
  }

  @Nullable
  public PackageAnnotator.ClassCoverageInfo collectClassCoverageInformation(final File classFile,
                                                                            @Nullable final PsiClass psiClass,
                                                                            final String className) {
    final PackageAnnotator.ClassCoverageInfo info = new PackageAnnotator.ClassCoverageInfo();
    ClassData classData = myProjectData.getClassData(className);
    if (classData == null || classData.getLines() == null) {
      classData = collectNonCoveredClassInfo(classFile, className, myUnloadedClassesProjectData);
    }

    if (classData != null && classData.getLines() != null) {
      boolean isDefaultConstructorGenerated = false;
      final Collection<String> methodSigs = classData.getMethodSigs();
      for (final String nameAndSig : methodSigs) {
        if (myIgnoreImplicitConstructor && isGeneratedDefaultConstructor(psiClass, nameAndSig)) {
          isDefaultConstructorGenerated = true;
          continue;
        }

        if (classData.getStatus(nameAndSig) != LineCoverage.NONE) {
          info.coveredMethodCount++;
        }
        info.totalMethodCount++;
      }

      final Object[] lines = classData.getLines();
      for (Object l : lines) {
        if (l instanceof LineData) {
          final LineData lineData = (LineData)l;
          if (lineData.getStatus() == LineCoverage.FULL) {
            info.fullyCoveredLineCount++;
          }
          else if (lineData.getStatus() == LineCoverage.PARTIAL) {
            info.partiallyCoveredLineCount++;
          }
          else if (myIgnoreImplicitConstructor &&
                   isDefaultConstructorGenerated &&
                   isDefaultConstructor(lineData.getMethodSignature())) {
            continue;
          }
          info.totalLineCount++;
          BranchData branchData = lineData.getBranchData();
          if (branchData != null) {
            info.totalBranchCount += branchData.getTotalBranches();
            info.coveredBranchCount += branchData.getCoveredBranches();
          }
        }
      }
      if (!methodSigs.isEmpty()) {
        info.totalClassCount = 1;
        if (info.getCoveredLineCount() > 0) {
          info.coveredClassCount = 1;
        }
      }
    }
    else {
      return null;
    }


    return info;
  }

  public static boolean isClassFile(@NotNull File classFile) {
    return classFile.getPath().endsWith(".class");
  }

  public static String getClassName(File classFile) {
    return StringUtil.trimEnd(classFile.getName(), ".class");
  }

  /**
   * Checks if the method is a default constructor generated by the compiler. Such constructors are not marked as synthetic
   * in the bytecode, so we need to look at the PSI to see if the class defines such a constructor.
   */
  public static boolean isGeneratedDefaultConstructor(@Nullable final PsiClass aClass, String nameAndSig) {
    if (aClass == null) return false;
    if (isDefaultConstructor(nameAndSig)) {
      return hasGeneratedConstructor(aClass);
    }
    return false;
  }

  public static boolean isDefaultConstructor(String nameAndSig) {
    return DEFAULT_CONSTRUCTOR_NAME_SIGNATURE.equals(nameAndSig);
  }

  private static boolean hasGeneratedConstructor(@NotNull final PsiClass aClass) {
    return aClass.getLanguage().isKindOf(JavaLanguage.INSTANCE) && ReadAction.compute(() -> {
      if (!aClass.isValid()) return false;
      PsiMethod[] constructors = aClass.getConstructors();
      return constructors.length == 0;
    });
  }

  public static String getSourceToplevelFQName(String classFQVMName) {
    final int index = classFQVMName.indexOf('$');
    if (index > 0) classFQVMName = classFQVMName.substring(0, index);
    classFQVMName = StringUtil.trimStart(classFQVMName, "/");
    return classFQVMName.replace('/', '.');
  }

  @Nullable
  private ClassData collectNonCoveredClassInfo(final File classFile, String className, ProjectData projectData) {
    final byte[] content;
    try {
      content = FileUtil.loadFileBytes(classFile);
    }
    catch (IOException e) {
      return null;
    }
    SaveHook.appendUnloadedClass(projectData, className, new ClassReader(content), !mySuite.isTracingEnabled(), false, myIgnoreEmptyPrivateConstructors);
    return projectData.getClassData(className);
  }
}
