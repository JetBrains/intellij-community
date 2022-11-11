// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.datetime;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static com.siyeh.ig.callMatcher.CallMatcher.*;

/**
 * @author Bas Leijdekkers
 */
public class ShowDateTimeExampleOutputIntention extends Intention implements HighPriorityAction {

  private static final CallMatcher DATE_TIME_FORMATTER_METHODS = anyOf(
    staticCall("java.time.format.DateTimeFormatter", "ofPattern"),
    instanceCall("java.time.format.DateTimeFormatterBuilder", "appendPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING)
  );
  private static final CallMatcher SIMPLE_DATE_FORMAT_METHODS =
    instanceCall("java.text.SimpleDateFormat", "applyPattern", "applyLocalizedPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING);
  Boolean dateTimeFormatter = null;

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("show.example.date.time.output.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiExpression)) {
          return false;
        }
        final PsiExpression expression = (PsiExpression)element;
        final PsiType type = expression.getType();
        if (!TypeUtils.isJavaLangString(type)) {
          return false;
        }
        final PsiElement grandParent = PsiUtil.skipParenthesizedExprUp(expression).getParent().getParent();
        if (grandParent instanceof PsiMethodCallExpression) {
          if (SIMPLE_DATE_FORMAT_METHODS.test((PsiMethodCallExpression)grandParent)) {
            dateTimeFormatter = false;
          }
          else if (DATE_TIME_FORMATTER_METHODS.test((PsiMethodCallExpression)grandParent)) {
            dateTimeFormatter = true;
          }
          else {
            return false;
          }
        }
        else if (grandParent instanceof PsiNewExpression) {
          final PsiNewExpression newExpression = (PsiNewExpression)grandParent;
          final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
          if (classReference == null || !"SimpleDateFormat".equals(classReference.getReferenceName())) {
            return false;
          }
          final PsiElement target = classReference.resolve();
          if (!(target instanceof PsiClass)) {
            return false;
          }
          final PsiClass aClass = (PsiClass)target;
          if (!InheritanceUtil.isInheritor(aClass, "java.text.SimpleDateFormat")) {
            return false;
          }
          dateTimeFormatter = false;
          return true;
        }
        else {
          return false;
        }
        final Object value = ExpressionUtils.computeConstantExpression(expression);
        return value instanceof String;
      }
    };
  }

  @Override
  protected void processIntention(Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiExpression) || dateTimeFormatter == null) {
      return;
    }
    final PsiExpression expression = (PsiExpression)element;
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    if (!(value instanceof String)) {
      return;
    }
    String example;
    if (dateTimeFormatter) {
      try {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern((String)value);
        //noinspection HardCodedStringLiteral
        example = LocalDateTime.now().format(formatter);
      }
      catch (IllegalArgumentException e) {
        example = IntentionPowerPackBundle.message("invalid.pattern.hint.text");
      }
    }
    else {
      try {
        final SimpleDateFormat format = new SimpleDateFormat((String)value);
        example = format.format(new Date());
      }
      catch (IllegalArgumentException e) {
        example = IntentionPowerPackBundle.message("invalid.pattern.hint.text");
      }
    }
    HintManager.getInstance().showInformationHint(editor, example);
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    assert false;
  }
}
