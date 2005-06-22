package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceConstantFix;
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
        return "\"Magic character\"";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "\"Magic character\" #ref in an internationalized context #loc";
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
