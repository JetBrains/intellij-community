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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class UnnecessaryTemporaryOnConversionToStringInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.temporary.on.conversion.to.string.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final String replacementString =
                calculateReplacementExpression((PsiElement)infos[0]);
        return InspectionGadgetsBundle.message(
                "unnecessary.temporary.on.conversion.from.string.problem.descriptor",
                replacementString);
    }

    @Nullable @NonNls
    static String calculateReplacementExpression(
            PsiElement location) {
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) location;
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiNewExpression)) {
            return null;
        }
        final PsiNewExpression newExpression = (PsiNewExpression) qualifier;
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if(argumentList == null) {
            return null;
        }
        final PsiExpression[] expressions = argumentList.getExpressions();
        if(expressions.length < 1) {
            return null;
        }
        final PsiExpression arg = expressions[0];
        final PsiType type = newExpression.getType();
        if(type == null) {
            return null;
        }
        final String qualifierType = type.getPresentableText();
        return qualifierType + ".toString(" + arg.getText() + ')';
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        final String replacement = calculateReplacementExpression(location);
        final String name = InspectionGadgetsBundle.message(
                "unnecessary.temporary.on.conversion.from.string.fix.name",
                replacement);
        return new UnnecessaryTemporaryObjectFix(name);
    }

    private static class UnnecessaryTemporaryObjectFix
            extends InspectionGadgetsFix {

        private final String m_name;

        private UnnecessaryTemporaryObjectFix(
                String name) {
            m_name = name;
        }

        public String getName() {
            return m_name;
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression) descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            if (newExpression == null) {
                return;
            }
            replaceExpression(expression, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryTemporaryObjectVisitor();
    }

    private static class UnnecessaryTemporaryObjectVisitor
            extends BaseInspectionVisitor {

        /** @noinspection StaticCollection*/
        private static final Set<String> s_basicTypes = new HashSet<String>(8);

        static {
            s_basicTypes.add("java.lang.Boolean");
            s_basicTypes.add("java.lang.Byte");
            s_basicTypes.add("java.lang.Character");
            s_basicTypes.add("java.lang.Double");
            s_basicTypes.add("java.lang.Float");
            s_basicTypes.add("java.lang.Integer");
            s_basicTypes.add("java.lang.Long");
            s_basicTypes.add("java.lang.Short");
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(!(qualifier instanceof PsiNewExpression)) {
                return;
            }
            final PsiNewExpression newExpression = (PsiNewExpression)qualifier;
            final PsiExpressionList argumentList =
                    newExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] expressions = argumentList.getExpressions();
            if (expressions.length < 1) {
                return;
            }
            final PsiType type = qualifier.getType();
            if(type == null) {
                return;
            }
            final String typeName = type.getCanonicalText();
            if(!s_basicTypes.contains(typeName)) {
                return;
            }
            registerError(expression, expression);
        }
    }
}