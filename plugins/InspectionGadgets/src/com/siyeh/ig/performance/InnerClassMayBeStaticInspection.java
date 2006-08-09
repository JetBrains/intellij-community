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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class InnerClassMayBeStaticInspection extends BaseInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "inner.class.may.be.static.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "inner.class.may.be.static.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new InnerClassMayBeStaticFix();
    }

    private static class InnerClassMayBeStaticFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message("make.static.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiJavaToken classNameToken =
                    (PsiJavaToken)descriptor.getPsiElement();
            final PsiClass innerClass = (PsiClass)classNameToken.getParent();
            assert innerClass != null;
            final SearchScope useScope = innerClass.getUseScope();
            final Query<PsiReference> query =
                    ReferencesSearch.search(innerClass, useScope);
            final Collection<PsiReference> references = query.findAll();
            for (final PsiReference reference : references) {
                final PsiElement element = reference.getElement();
                final PsiElement parent = element.getParent();
                if (!(parent instanceof PsiNewExpression)) {
                    continue;
                }
                final PsiNewExpression newExpression =
                        (PsiNewExpression)parent;
                final PsiExpression qualifier =
                        newExpression.getQualifier();
                if (qualifier == null) {
                    continue;
                }
                qualifier.delete();
            }
            final PsiModifierList modifiers = innerClass.getModifierList();
            modifiers.setModifierProperty(PsiModifier.STATIC, true);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InnerClassCanBeStaticVisitor();
    }

    private static class InnerClassCanBeStaticVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (aClass.getContainingClass() != null &&
                    !aClass.hasModifierProperty(PsiModifier.STATIC)) {
                // inner class cannot have static declarations
                return;
            }
            final PsiClass[] innerClasses = aClass.getInnerClasses();
            for (final PsiClass innerClass : innerClasses) {
                if (innerClass.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }
                final InnerClassReferenceVisitor visitor =
                        new InnerClassReferenceVisitor(innerClass);
                innerClass.accept(visitor);
                if (!visitor.canInnerClassBeStatic()) {
                    continue;
                }
                registerClassError(innerClass);
            }
        }
    }
}