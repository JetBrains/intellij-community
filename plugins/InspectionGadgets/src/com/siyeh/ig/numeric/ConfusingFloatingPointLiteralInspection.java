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
package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfusingFloatingPointLiteralInspection
        extends ExpressionInspection {

    @NonNls private static final Pattern pickyFloatingPointPattern =
            Pattern.compile("[0-9]+\\.[0-9]+((e|E)(-)?[0-9]+)?(f|F|d|D)?");

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "confusing.floating.point.literal.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "confusing.floating.point.literal.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ConfusingFloatingPointLiteralFix();
    }

    private static class ConfusingFloatingPointLiteralFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "confusing.floating.point.literal.change.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression literalExpression =
                    (PsiExpression)descriptor.getPsiElement();
            final String text = literalExpression.getText();
            final String newText = getCanonicalForm(text);
            replaceExpression(literalExpression, newText);
        }

        private static String getCanonicalForm(String text) {
            final String suffix;
            final String prefix;
            if (text.indexOf((int)'e') > 0) {
                final int breakPoint = text.indexOf((int)'e');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            }
            else if (text.indexOf((int)'E') > 0) {
                final int breakPoint = text.indexOf((int)'E');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            }
            else if (text.indexOf((int)'f') > 0) {
                final int breakPoint = text.indexOf((int)'f');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            }
            else if (text.indexOf((int)'F') > 0) {
                final int breakPoint = text.indexOf((int)'F');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            }
            else if (text.indexOf((int)'d') > 0) {
                final int breakPoint = text.indexOf((int)'d');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            }
            else if (text.indexOf((int)'D') > 0) {
                final int breakPoint = text.indexOf((int)'D');
                suffix = text.substring(breakPoint);
                prefix = text.substring(0, breakPoint);
            }
            else {
                suffix = "";
                prefix = text;
            }
            final int indexPoint = prefix.indexOf((int)'.');
            if (indexPoint < 0) {
                return prefix + ".0" + suffix;
            }
            else if (indexPoint == 0) {
                return '0' + prefix + suffix;
            }
            else {
                return prefix + '0' + suffix;
            }

        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConfusingFloatingPointLiteralVisitor();
    }

    private static class ConfusingFloatingPointLiteralVisitor
            extends BaseInspectionVisitor {

        public void visitLiteralExpression(
                @NotNull PsiLiteralExpression literal) {
            super.visitLiteralExpression(literal);
            final PsiType type = literal.getType();
            if (type == null) {
                return;
            }
            if (!(type.equals(PsiType.FLOAT) || type.equals(PsiType.DOUBLE))) {
                return;
            }
            final String text = literal.getText();
            if (text == null) {
                return;
            }
            if (!isConfusing(text)) {
                return;
            }
            registerError(literal);
        }

        private static boolean isConfusing(String text) {
            final Matcher matcher = pickyFloatingPointPattern.matcher(text);
            return !matcher.matches();
        }
    }
}