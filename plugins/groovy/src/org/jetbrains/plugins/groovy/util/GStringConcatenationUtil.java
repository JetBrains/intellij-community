// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_DQ;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_TDQ;

public final class GStringConcatenationUtil {
  private static final String END_BRACE = "}";
  private static final String START_BRACE = "${";

  @ApiStatus.Internal
  public static void convertToGString(GrBinaryExpression expr, StringBuilder builder, boolean multiline) {
    GrExpression left = (GrExpression)PsiUtil.skipParentheses(expr.getLeftOperand(), false);
    GrExpression right = (GrExpression)PsiUtil.skipParentheses(expr.getRightOperand(), false);
    appendOperandText(left, builder, multiline);
    appendOperandText(right, builder, multiline);
  }

  @ApiStatus.Internal
  public static void appendOperandText(@Nullable GrExpression operand, StringBuilder builder, boolean multiline) {
    if (operand instanceof GrRegex) {
      for (GroovyPsiElement element : ((GrRegex)operand).getAllContentParts()) {
        if (element instanceof GrStringInjection) {
          builder.append(element.getText());
        }
        else if (element instanceof GrStringContent) {
          if (GrStringUtil.isDollarSlashyString((GrLiteral)operand)) {
            processDollarSlashyContent(builder, multiline, element.getText());
          }
          else {
            processSlashyContent(builder, multiline, element.getText());
          }
        }
      }
    }
    else if (operand instanceof GrString) {
      boolean isMultiline = GrStringUtil.isMultilineStringLiteral((GrLiteral)operand);
      for (GroovyPsiElement element : ((GrString)operand).getAllContentParts()) {
        if (element instanceof GrStringInjection) {
          builder.append(element.getText());
        }
        else if (element instanceof GrStringContent) {
          if (isMultiline) {
            processMultilineGString(builder, element.getText());
          }
          else {
            processSinglelineGString(builder, element.getText());
          }
        }
      }
    }
    else if (operand instanceof GrLiteral literal) {
      String text = GrStringUtil.removeQuotes(operand.getText());

      if (GrStringUtil.isSingleQuoteString(literal)) {
        processSinglelineString(builder, text);
      }
      else if (GrStringUtil.isTripleQuoteString(literal)) {
        processMultilineString(builder, text);
      }
      else if (GrStringUtil.isDoubleQuoteString(literal)) {
        processSinglelineGString(builder, text);
      }
      else if (GrStringUtil.isTripleDoubleQuoteString(literal)) {
        processMultilineGString(builder, text);
      }
      else if (GrStringUtil.isSlashyString(literal)) {
        processSlashyContent(builder, multiline, text);
      }
      else if (GrStringUtil.isDollarSlashyString(literal)) {
        processDollarSlashyContent(builder, multiline, text);
      }
    }
    else if (ConvertibleToGStringPredicate.satisfied(operand)) {
      convertToGString((GrBinaryExpression)operand, builder, multiline);
    }
    else if (isToStringMethod(operand, builder)) {
      //nothing to do
    }
    else {
      builder.append(START_BRACE).append(operand == null ? "" : operand.getText()).append(END_BRACE);
    }
  }

  private static void processMultilineString(StringBuilder builder, String text) {
    final int position = builder.length();
    GrStringUtil.escapeAndUnescapeSymbols(text, "$", "'\"", builder);
    GrStringUtil.fixAllTripleDoubleQuotes(builder, position);
  }

  private static void processSinglelineString(StringBuilder builder, String text) {
    GrStringUtil.escapeAndUnescapeSymbols(text, "$\"", "'", builder);
  }

  private static StringBuilder processSinglelineGString(StringBuilder builder, String text) {
    return builder.append(text);
  }

  private static void processMultilineGString(StringBuilder builder, String text) {
    StringBuilder raw = new StringBuilder(text);
    GrStringUtil.unescapeCharacters(raw, "\"", true);
    builder.append(raw);
  }

  private static void processDollarSlashyContent(StringBuilder builder, boolean multiline, String text) {
    GrStringUtil.escapeSymbolsForGString(text, !multiline, false, builder);
  }

  private static void processSlashyContent(StringBuilder builder, boolean multiline, String text) {
    String unescaped = GrStringUtil.unescapeSlashyString(text);
    GrStringUtil.escapeSymbolsForGString(unescaped, !multiline, false, builder);
  }

  /**
   * append text to builder if the operand is 'something'.toString()
   */
  private static boolean isToStringMethod(GrExpression operand, StringBuilder builder) {
    if (!(operand instanceof GrMethodCallExpression)) return false;

    final GrExpression expression = ((GrMethodCallExpression)operand).getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression refExpr)) return false;

    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) return false;

    final GroovyResolveResult[] results = refExpr.multiResolve(false);
    if (results.length != 1) return false;

    final PsiElement element = results[0].getElement();
    if (!(element instanceof PsiMethod method)) return false;

    final PsiClass objectClass =
      JavaPsiFacade.getInstance(operand.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, operand.getResolveScope());
    if (objectClass == null) return false;

    final PsiMethod[] toStringMethod = objectClass.findMethodsByName("toString", true);
    if (MethodSignatureUtil.isSubsignature(toStringMethod[0].getHierarchicalMethodSignature(), method.getHierarchicalMethodSignature())) {
      builder.append(START_BRACE).append(qualifier.getText()).append(END_BRACE);
      return true;
    }
    return false;
  }

  @ApiStatus.Internal
  public static class ConvertibleToGStringPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(@NotNull PsiElement element) {
      return satisfied(element);
    }

    public static boolean satisfied(PsiElement element) {
      if (isApplicableLiteral(element)) {
        return true;
      }

      if (!(element instanceof GrBinaryExpression binaryExpression)) return false;

      if (!GroovyTokenTypes.mPLUS.equals(binaryExpression.getOperationTokenType())) return false;

      if (ErrorUtil.containsError(element)) return false;

      final PsiType type = binaryExpression.getType();
      if (type == null) return false;

      final PsiClassType stringType = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, element);
      final PsiClassType gstringType = TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_GSTRING, element);
      if (!(TypeConversionUtil.isAssignable(stringType, type) || TypeConversionUtil.isAssignable(gstringType, type))) return false;

      return true;
    }

    private static boolean isApplicableLiteral(PsiElement element) {
      if (!(element instanceof GrLiteral literal)) return false;
      if (!(literal.getValue() instanceof String)) return false;
      IElementType literalType = GrLiteralImpl.getLiteralType(literal);
      return literalType != STRING_DQ && literalType != STRING_TDQ;
    }
  }
}
