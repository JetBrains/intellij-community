// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.modplan.ModPlanRefactoringListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyUnusedImportUtil.usedImports;

/**
 * Removes the unused imports of a Groovy source file that a headless refactoring changed, as {@link GroovyImportOptimizerRefactoringHelper}
 * does after a legacy one: all of them, also the ones that were unused before. An import that resolves to nothing in the copy stays.
 * <p>
 * The copy resolves into the other files as they were before the edits. So the import of an element that the refactoring renames in
 * another file resolves to nothing, and its usages do not use it. Any language can own the renamed element, for example Java.
 * <pre>{@code
 * import static p.Util.bar as foo
 * print foo()
 * // rename the Java method Util.bar to baz
 * import static p.Util.baz as foo
 * print foo()
 * }</pre>
 */
@ApiStatus.Internal
public final class GroovyModPlanImportOptimizerListener implements ModPlanRefactoringListener {
  @Override
  public void onRefactoringDone(@NotNull PsiFile copy) {
    if (!(copy instanceof GroovyFile groovyFile)) return;
    VirtualFile originalFile = copy.getOriginalFile().getVirtualFile();
    if (originalFile == null || !ProjectRootManager.getInstance(copy.getProject()).getFileIndex().isInSource(originalFile)) return;
    Set<GrImportStatement> usedImports = usedImports(groovyFile);
    for (GrImportStatement importStatement : PsiUtil.getValidImportStatements(groovyFile)) {
      if (!usedImports.contains(importStatement) && !resolvesToNothing(importStatement)) {
        groovyFile.removeImport(importStatement);
      }
    }
  }

  private static boolean resolvesToNothing(@NotNull GrImportStatement importStatement) {
    GrCodeReferenceElement reference = importStatement.getImportReference();
    return reference != null && reference.multiResolve(false).length == 0;
  }
}
