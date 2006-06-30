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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.JComponent;
import java.util.HashSet;
import java.util.Set;

public class MagicNumberInspection extends ExpressionInspection {

    @NonNls private static final String[] s_specialCaseLiteralArray =
            new String[]{
                    "0", "1", "2", "3", "4",
                    "5", "6", "7", "8", "9",
                    "10", "0L", "1L", "2L", "0l",
                    "1l", "2l", "0.0", "1.0", "0.0F",
                    "1.0F", "0.0f", "1.0f", "0.0d", "1.0d",
                    "0.0D", "1.0D"
            };

    /** @noinspection StaticCollection*/
    private static final Set<String> s_specialCaseLiterals =
            new HashSet<String>(27);

	/** @noinspection PublicField*/
    public boolean m_ignoreInHashCode = true;

    static {
        for(String string : s_specialCaseLiteralArray) {
            s_specialCaseLiterals.add(string);
        }
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("magic.number.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "magic.number.problem.descriptor");
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "magic.number.ignore.option"),
                this, "m_ignoreInHashCode");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new IntroduceConstantFix();
    }

    static boolean isSpecialCaseLiteral(PsiLiteralExpression expression) {
        final String text = expression.getText();
        return s_specialCaseLiterals.contains(text);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MagicNumberVisitor();
    }

    private  class MagicNumberVisitor extends BaseInspectionVisitor {

        public void visitLiteralExpression(
                @NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (!ClassUtils.isPrimitiveNumericType(type)) {
                return;
            }
            if (PsiType.CHAR.equals(type)) {
                return;
            }
            if (isSpecialCaseLiteral(expression)) {
                return;
            }
            if (isDeclaredConstant(expression)) {
                return;
            }
            if(m_ignoreInHashCode) {
                final PsiMethod containingMethod =
                        PsiTreeUtil.getParentOfType(expression,
                                                    PsiMethod.class);
                if(MethodUtils.isHashCode(containingMethod)) {
                    return;
                }
            }
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiPrefixExpression) {
                registerError(parent);
            } else {
                registerError(expression);
            }
        }

        private  boolean isDeclaredConstant(PsiLiteralExpression expression) {
            final PsiField field =
                    PsiTreeUtil.getParentOfType(expression, PsiField.class);
            if (field == null) {
                return false;
            }
            if (!field.hasModifierProperty(PsiModifier.STATIC) ||
                    !field.hasModifierProperty(PsiModifier.FINAL)) {
                return false;
            }
            final PsiExpression initializer = field.getInitializer();
            if (initializer == null) {
                return false;
            }
            final PsiType type = initializer.getType();
            return ClassUtils.isImmutable(type);
        }
    }
}