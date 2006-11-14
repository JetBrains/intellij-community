/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThisEscapedInConstructorInspection extends ClassInspection{

    public String getID(){
        return "ThisEscapedInObjectConstruction";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "this.reference.escaped.in.construction.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "this.reference.escaped.in.construction.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ThisExposedInConstructorInspectionVisitor();
    }

    private static class ThisExposedInConstructorInspectionVisitor
            extends BaseInspectionVisitor{

        public void visitNewExpression(
                @NotNull PsiNewExpression newExpression){
            super.visitNewExpression(newExpression);
            final boolean isInInitialization =
                    checkForInitialization(newExpression);
            if(!isInInitialization){
                return;
            }
            final PsiJavaCodeReferenceElement refElement =
                    newExpression.getClassReference();
            if(refElement == null){
                return;
            }
            final PsiThisExpression thisExposed =
                    checkArgumentsForThis(newExpression);
            if(thisExposed == null){
                return;
            }
            final PsiClass constructorClass = (PsiClass)refElement.resolve();
            if(constructorClass != null){
                // Skips inner classes and containing classes (as well as
                // top level package class with file-named class)
                final PsiFile containingFile =
                        constructorClass.getContainingFile();
                if(containingFile.equals(
                        newExpression.getContainingFile())){
                    return;
                }
            }
            registerError(thisExposed);
        }

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment){
            super.visitAssignmentExpression(assignment);
            if(!WellFormednessUtils.isWellFormed(assignment)){
                return;
            }
            final boolean isInInitialization =
                    checkForInitialization(assignment);
            if(!isInInitialization){
                return;
            }
            final PsiExpression psiExpression =
                    getLastRightExpression(assignment);
            if(psiExpression == null ||
                       !(psiExpression instanceof PsiThisExpression)){
                return;
            }
            final PsiThisExpression thisExpression =
                    (PsiThisExpression) psiExpression;

            // Need to confirm that LeftExpression is outside of class relatives
            final PsiExpression lExpression = assignment.getLExpression();
            if(!(lExpression instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression leftExpression =
                    (PsiReferenceExpression) lExpression;
            if(!(leftExpression.resolve() instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) leftExpression.resolve();
            if(field == null){
                return;
            }
            final PsiFile containingFile = field.getContainingFile();
            if(containingFile.equals(assignment.getContainingFile())){
                return;
            }
            // Inheritance check
            final PsiClass cls = ClassUtils.getContainingClass(assignment);
            if(cls==null){
                return;
            }
            if(cls.isInheritor(field.getContainingClass(), true)){
                return;
            }
            registerError(thisExpression);
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final boolean isInInitialization = checkForInitialization(call);
            if(!isInInitialization){
                return;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final PsiMethod calledMethod = call.resolveMethod();
            if(calledMethod == null){
                return;
            }
            if(calledMethod.isConstructor()){
                return;
            }
            final PsiClass calledMethodClass =
                    calledMethod.getContainingClass();
            final PsiClass methodClass =
                    ClassUtils.getContainingClass(call);
            if(methodClass == null || calledMethodClass == null){
                return;
            }
            // compares class types statically?
            if(calledMethodClass.equals(methodClass)){
                return;
            }
            final PsiThisExpression thisExposed = checkArgumentsForThis(call);
            if(thisExposed == null){
                return;
            }

            // Methods - static or not - from superclasses don't trigger
            if(methodClass.isInheritor(calledMethodClass, true)){
                return;
            }

            // Make sure using this with members of self or superclasses
            // doesn't trigger
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(!(qualifier instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression qualifiedExpression =
                    (PsiReferenceExpression) qualifier;
            final PsiElement referent = qualifiedExpression.resolve();
            if(referent instanceof PsiField){
                final PsiField field = (PsiField) referent;
                final PsiClass containingClass = field.getContainingClass();

                if(methodClass.equals(containingClass) ||
                           methodClass.isInheritor(containingClass, true)){
                    return;
                }
            }
            registerError(thisExposed);
        }

        // Get rightmost expression of assignment. Used when assignments are
        // chained. Recursive
        @Nullable
        private static PsiExpression getLastRightExpression(
                PsiAssignmentExpression assignmentExp){
            if(assignmentExp == null){
                return null;
            }
            final PsiExpression expression = assignmentExp.getRExpression();
            if(expression == null){
                return null;
            }
            if(expression instanceof PsiAssignmentExpression){
                final PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression) expression;
                return getLastRightExpression(assignmentExpression);
            }
            return expression;
        }

        /**
         * @return true if CallExpression is in a constructor, instance
         *         initializer, or field initializaer. Otherwise it returns
         *         false
         */
        private static boolean checkForInitialization(PsiElement call){
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if (method != null) {
                return method.isConstructor();
            }
            final PsiField field =
                    PsiTreeUtil.getParentOfType(call, PsiField.class);
            if (field != null) {
                return true;
            }
            final PsiClassInitializer classInitializer =
                    PsiTreeUtil.getParentOfType(call, PsiClassInitializer.class);
            return classInitializer != null &&
                    !classInitializer.hasModifierProperty(PsiModifier.STATIC);
        }

        // If there are more than two of 'this' as arguments, only marks the
        // first until it is removed. No big deal.
        @Nullable
        private static PsiThisExpression checkArgumentsForThis(PsiCall call){
            final PsiExpressionList peList = call.getArgumentList();
            if(peList == null){   // array initializer
                return null;
            }
            final PsiExpression[] argExpressions = peList.getExpressions();
            for(final PsiExpression argExpression : argExpressions){
                if(argExpression instanceof PsiThisExpression){
                    return (PsiThisExpression) argExpression;
                }
            }
            return null;
        }
    }
}