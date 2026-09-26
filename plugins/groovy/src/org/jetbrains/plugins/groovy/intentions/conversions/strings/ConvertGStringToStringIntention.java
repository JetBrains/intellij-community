// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;

public class ConvertGStringToStringIntention extends GrPsiUpdateIntention {
  public static final String INTENTION_NAME = "Convert to String";

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ConvertibleGStringLiteralPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrLiteral exp = (GrLiteral)element;
    PsiImplUtil.replaceExpression(convertLiteralToStringLiteral(exp), exp);
  }

  /** @deprecated Use {@link #convertLiteralToStringLiteral(GrLiteral)} */
  @Deprecated
  public static String convertGStringLiteralToStringLiteral(GrLiteral literal) {
    return convertLiteralToStringLiteral(literal);
  }

  public static String convertLiteralToStringLiteral(GrLiteral literal) {
    if (GrStringUtil.isSlashyString(literal) || GrStringUtil.isDollarSlashyString(literal)) {
      return convertRegexLiteralToStringLiteral(literal);
    }
    return convertDoubleQuotedLiteralToStringLiteral(literal);
  }

  private static String convertDoubleQuotedLiteralToStringLiteral(GrLiteral literal) {
    if (!(literal instanceof GrString grString)) {
      String quoted = escapeAndQuoteGStringContent(GrStringUtil.removeQuotes(literal.getText()));
      return quoted != null ? quoted : "''";
    }
    ArrayList<String> parts = new ArrayList<>();
    for (PsiElement part : grString.getAllContentParts()) {
      if (part instanceof GrStringInjection injection) {
        String text = convertInjection(injection);
        if (text != null) parts.add(text);
      }
      else {
        String quoted = escapeAndQuoteGStringContent(part.getText());
        if (quoted != null) parts.add(quoted);
      }
    }
    return parts.isEmpty() ? "''" : StringUtil.join(parts, " + ");
  }

  private static String convertRegexLiteralToStringLiteral(GrLiteral literal) {
    boolean isSlashy = GrStringUtil.isSlashyString(literal);
    if (literal instanceof GrString grString) {
      ArrayList<String> parts = new ArrayList<>();
      for (PsiElement part : grString.getAllContentParts()) {
        if (part instanceof GrStringInjection injection) {
          String text = convertInjection(injection);
          if (text != null) parts.add(text);
        }
        else {
          String raw = isSlashy ? GrStringUtil.unescapeSlashyString(part.getText())
                                : GrStringUtil.unescapeDollarSlashyString(part.getText());
          String quoted = escapeAndQuoteRaw(raw);
          if (!quoted.equals("''")) parts.add(quoted);
        }
      }
      return parts.isEmpty() ? "''" : StringUtil.join(parts, " + ");
    }
    String content = GrStringUtil.removeQuotes(literal.getText());
    String raw = literal.getValue() instanceof String s ? s
               : isSlashy ? GrStringUtil.unescapeSlashyString(content) : GrStringUtil.unescapeDollarSlashyString(content);
    return escapeAndQuoteRaw(raw);
  }

  private static @Nullable String convertInjection(GrStringInjection injection) {
    GrClosableBlock block = injection.getClosableBlock();
    if (block != null) return prepareClosableBlock(block);
    GrExpression expr = injection.getExpression();
    if (expr != null) return prepareExpression(expr);
    return injection.getText();
  }

  private static @Nullable String escapeAndQuoteGStringContent(String text) {
    StringBuilder buffer = new StringBuilder();
    if (text.indexOf('\n') >= 0) {
      GrStringUtil.escapeAndUnescapeSymbols(text, "", "\"$", buffer);
      GrStringUtil.fixAllTripleQuotes(buffer, 0);
    }
    else {
      GrStringUtil.escapeAndUnescapeSymbols(text, "'", "\"$", buffer);
    }
    String quoted = GrStringUtil.addQuotes(buffer.toString(), false);
    return quoted.equals("''") ? null : quoted;
  }

  private static String escapeAndQuoteRaw(String raw) {
    boolean multiline = raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0;
    String escaped = GrStringUtil.escapeSymbolsForString(raw, !multiline, false);
    if (multiline) {
      StringBuilder sb = new StringBuilder(escaped);
      GrStringUtil.fixAllTripleQuotes(sb, 0);
      escaped = sb.toString();
    }
    return GrStringUtil.addQuotes(escaped, false);
  }

  private static String prepareClosableBlock(GrClosableBlock block) {
    final GrStatement statement = block.getStatements()[0];
    final GrExpression expr;
    if (statement instanceof GrReturnStatement) {
      expr = ((GrReturnStatement)statement).getReturnValue();
    }
    else {
      expr = (GrExpression)statement;
    }
    return prepareExpression(expr);
  }

  private static String prepareExpression(GrExpression expr) {
    if (PsiUtil.isThisOrSuperRef(expr)) return expr.getText();
    String text = expr.getText();

    final PsiType type = expr.getType();
    if (type != null && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText())) {
      if (expr instanceof GrBinaryExpression && GroovyTokenTypes.mPLUS.equals(((GrBinaryExpression)expr).getOperationTokenType())) {
        return '(' + text + ')';
      }
      else {
        return text;
      }
    }
    else {
      return "String.valueOf(" + text + ")";
    }
  }
}