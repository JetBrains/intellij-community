package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
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
