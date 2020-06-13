// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Max Medvedev
 */
public final class GrInspectionUtil {
  public static boolean isNull(@NotNull GrExpression expression) {
    return "null".equals(expression.getText());
  }

  public static boolean isEquality(@NotNull GrBinaryExpression binaryCondition) {
    final IElementType tokenType = binaryCondition.getOperationTokenType();
    return GroovyTokenTypes.mEQUAL == tokenType;
  }

  public static boolean isInequality(@NotNull GrBinaryExpression binaryCondition) {
    final IElementType tokenType = binaryCondition.getOperationTokenType();
    return GroovyTokenTypes.mNOT_EQUAL == tokenType;
  }

  public static HighlightInfo createAnnotationForRef(@NotNull GrReferenceElement ref,
                                                     @NotNull HighlightDisplayLevel displayLevel,
                                                     @NotNull String message) {
    PsiElement refNameElement = ref.getReferenceNameElement();
    assert refNameElement != null;

    if (displayLevel == HighlightDisplayLevel.ERROR) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refNameElement).descriptionAndTooltip(message).create();
    }

    if (displayLevel == HighlightDisplayLevel.WEAK_WARNING) {
      boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
      HighlightInfoType infotype = isTestMode ? HighlightInfoType.WARNING : HighlightInfoType.INFORMATION;

      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(infotype).range(refNameElement);
      builder.descriptionAndTooltip(message);
      return builder.needsUpdateOnTyping(false).textAttributes(GroovySyntaxHighlighter.UNRESOLVED_ACCESS).create();
    }

    HighlightInfoType highlightInfoType = HighlightInfo.convertSeverity(displayLevel.getSeverity());
    return HighlightInfo.newHighlightInfo(highlightInfoType).range(refNameElement).descriptionAndTooltip(message).create();
  }

  public static void replaceExpression(GrExpression expression, String newExpression) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());
    final GrExpression newCall = factory.createExpressionFromText(newExpression, expression.getContext());
    expression.replaceWithExpression(newCall, true);
  }
}
