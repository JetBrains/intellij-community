// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Max Medvedev
 */
public final class GrBreakStringOnLineBreaksIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final String text = invokeImpl(element);
    final GrExpression newExpr = GroovyPsiElementFactory.getInstance(context.project()).createExpressionFromText(text);
    ((GrExpression)element).replaceWithExpression(newExpr, true);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return element instanceof GrLiteral && !element.getText().equals(invokeImpl(element));
      }
    };
  }

  private static String invokeImpl(PsiElement element) {
    final String text = element.getText();
    final String quote = GrStringUtil.getStartQuote(text);

    if (!("'".equals(quote) || "\"".equals(quote))) return text;
    if (!text.contains("\\n")) return text;

    String value = GrStringUtil.removeQuotes(text);

    StringBuilder buffer = new StringBuilder();
    if (element instanceof GrString) {
      processGString(element, quote, buffer);
    }
    else {
      processSimpleString(quote, value, buffer);
    }

    final String result = buffer.toString();
    if (result.endsWith("+\n\"\"")) return result.substring(0, result.length() - 4);

    return result;
  }

  private static void processGString(PsiElement element, String quote, StringBuilder buffer) {
    final ASTNode node = element.getNode();

    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      final IElementType type = child.getElementType();
      if (type == GroovyTokenTypes.mGSTRING_BEGIN || type == GroovyTokenTypes.mGSTRING_END) continue;
      if (type == GroovyElementTypes.GSTRING_INJECTION) {
        buffer.append(child.getText());
      }
      else {
        String value = child.getText();
        int prev = 0;
        if (!isInjection(child.getTreePrev())) {
          buffer.append(quote);
        }
        for (int pos = value.indexOf("\\n"); pos >= 0; pos = value.indexOf("\\n", prev)) {
          int end = checkForR(value, pos);
          buffer.append(value, prev, end);
          prev = end;
          buffer.append(quote);
          buffer.append("+\n");
          buffer.append(quote);
        }
        buffer.append(value.substring(prev));
        if (!isInjection(child.getTreeNext())) {
          buffer.append(quote);
        }
      }
    }
  }

  private static boolean isInjection(ASTNode next) {
    return next != null && next.getElementType() == GroovyElementTypes.GSTRING_INJECTION;
  }

  private static void processSimpleString(String quote, String value, StringBuilder buffer) {
    int prev = 0;
    for (int pos = value.indexOf("\\n"); pos >= 0; pos = value.indexOf("\\n", prev)) {
      buffer.append(quote);
      int end = checkForR(value, pos);
      buffer.append(value, prev, end);
      prev = end;
      buffer.append(quote);
      buffer.append("+\n");
    }
    buffer.append(quote);
    buffer.append(value.substring(prev));
    buffer.append(quote);
  }

  private static int checkForR(String value, int pos) {
    pos += 2;
    if (value.length() > pos + 2 && "\\r".equals(value.substring(pos, pos + 2))) return pos + 2;
    return pos;
  }
}
