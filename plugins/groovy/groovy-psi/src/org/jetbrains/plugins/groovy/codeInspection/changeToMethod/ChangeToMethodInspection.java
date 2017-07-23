/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations.Transformation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle.message;
import static org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations.Transformations.*;

/**
 * Replaces operator call with explicit method call.
 * GetAt and PutAt already covered by IndexedExpressionConversionIntention
 */
public class ChangeToMethodInspection extends BaseInspection {

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitExpression(@NotNull GrExpression expression) {
        Transformation<?> transformation = getTransformation(expression);
        if (transformation == null) return;
        PsiElement highlightingElement = getHighlightingElement(expression);
        if (highlightingElement == null) return;
        if (transformation.couldApplyRow(expression)) {
          registerError(
            highlightingElement,
            message("replace.with.method.message", transformation.getMethod()),
            new LocalQuickFix[]{getFix(transformation)},
            GENERIC_ERROR_OR_WARNING
          );
        }
      }
    };
  }

  @Nullable
  protected GroovyFix getFix(@NotNull Transformation<?> transformation) {

    return new GroovyFix() {
      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return message("replace.with.method.fix", transformation.getMethod());
      }

      @Override
      protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
        PsiElement call = descriptor.getPsiElement().getParent();
        if (!(call instanceof GrExpression)) return;
        if (!transformation.couldApplyRow((GrExpression)call)) return;

        transformation.applyRow((GrExpression)call);
      }
    };
  }

  @Nullable
  public Transformation<? extends GrExpression> getTransformation(@NotNull GrExpression expression) {
    if (expression instanceof GrUnaryExpression) {
      return UNARY_TRANSFORMATIONS.get(((GrUnaryExpression)expression).getOperationTokenType());
    }

    if (expression instanceof GrBinaryExpression) {
      return BINARY_TRANSFORMATIONS.get(((GrBinaryExpression)expression).getOperationTokenType());
    }

    if (expression instanceof GrSafeCastExpression) {
      return AS_TYPE_TRANSFORMATION;
    }
    return null;
  }

  @Nullable
  public PsiElement getHighlightingElement(@NotNull GrExpression expression) {
    if (expression instanceof GrUnaryExpression) {
      return ((GrUnaryExpression)expression).getOperationToken();
    }

    if (expression instanceof GrBinaryExpression) {
      return ((GrBinaryExpression)expression).getOperationToken();
    }

    if (expression instanceof GrSafeCastExpression) {
      return ((GrSafeCastExpression)expression).getOperationToken();
    }
    return null;
  }
}