/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.PsiClass;
import com.intellij.rt.coverage.instrumentation.SourceLineCounter;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.util.List;

/**
 * User: anna
 * Date: 12/30/11
 */
public class SourceLineCounterUtil {
  public static boolean collectNonCoveredClassInfo(final PackageAnnotator.ClassCoverageInfo classCoverageInfo,
                                                   final PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                                                   byte[] content,
                                                   final boolean excludeLines,
                                                   final PsiClass psiClass) {
    if (content == null) return false;
    ClassReader reader = new ClassReader(content, 0, content.length);

    SourceLineCounter counter = new SourceLineCounter(null, excludeLines, null);
    reader.accept(counter, 0);
    classCoverageInfo.totalLineCount += counter.getNSourceLines();
    packageCoverageInfo.totalLineCount += counter.getNSourceLines();
    for (Object nameAndSig : counter.getMethodsWithSourceCode()) {
      if (!PackageAnnotator.isGeneratedDefaultConstructor(psiClass, (String) nameAndSig)) {
        classCoverageInfo.totalMethodCount++;
        packageCoverageInfo.totalMethodCount++;
      }
    }
    if (!counter.isInterface()) {
      packageCoverageInfo.totalClassCount++;
    }
    return !counter.isInterface();
  }

  public static void collectSrcLinesForUntouchedFiles(final List<Integer> uncoveredLines,
                                                      byte[] content, final boolean excludeLines) {
    final ClassReader reader = new ClassReader(content);
    final SourceLineCounter collector = new SourceLineCounter(null, excludeLines, null);
    reader.accept(collector, 0);
    final TIntObjectHashMap lines = collector.getSourceLines();
    lines.forEachKey(new TIntProcedure() {
      public boolean execute(int line) {
        line--;
        uncoveredLines.add(line);
        return true;
      }
    });
  }
}
