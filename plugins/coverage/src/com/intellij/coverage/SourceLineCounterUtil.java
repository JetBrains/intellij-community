// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.rt.coverage.instrumentation.SourceLineCounter;
import org.jetbrains.coverage.gnu.trove.TIntObjectHashMap;
import org.jetbrains.coverage.org.objectweb.asm.ClassReader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SourceLineCounterUtil {
  public static boolean collectNonCoveredClassInfo(final PackageAnnotator.ClassCoverageInfo classCoverageInfo, byte[] content,
                                                   final boolean excludeLines, final boolean ignoreEmptyPrivateConstructors,
                                                   final boolean ignoreGeneratedDefaultConstructor, PsiClass psiClass) {
    if (content == null) return false;
    ClassReader reader = new ClassReader(content, 0, content.length);

    SourceLineCounter counter = new SourceLineCounter(null, excludeLines, null, ignoreEmptyPrivateConstructors);
    reader.accept(counter, ClassReader.SKIP_FRAMES);
    Set<String> descriptions = new HashSet<>();
    TIntObjectHashMap<String> lines = counter.getSourceLines();
    Ref<Boolean> isDefaultConstructorGenerated = new Ref<>();
    lines.forEachEntry((line, description) -> {
      if (isDefaultConstructorGenerated.isNull() &&
          ignoreGeneratedDefaultConstructor &&
          PackageAnnotator.isDefaultConstructor(description)) {
        isDefaultConstructorGenerated.set(PackageAnnotator.isGeneratedDefaultConstructor(psiClass, description));
      }
      if (!isDefaultConstructorGenerated.isNull() &&
          isDefaultConstructorGenerated.get() &&
          PackageAnnotator.isDefaultConstructor(description)) {
        return true;
      }
      classCoverageInfo.totalLineCount++;
      descriptions.add(description);
      return true;
    });

    classCoverageInfo.totalMethodCount += descriptions.size();
    classCoverageInfo.totalBranchCount += counter.getTotalBranches();

    if (!counter.isInterface()) {
      classCoverageInfo.totalClassCount = 1;
    }

    return !counter.isInterface();
  }

  public static void collectSrcLinesForUntouchedFiles(final List<? super Integer> uncoveredLines,
                                                      byte[] content,
                                                      final boolean excludeLines,
                                                      final Project project) {
    final ClassReader reader = new ClassReader(content);
    final SourceLineCounter collector = new SourceLineCounter(null, excludeLines, null, JavaCoverageOptionsProvider.getInstance(project).ignoreEmptyPrivateConstructors());
    reader.accept(collector, 0);

    String qualifiedName = reader.getClassName();
    Condition<String> includeDescriptionCondition = description -> !JavaCoverageOptionsProvider.getInstance(project).isGeneratedConstructor(qualifiedName, description);
    TIntObjectHashMap<String> lines = collector.getSourceLines();
    lines.forEachEntry((line, description) -> {
      if (includeDescriptionCondition.value(description)) {
        line--;
        uncoveredLines.add(line);
      }
      return true;
    });
  }
}
