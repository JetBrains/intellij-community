// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.modplan.ModPlanRefactoringListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyUnusedImportUtil.usedImports;

/**
 * Removes the unused imports of a Groovy source file that a headless refactoring changed, as {@link GroovyImportOptimizerRefactoringHelper}
 * does after a legacy one: all of them, also the ones that were unused before.
 */
final class GroovyModPlanImportOptimizerListener implements ModPlanRefactoringListener {
  @Override
  public void onRefactoringDone(@NotNull PsiFile copy) {
    if (!(copy instanceof GroovyFile groovyFile)) return;
    VirtualFile originalFile = copy.getOriginalFile().getVirtualFile();
    if (originalFile == null || !ProjectRootManager.getInstance(copy.getProject()).getFileIndex().isInSource(originalFile)) return;
    Set<GrImportStatement> usedImports = usedImports(groovyFile);
    for (GrImportStatement importStatement : PsiUtil.getValidImportStatements(groovyFile)) {
      if (!usedImports.contains(importStatement)) {
        groovyFile.removeImport(importStatement);
      }
    }
  }
}
