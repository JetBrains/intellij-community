// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * @author Bas Leijdekkers
 */
public class CopyConcatenatedStringToClipboardIntention extends MutablyNamedIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("copy.concatenated.string.to.clipboard.intention.family.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return element -> {
      final boolean isStringLiteral =
        element instanceof PsiLiteralExpression &&
        ExpressionUtils.hasStringType((PsiLiteralExpression)element) &&
        !(PsiTreeUtil.skipParentsOfType(element, PsiParenthesizedExpression.class) instanceof PsiPolyadicExpression);
      return isStringLiteral || ExpressionUtils.isStringConcatenation(element);
    };
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    return element instanceof PsiLiteralExpression
           ? IntentionPowerPackBundle.message("copy.string.literal.to.clipboard.intention.name")
           : IntentionPowerPackBundle.message("copy.concatenated.string.to.clipboard.intention.name");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final String text;
    if (element instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      final Object value = literalExpression.getValue();
      if (!(value instanceof String)) {
        return;
      }
      text = (String)value;
    }
    else {
      if (!ExpressionUtils.isStringConcatenation(element)) {
        return;
      }
      text = buildConcatenationText((PsiPolyadicExpression)element);
    }
    CopyPasteManager.getInstance().setContents(new StringSelection(text));
  }

  public static String buildConcatenationText(PsiPolyadicExpression polyadicExpression) {
    final StringBuilder out = new StringBuilder();
    for(PsiElement element = polyadicExpression.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiExpression) {
        final PsiExpression expression = (PsiExpression)element;
        final Object value = ExpressionUtils.computeConstantExpression(expression);
        out.append((value == null) ? "?" : value.toString());
      }
      else if (element instanceof PsiWhiteSpace && element.getText().contains("\n") &&
               (out.length() == 0 || out.charAt(out.length() - 1) != '\n')) {
        out.append('\n');
      }
    }
    return out.toString();
  }
}
