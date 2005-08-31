/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class CollectionAddedToSelfInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("collection.added.to.self.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }


    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("collection.added.to.self.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CollectionAddedToSelfVisitor();
    }

    private static class CollectionAddedToSelfVisitor extends BaseInspectionVisitor {
        private boolean inClass = false;

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
            @NonNls final String methodName = methodExpression.getReferenceName();
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
            final PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList == null) {
                return;
            }
            boolean hasMatchingArg=false;
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
