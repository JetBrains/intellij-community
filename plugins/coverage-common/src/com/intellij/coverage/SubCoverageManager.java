// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This manager processes requests to filter coverage result by test (and restore default).
 */
@Service(Service.Level.PROJECT)
public final class SubCoverageManager {
  private static final Logger LOG = Logger.getInstance(SubCoverageManager.class);
  private boolean mySubCoverageIsActive;

  public static SubCoverageManager getInstance(Project project) {
    return project.getService(SubCoverageManager.class);
  }

  public boolean isSubCoverageActive() {
    return mySubCoverageIsActive;
  }

  public void restoreMergedCoverage(@NotNull CoverageSuitesBundle suite) {
    mySubCoverageIsActive = false;
    suite.restoreCoverageData();
  }

  public void selectSubCoverage(@NotNull CoverageSuitesBundle suite, List<String> testNames) {
    suite.restoreCoverageData();
    final ProjectData data = suite.getCoverageData();
    if (data == null) return;
    mySubCoverageIsActive = true;
    final Map<String, Set<Integer>> executionTrace = new HashMap<>();
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      suite.getCoverageEngine().collectTestLines(testNames, coverageSuite, executionTrace);
    }
    final ProjectData projectData = new ProjectData();
    for (Map.Entry<String, Set<Integer>> entry : executionTrace.entrySet()) {
      String className = entry.getKey();
      Set<Integer> lineNumbers = entry.getValue();

      ClassData classData = projectData.getOrCreateClassData(className);
      ClassData oldData = data.getClassData(className);
      LOG.assertTrue(oldData != null, "missed className: \"" + className + "\"");
      final Object[] oldLines = oldData.getLines();
      LOG.assertTrue(oldLines != null);
      int newLength = Math.max(oldLines.length, 1 + lineNumbers.stream().mapToInt(Integer::intValue).max().orElse(-1));
      LineData[] lines = new LineData[newLength];
      for (int line : lineNumbers) {
        String methodSig = null;
        if (line < oldLines.length) {
          final LineData oldLineData = oldData.getLineData(line);
          if (oldLineData != null) {
            methodSig = oldLineData.getMethodSignature();
          }
        }
        final LineData lineData = new LineData(line, methodSig);
        if (methodSig != null) {
          classData.registerMethodSignature(lineData);
        }
        lineData.setStatus(LineCoverage.FULL);
        lines[line] = lineData;
      }
      classData.setLines(lines);
      classData.setFullyAnalysed(false);
    }
    suite.setCoverageData(projectData);
  }
}
