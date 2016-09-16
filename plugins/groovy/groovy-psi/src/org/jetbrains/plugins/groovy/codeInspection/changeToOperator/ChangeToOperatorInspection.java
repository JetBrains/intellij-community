/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.OptionsData;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.data.ReplacementData;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations.Transformation;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations.Transformations;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import javax.swing.*;
import java.util.Map;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle.message;

public class ChangeToOperatorInspection extends BaseInspection {
  private static final Map<String, Transformation> TRANSFORMATIONS = Transformations.get();

  public boolean useDoubleNegation = true;
  public boolean shouldChangeCompareToEqualityToEquals = true;

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCallExpression) {
        super.visitMethodCallExpression(methodCallExpression);
        processMethodCall(methodCallExpression);
      }

      private void processMethodCall(GrMethodCallExpression methodCallExpression) {
        PsiMethod method = methodCallExpression.resolveMethod();
        if (!isValid(method)) return;

        String methodName = method.getName();
        Transformation transformation = TRANSFORMATIONS.get(methodName);
        if (transformation == null) return;

        // TODO apply transformation recursively
        OptionsData optionsData = new OptionsData(useDoubleNegation, shouldChangeCompareToEqualityToEquals);
        ReplacementData replacement = transformation.transform(methodCallExpression, optionsData);
        if (replacement == null) return;

        GrExpression expression = replacement.getExpression();
        String text = expression.getText();
        GroovyFix fix = getFix(message("change.to.operator.fix", text), replacement.getReplacement());
        registerError(expression, message("change.to.operator.message", text), new LocalQuickFix[]{fix}, GENERIC_ERROR_OR_WARNING);
      }

      private boolean isValid(PsiModifierListOwner method) {
        return ((method != null) && !method.hasModifierProperty(PsiModifier.STATIC));
      }

      private GroovyFix getFix(@NotNull final String message, final String replacement) {
        return new GroovyFix() {
          @NotNull
          @Override
          public String getName() {
            return message;
          }

          @Override
          protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            replaceExpression((GrExpression)descriptor.getPsiElement(), replacement);
          }
        };
      }
    };
  }

  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(message("change.to.operator.double.negation.option"), "useDoubleNegation");
    optionsPanel.addCheckbox(message("change.to.operator.compareTo.equality.option"), "shouldChangeCompareToEqualityToEquals");
    return optionsPanel;
  }
}