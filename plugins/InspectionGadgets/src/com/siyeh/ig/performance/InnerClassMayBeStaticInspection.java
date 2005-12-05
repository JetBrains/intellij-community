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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class InnerClassMayBeStaticInspection extends ClassInspection {

    private final InnerClassMayBeStaticFix fix = new InnerClassMayBeStaticFix();

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class InnerClassMayBeStaticFix extends InspectionGadgetsFix {
        public String getName() {
            return InspectionGadgetsBundle.message("make.static.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiJavaToken classNameToken =
                    (PsiJavaToken)descriptor.getPsiElement();
            final PsiClass innerClass = (PsiClass)classNameToken.getParent();
            assert innerClass != null;
            final PsiManager manager = innerClass.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final SearchScope useScope = innerClass.getUseScope();
            final PsiReference[] references =
                    searchHelper.findReferences(innerClass, useScope, false);
            for (final PsiReference reference : references) {
                final PsiElement element = reference.getElement();
                final PsiElement parent = element.getParent();
                if (parent instanceof PsiNewExpression) {
                    final PsiNewExpression newExpression =
                            (PsiNewExpression)parent;
                    final PsiExpression qualifier =
                            newExpression.getQualifier();
                    if (qualifier != null) {
                        qualifier.delete();
                    }
                }
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
                if (!innerClass.hasModifierProperty(PsiModifier.STATIC)) {
                    final InnerClassReferenceVisitor visitor =
                            new InnerClassReferenceVisitor(innerClass);
                    innerClass.accept(visitor);
                    if (visitor.canInnerClassBeStatic()) {
                        registerClassError(innerClass);
                    }
                }
            }
        }
    }
}