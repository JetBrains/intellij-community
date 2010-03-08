/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyImportOptimizerRefactoringHelper implements RefactoringHelper<Set<GroovyFile>> {
  public Set<GroovyFile> prepareOperation(UsageInfo[] usages) {
    Set<GroovyFile> files = new HashSet<GroovyFile>();
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element != null) {
        final PsiFile file = element.getContainingFile();
        if (file instanceof GroovyFile) {
          files.add((GroovyFile)file);
        }
      }
    }
    return files;
  }

  public void performOperation(Project project, Set<GroovyFile> files) {
    final GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
    for (GroovyFile file : files) {
      optimizer.removeUnusedImports(file);
    }
  }

}
