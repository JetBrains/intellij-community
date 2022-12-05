// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.integer;

import com.intellij.codeInsight.intention.numeric.AbstractNumberConversionIntention;
import com.intellij.codeInsight.intention.numeric.NumberConverter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.JavaPsiMathUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.immutableList;
import static com.siyeh.ipp.integer.JavaNumberConverters.*;

public class JavaNumberConversionIntention extends AbstractNumberConversionIntention {

  private static class Holder {
    static final List<NumberConverter> JAVA_1_CONVERTERS = immutableList(
      INTEGER_TO_DECIMAL, INTEGER_TO_HEX, INTEGER_TO_OCTAL, FLOAT_TO_DECIMAL, FLOAT_TO_PLAIN, FLOAT_TO_SCIENTIFIC);
    static final List<NumberConverter> JAVA_5_CONVERTERS = immutableList(
      INTEGER_TO_DECIMAL, INTEGER_TO_HEX, INTEGER_TO_OCTAL, FLOAT_TO_DECIMAL, FLOAT_TO_PLAIN, FLOAT_TO_SCIENTIFIC, FLOAT_TO_HEX);
    static final List<NumberConverter> JAVA_7_CONVERTERS = immutableList(
      INTEGER_TO_DECIMAL, INTEGER_TO_HEX, INTEGER_TO_BINARY, INTEGER_TO_OCTAL,
      FLOAT_TO_DECIMAL, FLOAT_TO_PLAIN, FLOAT_TO_SCIENTIFIC, FLOAT_TO_HEX);
  }

  @Override
  @Nullable
  @Contract(pure = true)
  protected NumberConversionContext extract(@NotNull PsiElement element) {
    if (element instanceof PsiJavaToken) {
      element = element.getParent();
    }
    PsiLiteralExpression literal = ObjectUtils.tryCast(element, PsiLiteralExpression.class);
    if (literal == null) return null;
    Number value = ObjectUtils.tryCast(literal.getValue(), Number.class);
    if (value == null) return null;
    if (ExpressionUtils.isNegative(literal)) {
      value = Objects.requireNonNull(JavaPsiMathUtil.negate(value));
      return new NumberConversionContext(element.getParent(), value, literal.getText(), true);
    }
    return new NumberConversionContext(element, value, literal.getText(), false);
  }

  @Override
  @NotNull
  @Contract(pure = true)
  protected List<NumberConverter> getConverters(@NotNull PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return Collections.emptyList();
    LanguageLevel level = PsiUtil.getLanguageLevel(file);
    if (level.isLessThan(LanguageLevel.JDK_1_5)) {
      return Holder.JAVA_1_CONVERTERS;
    }
    if (level.isLessThan(LanguageLevel.JDK_1_7)) {
      return Holder.JAVA_5_CONVERTERS;
    }
    return Holder.JAVA_7_CONVERTERS;
  }

  @Override
  protected void replace(PsiElement sourceElement, String replacement) {
    new CommentTracker().replaceAndRestoreComments(sourceElement, replacement);
  }
}
