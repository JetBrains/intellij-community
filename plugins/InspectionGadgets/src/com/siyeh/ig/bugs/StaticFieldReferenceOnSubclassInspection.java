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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class StaticFieldReferenceOnSubclassInspection
        extends ExpressionInspection{

    public String getID(){
        return "StaticFieldReferencedViaSubclass";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "static.field.via.subclass.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final PsiClass declaringClass = (PsiClass)infos[0];
        final PsiClass referencedClass = (PsiClass)infos[1];
        return InspectionGadgetsBundle.message(
                "static.field.via.subclass.problem.descriptor",
                declaringClass.getQualifiedName(), referencedClass.getQualifiedName());
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new StaticCallOnSubclassFix();
    }

    private static class StaticCallOnSubclassFix extends InspectionGadgetsFix{

        public String getName(){
            return InspectionGadgetsBundle.message(
                    "static.field.via.subclass.rationalize.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiIdentifier name = (PsiIdentifier) descriptor
                    .getPsiElement();
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) name.getParent();
            assert expression != null;
            final String fieldName = expression.getReferenceName();
            final PsiField field = (PsiField) expression.resolve();
            assert field != null;
            final PsiClass containingClass = field.getContainingClass();
            assert containingClass != null;
            final String containingClassName = containingClass.getName();
            replaceExpression(expression, containingClassName + '.' + fieldName);
        }

    }

    public BaseInspectionVisitor buildVisitor(){
        return new StaticCallOnSubclassVisitor();
    }

    private static class StaticCallOnSubclassVisitor
            extends BaseInspectionVisitor{

        public void visitReferenceExpression(PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            final PsiElement referent = expression.resolve();
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) referent;
            if(!field.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            final PsiElement qualifier = expression.getQualifier();
            if(!(qualifier instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement qualifierReferent =
                    ((PsiReference) qualifier).resolve();
            if(!(qualifierReferent instanceof PsiClass)){
                return;
            }
            final PsiClass referencedClass = (PsiClass) qualifierReferent;
            final PsiClass declaringClass = field.getContainingClass();
            if(declaringClass == null){
                return;
            }
            if(declaringClass.equals(referencedClass)){
                return;
            }
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(expression);
            if(!ClassUtils.isClassVisibleFromClass(containingClass,
                    declaringClass)){
                return;
            }
            final PsiElement identifier = expression.getReferenceNameElement();
            registerError(identifier, declaringClass, referencedClass);
        }
    }
}
