/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.NotNull;

public class CastConflictsWithInstanceofInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "cast.conflicts.with.instanceof.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "cast.conflicts.with.instanceof.problem.descriptor");
    }

    @NotNull
    @Override
    protected InspectionGadgetsFix[] buildFixes(final Object... infos) {
        final PsiType castExpressionType = (PsiType)infos[0];
        final PsiInstanceOfExpression conflictingInstanceof =
                (PsiInstanceOfExpression)infos[1];
        final PsiTypeElement typeElement = conflictingInstanceof.getCheckType();
        return new InspectionGadgetsFix[] {
                new ReplaceCastFix(typeElement, castExpressionType),
                new ReplaceInstanceofFix(typeElement, castExpressionType)
        };
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new CastConflictsWithInstanceofVisitor();
    }

    private static class CastConflictsWithInstanceofVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitTypeCastExpression(
                @NotNull PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiType castType = expression.getType();
            if (castType == null) {
                return;
            }
            final PsiInstanceOfExpression conflictingInstanceof =
                    InstanceOfUtils.getConflictingInstanceof(expression);
            if (conflictingInstanceof == null) {
                return;
            }
            registerError(expression, castType, conflictingInstanceof);
        }
    }

    private static abstract class ReplaceFix extends InspectionGadgetsFix {

        protected final PsiTypeElement myInstanceofTypeElement;
        protected final PsiType myCastType;

        protected ReplaceFix(@NotNull PsiTypeElement instanceofTypeElement,
                             @NotNull PsiType castType) {
            myInstanceofTypeElement = instanceofTypeElement;
            myCastType = castType;
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression)descriptor.getPsiElement();
            final PsiTypeElement castTypeElement =
                    typeCastExpression.getCastType();
            if (castTypeElement == null) {
                return;
            }
            final PsiElement newElement =
                    replace(castTypeElement, myInstanceofTypeElement, project);
            final JavaCodeStyleManager codeStyleManager =
                    JavaCodeStyleManager.getInstance(project);
            codeStyleManager.shortenClassReferences(newElement);
        }

        protected abstract PsiElement replace(PsiTypeElement castTypeElement,
                                        PsiTypeElement instanceofTypeElement,
                                        Project project);

    }

    private static class ReplaceCastFix extends ReplaceFix {

        public ReplaceCastFix(PsiTypeElement instanceofTypeElement,
                              PsiType castType) {
            super(instanceofTypeElement, castType);
        }

        @Override
        protected PsiElement replace(PsiTypeElement castTypeElement,
                               PsiTypeElement instanceofTypeElement,
                               Project project) {
            return castTypeElement.replace(instanceofTypeElement);
        }

        @NotNull
        public String getName() {
            return "Replace cast to \'" +
                   myCastType.getPresentableText() + "\' with \'" +
                   myInstanceofTypeElement.getType().getPresentableText() + '\'';
        }
    }

    private static class ReplaceInstanceofFix extends ReplaceFix {

        public ReplaceInstanceofFix(PsiTypeElement instanceofTypeElement,
                                    PsiType castExpressionType) {
            super(instanceofTypeElement, castExpressionType);
        }

        @Override
        protected PsiElement replace(PsiTypeElement castTypeElement,
                               PsiTypeElement instanceofTypeElement,
                               Project project) {
            return instanceofTypeElement.replace(castTypeElement);
        }

        @NotNull
        public String getName() {
            return "Replace instanceof \'" +
                   myInstanceofTypeElement.getType().getPresentableText() +
                   "\' with \'" + myCastType.getPresentableText() + '\'';
        }
    }
}