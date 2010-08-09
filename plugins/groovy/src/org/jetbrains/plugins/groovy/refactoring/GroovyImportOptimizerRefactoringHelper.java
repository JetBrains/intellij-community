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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyImportOptimizerRefactoringHelper implements RefactoringHelper<Set<GroovyFile>> {
  public Set<GroovyFile> prepareOperation(UsageInfo[] usages) {
    Set<GroovyFile> files = new HashSet<GroovyFile>();
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) continue;
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

  public void performOperation(final Project project, final Set<GroovyFile> files) {
    final GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
    final ProgressManager progressManager = ProgressManager.getInstance();
    final Map<GroovyFile, Pair<List<GrImportStatement>, Set<GrImportStatement>>> redundants =
      new HashMap<GroovyFile, Pair<List<GrImportStatement>, Set<GrImportStatement>>>();
    final Runnable findUnusedImports = new Runnable() {
      public void run() {
        final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
        for (final GroovyFile file : files) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (!ProjectRootManager.getInstance(project).getFileIndex().isInSource(virtualFile)) {
            continue;
          }
          if (progressIndicator != null) {
            progressIndicator.setText2(virtualFile.getPresentableUrl());
          }
          final Set<GrImportStatement> usedImports = new HashSet<GrImportStatement>();
          final List<GrImportStatement> perFile = optimizer.findUnusedImports(file, usedImports);
          if (perFile != null) {
            redundants.put(file, Pair.create(perFile, usedImports));
          }
        }
      }
    };

    if (!progressManager.runProcessWithProgressSynchronously(findUnusedImports, "Optimizing imports (Groovy) ... ", false, project)) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (GroovyFile groovyFile : redundants.keySet()) {
          final Pair<List<GrImportStatement>, Set<GrImportStatement>> pair = redundants.get(groovyFile);
          final List<GrImportStatement> redundantPerFile = pair.getFirst();
          final Set<GrImportStatement> usedInFile = pair.getSecond();
          for (GrImportStatement importStatement : redundantPerFile) {
            if (!usedInFile.contains(importStatement)) {
              groovyFile.removeImport(importStatement);
            }
          }
        }
      }
    });
  }

}
