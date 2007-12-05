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
package com.siyeh.ig.internationalization;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import org.jetbrains.annotations.NotNull;

public class MagicCharacterInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("magic.character.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "magic.character.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new IntroduceConstantFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CharacterLiteralsShouldBeExplicitlyDeclaredVisitor();
    }

    private static class CharacterLiteralsShouldBeExplicitlyDeclaredVisitor
            extends BaseInspectionVisitor {

        @Override public void visitLiteralExpression(
                @NotNull PsiLiteralExpression expression) {
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
            if (text.equals(" ")) {
                return;
            }
            if (isDeclaredConstant(expression)) {
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isDeclaredConstant(
                PsiLiteralExpression expression) {
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