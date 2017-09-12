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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.rt.coverage.instrumentation.SourceLineCounter;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.util.List;
import java.util.Set;

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
    Set<Object> descriptions = new HashSet<>();
    TIntObjectHashMap<?> lines = counter.getSourceLines();
    lines.forEachEntry((line, description) -> {
        if (!PackageAnnotator.isGeneratedDefaultConstructor(psiClass, (String)description)) {
          classCoverageInfo.totalLineCount ++;
          packageCoverageInfo.totalLineCount ++;
          descriptions.add(description);
        } 
        return true;
      });
    
    classCoverageInfo.totalMethodCount += descriptions.size();
    packageCoverageInfo.totalMethodCount += descriptions.size();

    if (!counter.isInterface()) {
      packageCoverageInfo.totalClassCount++;
    }
    return !counter.isInterface();
  }

  public static void collectSrcLinesForUntouchedFiles(final List<Integer> uncoveredLines,
                                                      byte[] content, 
                                                      final boolean excludeLines, 
                                                      final Project project) {
    final ClassReader reader = new ClassReader(content);
    final SourceLineCounter collector = new SourceLineCounter(null, excludeLines, null);
    reader.accept(collector, 0);
    
    String qualifiedName = reader.getClassName();
    PsiClass psiClass = ReadAction.compute(() -> ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), qualifiedName));
    TIntObjectHashMap<?> lines = collector.getSourceLines();
    lines.forEachEntry((line, description) -> {
      if (!PackageAnnotator.isGeneratedDefaultConstructor(psiClass, (String)description)) {
        line--;
        uncoveredLines.add(line);
      }
      return true;
    });
  }
}
