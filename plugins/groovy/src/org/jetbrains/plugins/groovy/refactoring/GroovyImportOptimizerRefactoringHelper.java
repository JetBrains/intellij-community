/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.refactoring.RefactoringHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

import java.util.Set;
import java.util.Collections;

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
    for (GroovyFile file : files) {
      removeUnusedImports(file);
    }
  }

  public void removeUnusedImports(GroovyFile file) {
    final GrImportStatement[] imports = file.getImportStatements();
    final Set<GrImportStatement> unused = new HashSet<GrImportStatement>(imports.length);
    Collections.addAll(unused, imports);
    file.accept(new GroovyRecursiveElementVisitor() {
      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        visitRefElement(refElement);
        super.visitCodeReferenceElement(refElement);
      }

      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        visitRefElement(referenceExpression);
        super.visitReferenceExpression(referenceExpression);
      }

      private void visitRefElement(GrReferenceElement refElement) {
        final GroovyResolveResult[] resolveResults = refElement.multiResolve(false);
        for (GroovyResolveResult resolveResult : resolveResults) {
          final GroovyPsiElement context = resolveResult.getCurrentFileResolveContext();
          final PsiElement element = resolveResult.getElement();
          if (element == null) return;
          if (context instanceof GrImportStatement) {
            final GrImportStatement importStatement = (GrImportStatement)context;
            unused.remove(importStatement);
          }
        }
      }
    });
    for (GrImportStatement importStatement : unused) {
      file.removeImport(importStatement);
    }
  }
}
