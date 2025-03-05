// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    if (!(element instanceof GrLiteral)) return;

    StringBuilder buffer = new StringBuilder();
    buffer.append("/");

    if (GrStringUtil.isDollarSlashyString(((GrLiteral)element))) {
      buffer.append(GrStringUtil.removeQuotes(element.getText()));
    }
    else if (element instanceof GrLiteralImpl) {
      Object value = ((GrLiteralImpl)element).getValue();
      if (value instanceof String) {
        GrStringUtil.escapeSymbolsForSlashyStrings(buffer, (String)value);
      }
      else {
        String rawText = GrStringUtil.removeQuotes(element.getText());
        unescapeAndAppend(buffer, rawText);
      }
    }
    else if (element instanceof GrString) {
      for (PsiElement part : ((GrString)element).getAllContentParts()) {
        if (part instanceof GrStringContent) {
          unescapeAndAppend(buffer, part.getText());
        }
        else if (part instanceof GrStringInjection) {
          buffer.append(part.getText());
        }
      }
    }

    buffer.append("/");
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project());
    GrExpression regex = factory.createExpressionFromText(buffer);

    element.replace(regex); //don't use replaceWithExpression since it can revert regex to string if regex brakes syntax
  }

  private static void unescapeAndAppend(StringBuilder buffer, String rawText) {
    String parsed = GrStringUtil.unescapeString(rawText);
    GrStringUtil.escapeSymbolsForSlashyStrings(buffer, parsed);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return element instanceof GrLiteral &&
               GrStringUtil.isStringLiteral((GrLiteral)element) &&
               !GrStringUtil.removeQuotes(element.getText()).isEmpty() &&
               !GrStringUtil.isSlashyString(((GrLiteral)element));
      }
    };
  }
}
