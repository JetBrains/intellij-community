// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;
import org.jetbrains.idea.devkit.inspections.quickfix.ChangeToPairCreateQuickFix;

import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
public class DontUseNewPairInspection extends DevKitInspectionBase {
  private static final String PAIR_FQN = "com.intellij.openapi.util.Pair";

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        final PsiType type = expression.getType();
        final PsiExpressionList params = expression.getArgumentList();
        if (PsiTypesUtil.classNameEquals(type, PAIR_FQN) && params != null
            && !PsiUtil.getLanguageLevel(expression).isAtLeast(LanguageLevel.JDK_1_7) //diamonds
        ) {
          final PsiType[] types = ((PsiClassType)type).getParameters();
          if (Arrays.equals(types, params.getExpressionTypes())) {
            final PsiJavaCodeReferenceElement reference = expression.getClassReference();
            if (reference != null) {
              holder.registerProblem(reference, DevKitBundle.message("inspections.dont.use.new.pair"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     new ChangeToPairCreateQuickFix());
            }
          }
        }
        super.visitNewExpression(expression);
      }
    };
  }

  @NotNull
  @Override
  public String getShortName() {
    return "DontUsePairConstructor";
  }
}

