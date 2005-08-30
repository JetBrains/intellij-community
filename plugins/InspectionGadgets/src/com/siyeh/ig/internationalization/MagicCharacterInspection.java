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
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MagicCharacterInspection extends ExpressionInspection {

    private static final Set<String> s_specialCaseLiterals = new HashSet<String>(1);
    private final IntroduceConstantFix fix = new IntroduceConstantFix();

    static {
        s_specialCaseLiterals.add(" ");
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("magic.character.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("magic.character.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CharacterLiteralsShouldBeExplicitlyDeclaredVisitor();
    }

    private static class CharacterLiteralsShouldBeExplicitlyDeclaredVisitor extends BaseInspectionVisitor {

        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!type.equals(PsiType.CHAR)) {
                return;
            }
            final String text = expression.getText();
            if (text == null) {
                return;
            }
            if (isSpecialCase(text)) {
                return;
            }
            if (isDeclaredConstant(expression)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isSpecialCase(String text) {
            return s_specialCaseLiterals.contains(text);
        }

        private static boolean isDeclaredConstant(PsiLiteralExpression expression) {
            final PsiField field =
                    PsiTreeUtil.getParentOfType(expression, PsiField.class);
            if (field == null) {
                return false;
            }
            return field.hasModifierProperty(PsiModifier.STATIC) &&
                    field.hasModifierProperty(PsiModifier.FINAL);
        }
    }

}
