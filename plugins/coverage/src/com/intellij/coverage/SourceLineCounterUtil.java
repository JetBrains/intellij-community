// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.project.Project;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.SaveHook;
import com.intellij.rt.coverage.util.ClassNameUtil;
import org.jetbrains.coverage.org.objectweb.asm.ClassReader;

import java.util.List;

public final class SourceLineCounterUtil {

  public static void collectSrcLinesForUntouchedFiles(final List<? super Integer> uncoveredLines,
                                                      byte[] content,
                                                      final boolean isSampling,
                                                      final Project project) {
    final ClassReader reader = new ClassReader(content);
    final String qualifiedName = ClassNameUtil.convertToFQName(reader.getClassName());
    final ProjectData projectData = new ProjectData();
    final boolean ignoreEmptyPrivateConstructors = JavaCoverageOptionsProvider.getInstance(project).ignoreEmptyPrivateConstructors();
    SaveHook.appendUnloadedClass(projectData, qualifiedName, reader, isSampling, false, ignoreEmptyPrivateConstructors);
    final ClassData classData = projectData.getClassData(qualifiedName);
    if (classData == null || classData.getLines() == null) return;
    final LineData[] lines = (LineData[])classData.getLines();
    for (LineData line : lines) {
      if (line == null) continue;
      final String description = line.getMethodSignature();
      if (!JavaCoverageOptionsProvider.getInstance(project).isGeneratedConstructor(qualifiedName, description)) {
        uncoveredLines.add(line.getLineNumber() - 1);
      }
    }
  }
}
