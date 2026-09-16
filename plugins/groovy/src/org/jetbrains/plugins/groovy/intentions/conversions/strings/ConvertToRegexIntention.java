// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Max Medvedev
 */
public final class ConvertToRegexIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof GrLiteral literal)) return;

    StringBuilder buffer = new StringBuilder("/");
    if (GrStringUtil.isDollarSlashyString(literal)) {
      buffer.append(GrStringUtil.escapeSymbolsForSlashyStrings(GrStringUtil.removeQuotes(element.getText())));
    }
    else if (element instanceof GrLiteralImpl) {
      Object value = literal.getValue();
      if (value instanceof String s) {
        GrStringUtil.escapeSymbolsForSlashyStrings(buffer, s);
      }
      else {
        unescapeAndAppend(buffer, GrStringUtil.removeQuotes(element.getText()));
      }
    }
    else if (element instanceof GrString string) {
      for (PsiElement part : string.getAllContentParts()) {
        if (part instanceof GrStringContent) {
          unescapeAndAppend(buffer, part.getText());
        }
        else if (part instanceof GrStringInjection) {
          buffer.append(part.getText());
        }
      }
    }

    buffer.append("/");
    GrExpression regex = GroovyPsiElementFactory.getInstance(context.project()).createExpressionFromText(buffer);

    element.replace(regex); //don't use replaceWithExpression since it can revert regex to string if regex brakes syntax
  }

  private static void unescapeAndAppend(StringBuilder buffer, String rawText) {
    GrStringUtil.escapeSymbolsForSlashyStrings(buffer, GrStringUtil.unescapeString(rawText));
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return element instanceof GrLiteral literal &&
               GrStringUtil.isStringLiteral(literal) &&
               !GrStringUtil.removeQuotes(element.getText()).isEmpty() &&
               !GrStringUtil.isSlashyString(literal);
      }
    };
  }
}
