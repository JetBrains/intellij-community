package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableSearchUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryThisInspection extends ExpressionInspection {
    private final UnnecessaryThisFix fix = new UnnecessaryThisFix();

    public String getDisplayName() {
        return "Unnecessary 'this' qualifier";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' is unnecessary in this context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryThisVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryThisFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary 'this.'";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement thisToken = descriptor.getPsiElement();
            final PsiReferenceExpression thisExpression = (PsiReferenceExpression) thisToken.getParent();
            assert thisExpression != null;
            final String newExpression = thisExpression.getReferenceName();
            replaceExpression(thisExpression, newExpression);
        }

    }

    private static class UnnecessaryThisVisitor extends BaseInspectionVisitor {

        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            final PsiReferenceParameterList parameterList =
                    expression.getParameterList();
            if(parameterList == null)
            {
                return;
            }
            if(parameterList.getTypeArguments().length > 0){
                return;
            }

            final PsiExpression qualifierExpression =
                    expression.getQualifierExpression();
            if(!(qualifierExpression instanceof PsiThisExpression)){
                return;
            }
            final PsiThisExpression thisExpression =
                    (PsiThisExpression) qualifierExpression;
            if(thisExpression.getQualifier() != null){
                return;
            }
            if(expression.getParent() instanceof PsiCallExpression){
                registerError(qualifierExpression);  // method calls are always in error
                return;
            }
            final String varName = expression.getReferenceName();
            if(varName == null){
                return;
            }
            if(!VariableSearchUtils.existsLocalOrParameter(varName,
                                                           expression)){
                registerError(thisExpression);
            }
        }
    }

}
