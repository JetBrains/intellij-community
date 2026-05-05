// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.project.Project;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceLineCounterUtil {

  public static List<Integer> collectSrcLinesForUntouchedFiles(ClassData classData, final Project project) {
    if (classData == null || classData.getLines() == null) return Collections.emptyList();
    final String qualifiedName = classData.getName();
    final List<Integer> uncoveredLines = new ArrayList<>();
    final LineData[] lines = (LineData[])classData.getLines();
    for (LineData line : lines) {
      if (line == null) continue;
      final String description = line.getMethodSignature();
      if (!JavaCoverageOptionsProvider.getInstance(project).isGeneratedConstructor(qualifiedName, description)) {
        uncoveredLines.add(line.getLineNumber() - 1);
      }
    }
    return uncoveredLines;
  }
}
