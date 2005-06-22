package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

public class ComparisonToNaNInspection extends ExpressionInspection{
    private final ComparisonToNaNFix fix = new ComparisonToNaNFix();

    public String getDisplayName(){
        return "Comparison to Double.NaN or Float.NaN";
    }

    public String getGroupDisplayName(){
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiBinaryExpression comparison =
                (PsiBinaryExpression) location.getParent();
        assert comparison != null;
        final PsiJavaToken sign = comparison.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(tokenType.equals(JavaTokenType.EQEQ)){
            return "Comparison to #ref is always false #loc";
        } else{
            return "Comparison to #ref is always true #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ComparisonToNaNVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ComparisonToNaNFix extends InspectionGadgetsFix{
        public String getName(){
            return "replace with call to isNaN()";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiReferenceExpression NaNExpression =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final String typeString = NaNExpression.getQualifier().getText();
            final PsiBinaryExpression comparison =
                    (PsiBinaryExpression) NaNExpression.getParent();

            final PsiExpression lhs = comparison.getLOperand();
            final PsiExpression rhs = comparison.getROperand();
            final PsiExpression qualifier;
            if(NaNExpression.equals(lhs)){
                qualifier = rhs;
            } else{
                qualifier = lhs;
            }

            assert qualifier != null;
            final String qualifierText = qualifier.getText();
            final PsiJavaToken sign = comparison.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String negationString;
            if(tokenType.equals(JavaTokenType.EQEQ)){
                negationString = "";
            } else{
                negationString = "!";
            }
            final String newExpressionText = negationString + typeString +
                    ".isNaN(" + qualifierText + ')';
            replaceExpression(comparison, newExpressionText);
        }
    }

    private static class ComparisonToNaNVisitor extends BaseInspectionVisitor{
        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            if(!ComparisonUtils.isEqualityComparison(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if(!isFloatingPointType(lhs) && !isFloatingPointType(rhs)){
                return;
            }
            if(isNaN(lhs)){
                registerError(lhs);
            } else if(isNaN(rhs)){
                registerError(rhs);
            }
        }

        private static boolean isFloatingPointType(PsiExpression expression){
            if(expression == null){
                return false;
            }
            final PsiType type = expression.getType();
            if(type == null){
                return false;
            }
            return PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type);
        }

        private static boolean isNaN(PsiExpression expression){
            if(!(expression instanceof PsiReferenceExpression)){
                return false;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            final String referenceName = referenceExpression.getReferenceName();
            if(!"NaN".equals(referenceName)){
                return false;
            }
            final PsiElement qualifier = referenceExpression.getQualifier();
            if(qualifier == null){
                return false;
            }
            final String qualifierText = qualifier.getText();
            return "Double".equals(qualifierText) ||
                    "Float" .equals(qualifierText);
        }
    }
}
