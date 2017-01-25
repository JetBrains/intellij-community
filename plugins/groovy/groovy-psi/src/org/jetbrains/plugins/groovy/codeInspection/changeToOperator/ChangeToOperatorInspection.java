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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations.Transformation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import javax.swing.*;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle.message;
import static org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations.Transformations.TRANSFORMATIONS;

public class ChangeToOperatorInspection extends BaseInspection {
  public boolean useDoubleNegation = true;
  public boolean shouldChangeCompareToEqualityToEquals = true;

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCall) {
        final String methodName = getMethodName(methodCall);
        if (methodName == null) return;

        Transformation transformation = TRANSFORMATIONS.get(methodName);
        if (transformation == null) return;

        if (transformation.couldApply(methodCall, getOptions())) {
          registerError(
            methodCall,
            message("replace.with.operator.message", methodName),
            new LocalQuickFix[]{getFix(transformation, methodName)},
            GENERIC_ERROR_OR_WARNING
          );
        }
      }
    };
  }

  @Nullable
  protected GroovyFix getFix(@NotNull Transformation transformation, @NotNull String methodName) {
    return new GroovyFix() {
      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return message("replace.with.operator.fix", methodName);
      }

      @Override
      protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
        PsiElement call = descriptor.getPsiElement();
        if (!(call instanceof GrMethodCall)) return;
        GrMethodCall methodCall = (GrMethodCall) call;
        GrExpression invokedExpression = methodCall.getInvokedExpression();
        if (!(invokedExpression instanceof GrReferenceExpression)) return;

        Options options = getOptions();
        if(!transformation.couldApply(methodCall, options)) return;
        transformation.apply(methodCall, options);
      }
    };
  }

  @Nullable
  public String getMethodName(@NotNull GrMethodCall methodCall) {
    GrExpression invokedExpression = methodCall.getInvokedExpression();
    if (!(invokedExpression instanceof GrReferenceExpression)) return null;

    PsiElement element = ((GrReferenceExpression)invokedExpression).getReferenceNameElement();
    if (element == null) return null;

    PsiMethod method = methodCall.resolveMethod();
    if (method == null || method.hasModifierProperty(PsiModifier.STATIC)) return null;

    return method.getName();
  }


  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(message("replace.with.operator.double.negation.option"), "useDoubleNegation");
    optionsPanel.addCheckbox(message("replace.with.operator.compareTo.equality.option"), "shouldChangeCompareToEqualityToEquals");
    return optionsPanel;
  }

  private Options getOptions() {
    return new Options(useDoubleNegation, shouldChangeCompareToEqualityToEquals);
  }

  public static final class Options {
    private final boolean useDoubleNegation;
    private final boolean shouldChangeCompareToEqualityToEquals;

    public Options(boolean useDoubleNegation, boolean shouldChangeCompareToEqualityToEquals) {
      this.useDoubleNegation = useDoubleNegation;
      this.shouldChangeCompareToEqualityToEquals = shouldChangeCompareToEqualityToEquals;
    }

    public boolean useDoubleNegation() {
      return useDoubleNegation;
    }

    public boolean shouldChangeCompareToEqualityToEquals() {
      return shouldChangeCompareToEqualityToEquals;
    }
  }
}