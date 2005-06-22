package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class SimpleDateFormatWithoutLocaleInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Instantiating a SimpleDateFormat without a Locale";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Instantiating a #ref without specifying a Locale in an internationalized context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SimpleDateFormatWithoutLocaleVisitor();
    }

    private static class SimpleDateFormatWithoutLocaleVisitor extends BaseInspectionVisitor {

        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if(!TypeUtils.expressionHasType("java.util.SimpleDateFormat", expression))
            {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null)
            {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null)
            {
                return;
            }
            for(PsiExpression arg : args){
                if(TypeUtils.expressionHasType("java.util.Locale", arg)){
                    return;
                }
            }
            final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
            registerError(classReference);
        }
    }

}
