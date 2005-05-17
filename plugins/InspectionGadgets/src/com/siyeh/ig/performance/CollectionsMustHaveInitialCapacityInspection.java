package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NotNull;

public class CollectionsMustHaveInitialCapacityInspection extends ExpressionInspection {
    public String getID(){
        return "CollectionWithoutInitialCapacity";
    }

    public String getDisplayName() {
        return "Collection without initial capacity";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref without initial capacity #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CollectionInitialCapacityVisitor();
    }

    private static class CollectionInitialCapacityVisitor extends BaseInspectionVisitor {

        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();

            if (type == null) {
                return;
            }
            if (!CollectionUtils.isCollectionWithInitialCapacity(type)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] parameters = argumentList.getExpressions();
            if (parameters.length != 0) {
                return;
            }
            registerError(expression);
        }
    }

}
