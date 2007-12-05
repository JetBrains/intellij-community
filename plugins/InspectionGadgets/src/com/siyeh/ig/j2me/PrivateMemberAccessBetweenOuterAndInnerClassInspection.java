/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrivateMemberAccessBetweenOuterAndInnerClassInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "private.member.access.between.outer.and.inner.classes.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "private.member.access.between.outer.and.inner.classes.problem.descriptor",
                infos[0]);
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        final PsiReferenceExpression reference =
                (PsiReferenceExpression) location;
        final PsiMember member = (PsiMember) reference.resolve();
        if (member == null) {
            return null;
        }
        final String memberName = member.getName();
        final PsiClass containingClass = member.getContainingClass();
        if (containingClass == null) {
            return null;
        }
        final String containingClassName = containingClass.getName();
        String elementName = containingClassName + '.' + memberName;
        return new MakePackagePrivateFix(elementName);
    }

    private static class MakePackagePrivateFix extends InspectionGadgetsFix{

        private String elementName;

        private MakePackagePrivateFix(String elementName){
            super();
            this.elementName = elementName;
        }

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "private.member.access.between.outer.and.inner.classes.make.local.quickfix",
                    elementName);
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiReferenceExpression reference =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final PsiModifierListOwner member =
                    (PsiModifierListOwner) reference.resolve();
            if (member == null) {
                return;
            }
            final PsiModifierList modifiers = member.getModifierList();
            if (modifiers == null) {
                return;
            }
            modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
            modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
            modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new PrivateMemberAccessFromInnerClassVisior();
    }

    private static class PrivateMemberAccessFromInnerClassVisior
            extends BaseInspectionVisitor{

        @Override public void visitReferenceExpression(
                @NotNull PsiReferenceExpression expression){
            if (PsiUtil.isInJspFile(expression)) {
                // disable for jsp files IDEADEV-12957
                return;
            }
            super.visitReferenceExpression(expression);
            final PsiElement containingClass =
                    getContainingContextClass(expression);
            if(containingClass == null){
                return;
            }
            final PsiElement element = expression.resolve();
            if(!(element instanceof PsiMethod || element instanceof PsiField)){
                return;
            }
            final PsiMember member = (PsiMember) element;
            if(!member.hasModifierProperty(PsiModifier.PRIVATE)){
                return;
            }
            final PsiClass memberClass =
                    ClassUtils.getContainingClass(member);
            if(memberClass == null){
                return;
            }
            if(memberClass.equals(containingClass)){
                return;
            }
            final String memberClassName = memberClass.getName();
            registerError(expression, memberClassName);
        }

        @Nullable
        private static PsiClass getContainingContextClass(
                PsiReferenceExpression expression){
            final PsiClass aClass =
                    ClassUtils.getContainingClass(expression);
            if(aClass instanceof PsiAnonymousClass){
                final PsiAnonymousClass anonymousClass =
                        (PsiAnonymousClass) aClass;
                final PsiExpressionList args = anonymousClass.getArgumentList();
                if(args!=null &&
                        PsiTreeUtil.isAncestor(args, expression, true)){
                    return ClassUtils.getContainingClass(aClass);
                }
            }
            return aClass;
        }
    }
}