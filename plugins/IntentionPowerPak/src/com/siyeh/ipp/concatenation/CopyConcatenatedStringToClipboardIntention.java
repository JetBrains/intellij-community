// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.codeInspection.ModCommands;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class CopyConcatenatedStringToClipboardIntention extends PsiBasedModCommandAction<PsiExpression> {
  public CopyConcatenatedStringToClipboardIntention() {
    super(PsiExpression.class);
  }
  
  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("copy.concatenated.string.to.clipboard.intention.family.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
    final boolean isStringLiteral =
      element instanceof PsiLiteralExpression &&
      ExpressionUtils.hasStringType(element) &&
      !(PsiTreeUtil.skipParentsOfType(element, PsiParenthesizedExpression.class) instanceof PsiPolyadicExpression);
    if (isStringLiteral) {
      return Presentation.of(IntentionPowerPackBundle.message("copy.string.literal.to.clipboard.intention.name"));
    }
    if (ExpressionUtils.isStringConcatenation(PsiTreeUtil.getNonStrictParentOfType(element, PsiPolyadicExpression.class))) {
      return Presentation.of(IntentionPowerPackBundle.message("copy.concatenated.string.to.clipboard.intention.name"));
    }
    return null;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiExpression element) {
    final String text;
    if (element instanceof PsiLiteralExpression literalExpression) {
      if (!(literalExpression.getValue() instanceof String string)) {
        return ModCommands.nop();
      }
      text = string;
    }
    else {
      PsiPolyadicExpression polyadic = PsiTreeUtil.getNonStrictParentOfType(element, PsiPolyadicExpression.class);
      if (!ExpressionUtils.isStringConcatenation(polyadic)) {
        return ModCommands.nop();
      }
      text = buildConcatenationText(polyadic);
    }
    return ModCommands.copyToClipboard(text);
  }

  public static String buildConcatenationText(PsiPolyadicExpression polyadicExpression) {
    final StringBuilder out = new StringBuilder();
    for(PsiElement element = polyadicExpression.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiExpression expression) {
        final Object value = ExpressionUtils.computeConstantExpression(expression);
        out.append((value == null) ? "?" : value.toString());
      }
      else if (element instanceof PsiWhiteSpace && element.getText().contains("\n") &&
               (out.isEmpty() || out.charAt(out.length() - 1) != '\n')) {
        out.append('\n');
      }
    }
    return out.toString();
  }
}
