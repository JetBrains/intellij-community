// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author Maxim.Medvedev
 */
public final class ImportOnDemandIntention extends GrPsiUpdateIntention {

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof GrReferenceElement<?> ref)) return;
    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiClass psiClass)) return;

    final String qname = psiClass.getQualifiedName();

    final GrImportStatement importStatement =
      GroovyPsiElementFactory.getInstance(context.project()).createImportStatementFromText(qname, true, true, null);

    final PsiFile containingFile = element.getContainingFile();
    if (!(containingFile instanceof GroovyFile)) return;
    ((GroovyFile)containingFile).addImport(importStatement);

    for (PsiReference reference : ReferencesSearch.search(resolved, new LocalSearchScope(containingFile))) {
      final PsiElement refElement = reference.getElement();
      final PsiElement parent = refElement.getParent();
      if (parent instanceof GrQualifiedReference<?>) {
        GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>)parent);
      }
    }
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element instanceof GrReferenceElement<?> ref)) return false;
        final PsiElement parent = ref.getParent();
        if (!(parent instanceof GrReferenceElement)) return false;
        final PsiElement resolved = ref.resolve();
        if (resolved == null) return false;
        return resolved instanceof PsiClass;
      }
    };
  }
}
