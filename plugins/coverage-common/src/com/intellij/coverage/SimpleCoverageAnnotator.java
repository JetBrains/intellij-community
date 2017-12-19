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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author traff
 */
public abstract class SimpleCoverageAnnotator extends BaseCoverageAnnotator {

  private final Map<String, FileCoverageInfo> myFileCoverageInfos = new HashMap<>();
  private final Map<String, DirCoverageInfo> myTestDirCoverageInfos = new HashMap<>();
  private final Map<String, DirCoverageInfo> myDirCoverageInfos = new HashMap<>();

  public SimpleCoverageAnnotator(Project project) {
    super(project);
  }

  @Override
  public void onSuiteChosen(CoverageSuitesBundle newSuite) {
    super.onSuiteChosen(newSuite);

    myFileCoverageInfos.clear();
    myTestDirCoverageInfos.clear();
    myDirCoverageInfos.clear();
  }

  @Nullable
  protected DirCoverageInfo getDirCoverageInfo(@NotNull final PsiDirectory directory,
                                               @NotNull final CoverageSuitesBundle currentSuite) {
    final VirtualFile dir = directory.getVirtualFile();

    final boolean isInTestContent = TestSourcesFilter.isTestSources(dir, directory.getProject());
    if (!currentSuite.isTrackTestFolders() && isInTestContent) {
      return null;
    }

    final String path = normalizeFilePath(dir.getPath());

    return isInTestContent ? myTestDirCoverageInfos.get(path) : myDirCoverageInfos.get(path);
  }

  @Nullable
  public String getDirCoverageInformationString(@NotNull final PsiDirectory directory,
                                                @NotNull final CoverageSuitesBundle currentSuite,
                                                @NotNull final CoverageDataManager manager) {
    DirCoverageInfo coverageInfo = getDirCoverageInfo(directory, currentSuite);
    if (coverageInfo == null) {
      return null;
    }

    if (manager.isSubCoverageActive()) {
      return coverageInfo.coveredLineCount > 0 ? "covered" : null;
    }

    final String filesCoverageInfo = getFilesCoverageInformationString(coverageInfo);
    if (filesCoverageInfo != null) {
      final StringBuilder builder = new StringBuilder();
      builder.append(filesCoverageInfo);
      final String linesCoverageInfo = getLinesCoverageInformationString(coverageInfo);
      if (linesCoverageInfo != null) {
        builder.append(", ").append(linesCoverageInfo);
      }
      return builder.toString();
    }
    return null;
  }

  // SimpleCoverageAnnotator doesn't require normalized file paths any more
  // so now coverage report should work w/o usage of this method
  @Deprecated
  public static String getFilePath(final String filePath) {
    return normalizeFilePath(filePath);
  }

  protected static @NotNull
  String normalizeFilePath(@NotNull String filePath) {
    if (SystemInfo.isWindows) {
      filePath = filePath.toLowerCase();
    }
    return FileUtil.toSystemIndependentName(filePath);
  }

  @Nullable
  public String getFileCoverageInformationString(@NotNull final PsiFile psiFile,
                                                 @NotNull final CoverageSuitesBundle currentSuite,
                                                 @NotNull final CoverageDataManager manager) {
    VirtualFile file = psiFile.getVirtualFile().getCanonicalFile();
    if (file == null) {
      file = psiFile.getVirtualFile();
    }

    assert file != null;
    final String path = normalizeFilePath(file.getPath());

    final FileCoverageInfo coverageInfo = myFileCoverageInfos.get(path);
    if (coverageInfo == null) {
      return null;
    }

    if (manager.isSubCoverageActive()) {
      return coverageInfo.coveredLineCount > 0 ? "covered" : null;
    }

    return getLinesCoverageInformationString(coverageInfo);
  }

  @Nullable
  protected FileCoverageInfo collectBaseFileCoverage(@NotNull VirtualFile file,
                                                     @NotNull final Annotator annotator,
                                                     @NotNull final ProjectData projectData,
                                                     @NotNull final Map<String, String> normalizedFiles2Files) {

    file = file.getCanonicalFile() != null ? file.getCanonicalFile() : file;
    assert file != null;

    final String filePath = normalizeFilePath(file.getPath());

    // process file
    final FileCoverageInfo info;

    final ClassData classData = getClassData(filePath, projectData, normalizedFiles2Files);
    if (classData != null) {
      // fill info from coverage data
      info = fileInfoForCoveredFile(classData);
    }
    else {
      // file wasn't mentioned in coverage information
      info = fillInfoForUncoveredFile(VfsUtilCore.virtualToIoFile(file));
    }

    if (info != null) {
      annotator.annotateFile(filePath, info);
    }
    return info;
  }

  private static @Nullable
  ClassData getClassData(
    final @NotNull String filePath,
    final @NotNull ProjectData data,
    final @NotNull Map<String, String> normalizedFiles2Files) {
    final String originalFileName = normalizedFiles2Files.get(filePath);
    if (originalFileName == null) {
      return null;
    }
    return data.getClassData(originalFileName);
  }

  @Nullable
  protected DirCoverageInfo collectFolderCoverage(@NotNull final VirtualFile dir,
                                                  final @NotNull CoverageDataManager dataManager,
                                                  final Annotator annotator,
                                                  final ProjectData projectInfo, boolean trackTestFolders,
                                                  @NotNull final ProjectFileIndex index,
                                                  @NotNull final CoverageEngine coverageEngine,
                                                  Set<VirtualFile> visitedDirs,
                                                  @NotNull final Map<String, String> normalizedFiles2Files) {
    if (!index.isInContent(dir)) {
      return null;
    }

    if (visitedDirs.contains(dir)) {
      return null;
    }

    if (!shouldCollectCoverageInsideLibraryDirs()) {
      if (index.isInLibrarySource(dir) || index.isInLibraryClasses(dir)) {
        return null;
      }
    }
    visitedDirs.add(dir);

    final boolean isInTestSrcContent = TestSourcesFilter.isTestSources(dir, getProject());

    // Don't count coverage for tests folders if track test folders is switched off
    if (!trackTestFolders && isInTestSrcContent) {
      return null;
    }

    final VirtualFile[] children = dataManager.doInReadActionIfProjectOpen(dir::getChildren);
    if (children == null) {
      return null;
    }

    final DirCoverageInfo dirCoverageInfo = new DirCoverageInfo();

    for (VirtualFile fileOrDir : children) {
      if (fileOrDir.isDirectory()) {
        final DirCoverageInfo childCoverageInfo =
          collectFolderCoverage(fileOrDir, dataManager, annotator, projectInfo, trackTestFolders, index,
                                coverageEngine, visitedDirs, normalizedFiles2Files);

        if (childCoverageInfo != null) {
          dirCoverageInfo.totalFilesCount += childCoverageInfo.totalFilesCount;
          dirCoverageInfo.coveredFilesCount += childCoverageInfo.coveredFilesCount;
          dirCoverageInfo.totalLineCount += childCoverageInfo.totalLineCount;
          dirCoverageInfo.coveredLineCount += childCoverageInfo.coveredLineCount;
        }
      }
      else if (coverageEngine.coverageProjectViewStatisticsApplicableTo(fileOrDir)) {
        // let's count statistics only for ruby-based files

        final FileCoverageInfo fileInfo =
          collectBaseFileCoverage(fileOrDir, annotator, projectInfo, normalizedFiles2Files);

        if (fileInfo != null) {
          dirCoverageInfo.totalLineCount += fileInfo.totalLineCount;
          dirCoverageInfo.totalFilesCount++;

          if (fileInfo.coveredLineCount > 0) {
            dirCoverageInfo.coveredFilesCount++;
            dirCoverageInfo.coveredLineCount += fileInfo.coveredLineCount;
          }
        }
      }
    }


    //TODO - toplevelFilesCoverage - is unused variable!

    // no sense to include directories without ruby files
    if (dirCoverageInfo.totalFilesCount == 0) {
      return null;
    }

    final String dirPath = normalizeFilePath(dir.getPath());
    if (isInTestSrcContent) {
      annotator.annotateTestDirectory(dirPath, dirCoverageInfo);
    }
    else {
      annotator.annotateSourceDirectory(dirPath, dirCoverageInfo);
    }

    return dirCoverageInfo;
  }

  protected boolean shouldCollectCoverageInsideLibraryDirs() {
    // By default returns "true" for backward compatibility
    return true;
  }

  public void annotate(@NotNull final VirtualFile contentRoot,
                       @NotNull final CoverageSuitesBundle suite,
                       final @NotNull CoverageDataManager dataManager, @NotNull final ProjectData data,
                       final Project project,
                       final Annotator annotator) {
    if (!contentRoot.isValid()) {
      return;
    }

    // TODO: check name filter!!!!!

    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

    @SuppressWarnings("unchecked") final Set<String> files = data.getClasses().keySet();
    final Map<String, String> normalizedFiles2Files = ContainerUtil.newHashMap();
    for (final String file : files) {
      normalizedFiles2Files.put(normalizeFilePath(file), file);
    }
    collectFolderCoverage(contentRoot, dataManager, annotator, data,
                          suite.isTrackTestFolders(),
                          index,
                          suite.getCoverageEngine(),
                          ContainerUtil.newHashSet(),
                          Collections.unmodifiableMap(normalizedFiles2Files));
  }

  @Override
  @Nullable
  protected Runnable createRenewRequest(@NotNull final CoverageSuitesBundle suite, final @NotNull CoverageDataManager dataManager) {
    final ProjectData data = suite.getCoverageData();
    if (data == null) {
      return null;
    }

    return () -> {
      final Project project = getProject();

      final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);

      // find all modules content roots
      final VirtualFile[] modulesContentRoots = dataManager.doInReadActionIfProjectOpen(() -> rootManager.getContentRoots());

      if (modulesContentRoots == null) {
        return;
      }

      // gather coverage from all content roots
      for (VirtualFile root : modulesContentRoots) {
        annotate(root, suite, dataManager, data, project, new Annotator() {
          public void annotateSourceDirectory(final String dirPath, final DirCoverageInfo info) {
            myDirCoverageInfos.put(dirPath, info);

            try {
              myDirCoverageInfos.put((new File(dirPath)).getCanonicalPath(), info);
            }
            catch (IOException e) {
              //pass
            }
          }

          public void annotateTestDirectory(final String dirPath, final DirCoverageInfo info) {
            myTestDirCoverageInfos.put(dirPath, info);

            try {
              myTestDirCoverageInfos.put((new File(dirPath)).getCanonicalPath(), info);
            }
            catch (IOException e) {
              //pass
            }
          }

          public void annotateFile(@NotNull final String filePath, @NotNull final FileCoverageInfo info) {
            myFileCoverageInfos.put(filePath, info);

            try {
              myFileCoverageInfos.put((new File(filePath)).getCanonicalPath(), info);
            }
            catch (IOException e) {
              //pass
            }
          }
        });
      }

      //final VirtualFile[] roots = ProjectRootManagerEx.getInstanceEx(project).getContentRootsFromAllModules();
      //index.iterateContentUnderDirectory(roots[0], new ContentIterator() {
      //  public boolean processFile(final VirtualFile fileOrDir) {
      //    // TODO support for libraries and sdk
      //    if (index.isInContent(fileOrDir)) {
      //      final String normalizedPath = RubyCoverageEngine.rcovalizePath(fileOrDir.getPath(), (RubyCoverageSuite)suite);
      //
      //      // TODO - check filters
      //
      //      if (fileOrDir.isDirectory()) {
      //        //// process dir
      //        //if (index.isInTestSourceContent(fileOrDir)) {
      //        //  //myTestDirCoverageInfos.put(RubyCoverageEngine.rcovalizePath(fileOrDir.getPath(), (RubyCoverageSuite)suite), )
      //        //} else {
      //        //  myDirCoverageInfos.put(normalizedPath, new FileCoverageInfo());
      //        //}
      //      } else {
      //        // process file
      //        final ClassData classData = data.getOrCreateClassData(normalizedPath);
      //        if (classData != null) {
      //          final int count = classData.getLines().length;
      //          if (count != 0) {
      //            final FileCoverageInfo info = new FileCoverageInfo();
      //            info.totalLineCount = count;
      //            // let's count covered lines
      //            for (int i = 1; i <= count; i++) {
      //              final LineData lineData = classData.getLineData(i);
      //              if (lineData.getStatus() != LineCoverage.NONE){
      //                info.coveredLineCount++;
      //              }
      //            }
      //            myFileCoverageInfos.put(normalizedPath, info);
      //          }
      //        }
      //      }
      //    }
      //    return true;
      //  }
      //});

      dataManager.triggerPresentationUpdate();
    };
  }

  @Nullable
  protected String getLinesCoverageInformationString(@NotNull final FileCoverageInfo info) {
    return calcCoveragePercentage(info) + "% lines covered";
  }

  protected static int calcCoveragePercentage(FileCoverageInfo info) {
    return calcPercent(info.coveredLineCount, info.totalLineCount);
  }

  private static int calcPercent(final int covered, final int total) {
    return total != 0 ? (int)((double)covered / total * 100) : 100;
  }

  @Nullable
  protected String getFilesCoverageInformationString(@NotNull final DirCoverageInfo info) {
    return calcPercent(info.coveredFilesCount, info.totalFilesCount) + "% files";
  }

  @Nullable
  private FileCoverageInfo fileInfoForCoveredFile(@NotNull final ClassData classData) {
    final Object[] lines = classData.getLines();

    // class data lines = [0, 1, ... count] but first element with index = #0 is fake and isn't
    // used thus count = length = 1
    final int count = lines.length - 1;

    if (count == 0) {
      return null;
    }

    final FileCoverageInfo info = new FileCoverageInfo();

    info.coveredLineCount = 0;
    info.totalLineCount = 0;
    // let's count covered lines
    for (int i = 1; i <= count; i++) {
      final LineData lineData = classData.getLineData(i);

      processLineData(info, lineData);
    }
    return info;
  }

  protected void processLineData(@NotNull FileCoverageInfo info, @Nullable LineData lineData) {
    if (lineData == null) {
      // Ignore not src code
      return;
    }
    final int status = lineData.getStatus();
    // covered - if src code & covered (or inferred covered)

    if (status != LineCoverage.NONE) {
      info.coveredLineCount++;
    }
    info.totalLineCount++;
  }

  @Nullable
  protected FileCoverageInfo fillInfoForUncoveredFile(@NotNull File file) {
    return null;
  }

  private interface Annotator {
    void annotateSourceDirectory(final String dirPath, final DirCoverageInfo info);

    void annotateTestDirectory(final String dirPath, final DirCoverageInfo info);

    void annotateFile(@NotNull final String filePath, @NotNull final FileCoverageInfo info);
  }
}
