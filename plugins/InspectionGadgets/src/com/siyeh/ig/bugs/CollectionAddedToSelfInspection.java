package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

public class CollectionAddedToSelfInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Collection added to self";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }


    public String buildErrorString(PsiElement location) {
        return "Collection '#ref' is added to self #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CollectionAddedToSelfVisitor(this, inspectionManager, onTheFly);
    }

    private static class CollectionAddedToSelfVisitor extends BaseInspectionVisitor {
        private boolean inClass = false;

        private CollectionAddedToSelfVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(@NotNull PsiClass aClass){
            if(!inClass)
            {
                inClass = true;
                super.visitClass(aClass);
                inClass = false;
            }
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"put".equals(methodName) &&
                        !"set".equals(methodName) &&
                        !"add".equals(methodName)) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if(qualifier ==null)
            {
                return;
            }
            if(!(qualifier instanceof PsiReferenceExpression))
            {
                return;
            }
            final PsiElement referent = ((PsiReference) qualifier).resolve();
            if(!(referent instanceof PsiVariable))
            {
                return;
            }
            boolean hasMatchingArg = false;
            final PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            for(PsiExpression arg : args){
                if(EquivalenceChecker.expressionsAreEquivalent(qualifier, arg)){
                    hasMatchingArg = true;
                }
            }
            if(!hasMatchingArg)
            {
                return ;
            }
            final PsiType qualifierType = qualifier.getType();
            if(!(qualifierType instanceof PsiClassType)){
                return ;
            }

            final PsiClass qualifierClass =
                    ((PsiClassType) qualifierType).resolve();
            if(qualifierClass == null)
            {
                return;
            }
            if(!ClassUtils.isSubclass(qualifierClass, "java.util.Collection") &&
                       !ClassUtils.isSubclass(qualifierClass,
                                              "java.util.Map")){

                return;
            }
            registerError(qualifier);
        }

    }

}
