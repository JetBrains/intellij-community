// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

public final class GroovyStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilter.Factory {
  @Override
  protected @NotNull PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document) {
    return new GroovyStripTrailingSpacesFilter(document);
  }

  @Override
  protected boolean isApplicableTo(@NotNull Language language) {
    return language.is(GroovyLanguage.INSTANCE);
  }
  
  private static class GroovyStripTrailingSpacesFilter extends PsiBasedStripTrailingSpacesFilter {

    protected GroovyStripTrailingSpacesFilter(@NotNull Document document) {
      super(document);
    }

    @Override
    protected void process(@NotNull PsiFile psiFile) {
      psiFile.accept(new GroovyPsiElementVisitor(new GroovyRecursiveElementVisitor() {
        @Override
        public void visitGStringExpression(@NotNull GrString gstring) {
          disableRange(gstring.getTextRange(), false);
        }

        @Override
        public void visitLiteralExpression(@NotNull GrLiteral literal) {
          disableRange(literal.getTextRange(), false);
        }
      }));
    }
  } 
}
