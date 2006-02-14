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
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class UseOfPropertiesAsHashtableInspection extends ExpressionInspection{

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "properties.object.as.hashtable.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message(
                "properties.object.as.hashtable.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new SystemSetSecurityManagerVisitor();
    }

    private static class SystemSetSecurityManagerVisitor
                                                         extends BaseInspectionVisitor{

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!isHashtableMethod(methodName)){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(!ClassUtils.isSubclass(containingClass, "java.util.Hashtable")){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null){
                return;
            }
            if(!TypeUtils.expressionHasTypeOrSubtype("java.util.Properties",
                                                     qualifier)){
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isHashtableMethod(String name){
            return HardcodedMethodConstants.PUT.equals(name) ||
                    HardcodedMethodConstants.PUTALL.equals(name) ||
                    HardcodedMethodConstants.GET.equals(name);
        }
    }
}
