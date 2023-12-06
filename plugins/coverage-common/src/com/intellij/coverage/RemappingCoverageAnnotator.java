// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * This coverage annotator tries to map existing absolute path specified for a file into a local machine absolute path.
 * By doing this, we are able to open reports that were written on remote machines.
 */
public class RemappingCoverageAnnotator extends SimpleCoverageAnnotator {

  public RemappingCoverageAnnotator(Project project) {
    super(project);
  }

  @Override
  protected void annotate(@NotNull VirtualFile contentRoot,
                          @NotNull CoverageSuitesBundle suite,
                          @NotNull CoverageDataManager dataManager,
                          @NotNull ProjectData data,
                          Project project,
                          Annotator annotator) {
    if (!contentRoot.isValid()) {
      return;
    }
    Map<String, String> normalizedFiles2Files = getNormalizedFiles2FilesMapping(data);
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    // try to annotate files. If no data was found then remap paths and retry
    final DirCoverageInfo info = collectFolderCoverage(contentRoot, dataManager, annotator, data,
                                                       suite.isTrackTestFolders(), index, suite.getCoverageEngine(),
                                                       new HashSet<>(), Collections.unmodifiableMap(normalizedFiles2Files));
    if (info == null) {
      String pathToRemap = findPathToRemap(data.getClasses().keySet(), contentRoot);
      if (pathToRemap != null) {
        // just replace paths for every file
        data = remapData(data, pathToRemap, contentRoot);
        suite.setCoverageData(data);
        for (CoverageSuite suiteSuite : suite.getSuites()) {
          suiteSuite.setCoverageData(data);
        }
        normalizedFiles2Files = getNormalizedFiles2FilesMapping(data);
        collectFolderCoverage(contentRoot, dataManager, annotator, data,
                              suite.isTrackTestFolders(), index, suite.getCoverageEngine(),
                              new HashSet<>(), Collections.unmodifiableMap(normalizedFiles2Files));
      }
    }
  }

  private static @Nullable String findPathToRemap(Set<String> files, VirtualFile contentRoot) {
    final Comparator<String> comparator = Comparator.comparingInt(o -> StringUtil.countChars(o, '/'));
    final List<String> sortedFiles = ContainerUtil.reverse(ContainerUtil.sorted(files, comparator));
    final List<String> suspects = sortedFiles.subList(0, Math.min(3, sortedFiles.size()));
    String pathToRemap = null;
    for (String suspect : suspects) {
      String[] pathParts = suspect.split("/");
      String path = "";
      String oldValue = pathToRemap;
      for (String part : pathParts) {
        path += part + "/";
        final String relPath = StringUtil.substringAfter(suspect, path);
        if (relPath != null && contentRoot.findFileByRelativePath(relPath) != null) {
          pathToRemap = StringUtil.substringBeforeLast(path, "/");
          break;
        }
      }
      if (oldValue != null && !oldValue.equals(pathToRemap)) {
        pathToRemap = null;
        break;
      }
    }
    return pathToRemap;
  }

  private static @NotNull ProjectData remapData(@NotNull ProjectData oldData, @NotNull String oldPath, @NotNull VirtualFile contentRoot) {
    final ProjectData newData = new ProjectData();
    oldData.getClasses().forEach((name, oldClass) -> {
      String relPath = StringUtil.substringAfter(name, oldPath);
      String newPath = name;
      if (relPath != null) {
        VirtualFile targetFile = contentRoot.findFileByRelativePath(relPath);
        if (targetFile != null) {
          newPath = targetFile.getCanonicalPath();
        }
        else {
          newPath = contentRoot.getPath() + relPath;
        }
      }
      final ClassData newClass = newData.getOrCreateClassData(newPath);
      newClass.setLines((LineData[])oldClass.getLines());
    });
    return newData;
  }
}
