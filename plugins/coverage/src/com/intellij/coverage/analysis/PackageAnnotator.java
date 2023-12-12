// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.IDEACoverageRunner;
import com.intellij.coverage.JavaCoverageEngineExtension;
import com.intellij.coverage.JavaCoverageOptionsProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.rt.coverage.data.*;
import com.intellij.rt.coverage.instrumentation.UnloadedUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.coverage.org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class PackageAnnotator {
  private static final Logger LOG = Logger.getInstance(PackageAnnotator.class);
  private static final @NonNls String DEFAULT_CONSTRUCTOR_NAME_SIGNATURE = "<init>()V";

  private final CoverageSuitesBundle mySuite;
  private final Project myProject;
  private final ProjectData myProjectData;
  private final boolean myIgnoreImplicitConstructor;
  private ProjectData myUnloadedClassesProjectData;

  public PackageAnnotator(CoverageSuitesBundle suite,
                          Project project,
                          ProjectData projectData) {
    mySuite = suite;
    myProject = project;
    myProjectData = projectData;
    IDEACoverageRunner.setExcludeAnnotations(project, myProjectData);

    JavaCoverageOptionsProvider optionsProvider = JavaCoverageOptionsProvider.getInstance(myProject);
    myIgnoreImplicitConstructor = optionsProvider.getIgnoreImplicitConstructors();
  }

  private synchronized ProjectData getUnloadedClassesProjectData() {
    if (myUnloadedClassesProjectData == null) {
      myUnloadedClassesProjectData = new ProjectData();
      IDEACoverageRunner.setExcludeAnnotations(myProject, myUnloadedClassesProjectData);
    }
    return myUnloadedClassesProjectData;
  }

  public static @NotNull File findRelativeFile(@NotNull String rootPackageVMName, File outputRoot) {
    outputRoot = !rootPackageVMName.isEmpty() ? new File(outputRoot, FileUtil.toSystemDependentName(rootPackageVMName)) : outputRoot;
    return outputRoot;
  }

  /**
   * Collect coverage for classes with the same top level name.
   * @param toplevelClassSrcFQName Top level element name
   * @param children name - file pairs, where file is optional (could be null),
   *                 when file is null, unloaded class analysis is skipped
   * @param packageVMName common package name in internal VM format
   */
  @Nullable
  public Result visitFiles(final String toplevelClassSrcFQName,
                         final Map<String, File> children,
                         final String packageVMName) {
    final Ref<VirtualFile> containingFileRef = new Ref<>();
    final Ref<PsiClass> psiClassRef = new Ref<>();
    if (myProject.isDisposed()) return null;
    final Boolean isInSource = DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
      if (myProject.isDisposed()) return null;
      final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(toplevelClassSrcFQName, mySuite.getSearchScope(myProject));
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

    if (isInSource == null || !isInSource.booleanValue()) return null;
    VirtualFile virtualFile = containingFileRef.get();
    var topLevelClassCoverageInfo = new PackageAnnotator.ClassCoverageInfo();
    VirtualFile parent = virtualFile == null ? null : virtualFile.getParent();
    for (Map.Entry<String, File> e : children.entrySet()) {
      File file = e.getValue();
      if (virtualFile == null && !ContainerUtil.exists(JavaCoverageEngineExtension.EP_NAME.getExtensions(),
                                                       extension -> extension.keepCoverageInfoForClassWithoutSource(mySuite, file))) {
        continue;
      }
      String simpleName = e.getKey();
      String classFqName = AnalysisUtils.internalNameToFqn(AnalysisUtils.buildVMName(packageVMName, simpleName));
      var info = collectClassCoverageInformation(file, psiClassRef.get(), classFqName);
      if (info == null) continue;
      topLevelClassCoverageInfo.append(info);
    }
    return new Result(topLevelClassCoverageInfo, parent);
  }

  @Nullable
  private PackageAnnotator.ClassCoverageInfo collectClassCoverageInformation(@Nullable File classFile,
                                                                             @Nullable PsiClass psiClass,
                                                                             String className) {
    ClassData classData = myProjectData.getClassData(className);
    final boolean classExists = classData != null && classData.getLines() != null;
    if (classFile != null && (!classExists || !classData.isFullyAnalysed())) {
      ClassData fullClassData = collectNonCoveredClassInfo(classFile, className, getUnloadedClassesProjectData());
      if (classData == null) {
        classData = fullClassData;
      }
      else {
        classData.merge(fullClassData);
      }
    }

    return getSummaryInfo(psiClass, classData, myIgnoreImplicitConstructor);
  }

  @Nullable
  private static ClassCoverageInfo getSummaryInfo(@Nullable PsiClass psiClass, @Nullable ClassData classData, boolean ignoreImplicitConstructor) {
    if (classData == null || classData.getLines() == null) return null;
    ClassCoverageInfo info = new ClassCoverageInfo();
    boolean isDefaultConstructorGenerated = false;
    final Collection<String> methodSigs = classData.getMethodSigs();
    for (final String nameAndSig : methodSigs) {
      if (ignoreImplicitConstructor && isGeneratedDefaultConstructor(psiClass, nameAndSig)) {
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
      if (l instanceof LineData lineData) {
        if (isDefaultConstructorGenerated &&
            isDefaultConstructor(lineData.getMethodSignature())) {
          continue;
        }
        else if (lineData.getStatus() == LineCoverage.FULL) {
          info.fullyCoveredLineCount++;
        }
        else if (lineData.getStatus() == LineCoverage.PARTIAL) {
          info.partiallyCoveredLineCount++;
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
    return info;
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

  private static boolean isDefaultConstructor(String nameAndSig) {
    return DEFAULT_CONSTRUCTOR_NAME_SIGNATURE.equals(nameAndSig);
  }

  private static boolean hasGeneratedConstructor(@NotNull final PsiClass aClass) {
    return aClass.getLanguage().isKindOf(JavaLanguage.INSTANCE) && ReadAction.compute(() -> {
      if (!aClass.isValid()) return false;
      PsiMethod[] constructors = aClass.getConstructors();
      return constructors.length == 0;
    });
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
    UnloadedUtil.appendUnloadedClass(projectData, className, new ClassReader(content), mySuite.isBranchCoverage(), false);
    return projectData.getClassData(className);
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

    public boolean isFullyCovered() {
      return totalBranchCount == coveredBranchCount
             && totalLineCount == getCoveredLineCount()
             && totalMethodCount == coveredMethodCount
             && totalClassCount == coveredClassCount;
    }
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
    final public VirtualFile sourceRoot;

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

  public static class Result {
    public final ClassCoverageInfo info;
    public final VirtualFile directory;

    public Result(ClassCoverageInfo info, VirtualFile directory) {
      this.info = info;
      this.directory = directory;
    }
  }
}
