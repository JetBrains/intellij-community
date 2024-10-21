// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.project.Project;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.UnloadedUtil;
import com.intellij.rt.coverage.util.ClassNameUtil;
import org.jetbrains.coverage.org.objectweb.asm.ClassReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceLineCounterUtil {

  public static List<Integer> collectSrcLinesForUntouchedFiles(byte[] content, final Project project) {
    final ClassReader reader = new ClassReader(content);
    final String qualifiedName = ClassNameUtil.convertToFQName(reader.getClassName());
    final ProjectData projectData = new ProjectData();
    IDEACoverageRunner.setExcludeAnnotations(project, projectData);
    UnloadedUtil.appendUnloadedClass(projectData, qualifiedName, reader, false);
    final ClassData classData = projectData.getClassData(qualifiedName);
    if (classData == null || classData.getLines() == null) return Collections.emptyList();
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
