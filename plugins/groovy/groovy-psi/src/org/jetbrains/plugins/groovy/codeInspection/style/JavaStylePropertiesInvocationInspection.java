/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.utils.JavaStylePropertiesUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class JavaStylePropertiesInvocationInspection extends BaseInspection {
  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
        super.visitMethodCallExpression(methodCallExpression);
        visitMethodCall(methodCallExpression);
      }

      @Override
      public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
        super.visitApplicationStatement(applicationStatement);
        visitMethodCall(applicationStatement);
      }

      private void visitMethodCall(GrMethodCall methodCall) {
        if (JavaStylePropertiesUtil.isPropertyAccessor(methodCall)) {
          final String message = GroovyInspectionBundle.message("java.style.property.access");
          final GrExpression expression = methodCall.getInvokedExpression();
          if (expression instanceof GrReferenceExpression) {
            PsiElement referenceNameElement = ((GrReferenceExpression)expression).getReferenceNameElement();
            registerError(referenceNameElement, message, myFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
      }
    };
  }

  private static final LocalQuickFix[] myFixes = new LocalQuickFix[]{new JavaStylePropertiesInvocationFixer()};
}
