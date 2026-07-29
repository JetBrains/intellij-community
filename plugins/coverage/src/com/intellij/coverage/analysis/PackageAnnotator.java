// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.IDEACoverageRunner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.BranchData;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.UnloadedUtil;
import com.intellij.rt.coverage.util.ClassNameUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

@ApiStatus.Internal
public final class PackageAnnotator implements Closeable {
  private final CoverageSuitesBundle mySuite;
  private final Project myProject;
  private final ProjectData myProjectData;
  private final Map<String, ZipFile> myArchiveZipCache = new ConcurrentHashMap<>();
  private ProjectData myUnloadedClassesProjectData;

  public PackageAnnotator(CoverageSuitesBundle suite,
                          Project project,
                          ProjectData projectData) {
    mySuite = suite;
    myProject = project;
    myProjectData = projectData;
    IDEACoverageRunner.setExcludeAnnotations(project, myProjectData);
  }

  private synchronized ProjectData getUnloadedClassesProjectData() {
    if (myUnloadedClassesProjectData == null) {
      myUnloadedClassesProjectData = new ProjectData();
      IDEACoverageRunner.setExcludeAnnotations(myProject, myUnloadedClassesProjectData);
    }
    return myUnloadedClassesProjectData;
  }

  @Override
  public void close() {
    for (ZipFile zipFile : myArchiveZipCache.values()) {
      try {
        zipFile.close();
      }
      catch (IOException ignored) {
      }
    }
    myArchiveZipCache.clear();
  }

  /**
   * Collect coverage for classes with the same top level name.
   *
   * @param children      name - file pairs, where file is optional (could be null),
   *                      when file is null, unloaded class analysis is skipped
   * @param packageVMName common package name in internal VM format
   */
  public @NotNull ClassCoverageInfo visitFiles(Map<String, @Nullable Path> children, String packageVMName) {
    var topLevelClassCoverageInfo = new PackageAnnotator.ClassCoverageInfo();
    for (Map.Entry<String, Path> e : children.entrySet()) {
      Path file = e.getValue();
      String simpleName = e.getKey();
      String classFqName = AnalysisUtils.internalNameToFqn(AnalysisUtils.buildVMName(packageVMName, simpleName));
      var info = collectClassCoverageInformation(file, classFqName);
      if (info == null) continue;
      topLevelClassCoverageInfo.append(info);
    }
    return topLevelClassCoverageInfo;
  }

  public @Nullable String getSourceFileName(@NotNull Map<String, @Nullable Path> children, @NotNull String packageVMName) {
    var projects = new ProjectData[]{myProjectData, getUnloadedClassesProjectData()};
    for (String simpleName : children.keySet()) {
      String classFqName = AnalysisUtils.internalNameToFqn(AnalysisUtils.buildVMName(packageVMName, simpleName));
      for (ProjectData projectData : projects) {
        if (projectData == null) continue;
        ClassData classData = projectData.getClassData(classFqName);
        if (classData == null) continue;
        String sourceFileName = classData.getSource();
        if (sourceFileName != null) return sourceFileName;
      }
    }

    for (Path classFile : children.values()) {
      if (classFile == null) continue;
      String sourceFileName = readSourceFileName(classFile);
      if (sourceFileName != null) return sourceFileName;
    }
    return null;
  }

  private @Nullable String readSourceFileName(@NotNull Path classFile) {
    byte[] bytes = loadClassBytes(classFile);
    if (bytes == null) return null;

    String[] sourceFileName = {null};
    new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
      @Override
      public void visitSource(String source, String debug) {
        sourceFileName[0] = source;
      }
    }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
    return sourceFileName[0];
  }

  private @Nullable PackageAnnotator.ClassCoverageInfo collectClassCoverageInformation(@Nullable Path classFile, String className) {
    ClassData classData = myProjectData.getClassData(className);
    final boolean classExists = classData != null && classData.getLines() != null;
    if (classFile != null && (!classExists || !classData.isFullyAnalysed())) {
      var bytes = loadClassBytes(classFile);
      if (bytes != null) {
        ClassData fullClassData = collectNonCoveredClassInfo(className, bytes, getUnloadedClassesProjectData());
        if (fullClassData != null) {
          if (classData == null) {
            classData = fullClassData;
          }
          else {
            classData.merge(fullClassData);
          }
        }
      }
    }

    return getSummaryInfo(classData);
  }

  private static @Nullable ClassCoverageInfo getSummaryInfo(@Nullable ClassData classData) {
    if (classData == null || classData.getLines() == null) return null;
    ClassCoverageInfo info = new ClassCoverageInfo();
    Set<String> coveredMethods = new HashSet<>();
    final Object[] lines = classData.getLines();
    for (Object l : lines) {
      if (l instanceof LineData lineData) {
        int lineStatus = lineData.getStatus();
        if (lineStatus != LineCoverage.NONE) {
          coveredMethods.add(lineData.getMethodSignature());
        }

        if (lineStatus == LineCoverage.FULL) {
          info.fullyCoveredLineCount++;
        }
        else if (lineStatus == LineCoverage.PARTIAL) {
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

    final Collection<String> methodSigs = classData.getMethodSigs();
    for (final String nameAndSig : methodSigs) {
      if (coveredMethods.contains(nameAndSig)) {
        info.coveredMethodCount++;
      }
      info.totalMethodCount++;
    }

    if (!methodSigs.isEmpty()) {
      info.totalClassCount = 1;
      if (info.getCoveredLineCount() > 0) {
        info.coveredClassCount = 1;
      }
    }
    return info;
  }

  public @Nullable ClassData collectNonCoveredClassInfo(final @NotNull Path classFile, @NotNull ProjectData projectData) {
    var bytes = loadClassBytes(classFile);
    if (bytes == null) return null;
    String className = ClassNameUtil.convertToFQName(new ClassReader(bytes).getClassName());
    return collectNonCoveredClassInfo(className, bytes, projectData);
  }

  private @Nullable ClassData collectNonCoveredClassInfo(@NotNull String className,
                                                         byte @NotNull [] bytes,
                                                         @NotNull ProjectData projectData) {
    UnloadedUtil.appendUnloadedClass(projectData, className, bytes, mySuite.isBranchCoverage());
    return projectData.getClassData(className);
  }

  private byte @Nullable [] loadClassBytes(@NotNull Path classFile) {
    AnalysisUtils.ArchiveEntryPath archiveEntryPath = AnalysisUtils.splitArchiveEntryPath(classFile);
    if (archiveEntryPath != null) {
      ZipFile zip = getOrCreateArchive(archiveEntryPath.archivePath());
      return zip == null ? null : AnalysisUtils.loadClassBytes(zip, archiveEntryPath.entryPath());
    }
    return AnalysisUtils.loadClassBytes(classFile);
  }

  private @Nullable ZipFile getOrCreateArchive(@NotNull String archivePath) {
    ZipFile cached = myArchiveZipCache.get(archivePath);
    if (cached != null) return cached;

    try {
      ZipFile opened = new ZipFile(archivePath);
      ZipFile existing = myArchiveZipCache.putIfAbsent(archivePath, opened);
      if (existing != null) {
        try {
          opened.close();
        }
        catch (IOException ignored) {
        }
        return existing;
      }
      return opened;
    }
    catch (IOException ignored) {
      return null;
    }
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
    public final VirtualFile sourceRoot;

    public DirCoverageInfo(VirtualFile sourceRoot) {
      this.sourceRoot = sourceRoot;
    }
  }
}
