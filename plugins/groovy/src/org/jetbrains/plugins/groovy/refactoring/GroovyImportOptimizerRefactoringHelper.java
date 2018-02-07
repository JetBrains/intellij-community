// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring;

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
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyUnusedImportUtil.usedImports;

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
      if (progressIndicator != null) {
        progressIndicator.setIndeterminate(false);
      }
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
          final Set<GrImportStatement> usedImports = usedImports(file);
          final List<GrImportStatement> validImports = PsiUtil.getValidImportStatements(file);
          redundants.put(file, Pair.create(validImports, usedImports));
        });
      }
    };

    if (!progressManager.runProcessWithProgressSynchronously(findUnusedImports, "Optimizing imports (Groovy) ... ", false, project)) {
      return;
    }

    WriteAction.run(() -> {
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
    });
  }

}
