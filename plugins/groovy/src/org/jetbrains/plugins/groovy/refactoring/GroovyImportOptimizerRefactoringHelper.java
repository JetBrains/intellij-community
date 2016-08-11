/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyImportUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyImportOptimizerRefactoringHelper implements RefactoringHelper<Set<GroovyFile>> {
  @Override
  public Set<GroovyFile> prepareOperation(UsageInfo[] usages) {
    Set<GroovyFile> files = new HashSet<>();
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) continue;
      PsiFile file = usage.getFile();
      if (file instanceof GroovyFile && file.isValid() && file.isPhysical()) {
        files.add((GroovyFile)file);
      }
    }
    return files;
  }

  @Override
  public void performOperation(final Project project, final Set<GroovyFile> files) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    final Map<GroovyFile, Pair<List<GrImportStatement>, Set<GrImportStatement>>> redundants = new HashMap<>();
    final Runnable findUnusedImports = () -> {
      final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
      final int total = files.size();
      int i = 0;
      for (final GroovyFile file : files) {
        if (!file.isValid()) continue;
        final VirtualFile virtualFile = file.getVirtualFile();
        if (!ProjectRootManager.getInstance(project).getFileIndex().isInSource(virtualFile)) {
          continue;
        }
        if (progressIndicator != null) {
          progressIndicator.setText2(virtualFile.getPresentableUrl());
          progressIndicator.setFraction((double)i++/total);
        }
        ApplicationManager.getApplication().runReadAction(() -> {
          final Set<GrImportStatement> usedImports = GroovyImportUtil.findUsedImports(file);
          final List<GrImportStatement> validImports = PsiUtil.getValidImportStatements(file);
          redundants.put(file, Pair.create(validImports, usedImports));
        });
      }
    };

    if (!progressManager.runProcessWithProgressSynchronously(findUnusedImports, "Optimizing imports (Groovy) ... ", false, project)) {
      return;
    }

    AccessToken accessToken = WriteAction.start();

    try {
      for (GroovyFile groovyFile : redundants.keySet()) {
        if (!groovyFile.isValid()) continue;
        final Pair<List<GrImportStatement>, Set<GrImportStatement>> pair = redundants.get(groovyFile);
        final List<GrImportStatement> validImports = pair.getFirst();
        final Set<GrImportStatement> usedImports = pair.getSecond();
        for (GrImportStatement importStatement : validImports) {
          if (!usedImports.contains(importStatement)) {
            groovyFile.removeImport(importStatement);
          }
        }
      }
    }
    finally {
      accessToken.finish();
    }
  }

}
