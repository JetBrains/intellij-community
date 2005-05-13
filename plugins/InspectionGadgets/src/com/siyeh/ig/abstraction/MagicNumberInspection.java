package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import com.siyeh.ig.psiutils.ClassUtils;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class MagicNumberInspection extends ExpressionInspection {

    private static final int NUM_SPECIAL_CASE_LITERALS = 22;
    private static final String[] s_specialCaseLiteralArray =
            new String[]{
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "0L",
                "1L", "2L", "0l", "1l", "2l", "0.0", "1.0", "0.0F", "1.0F", "0.0f", "1.0f"
            };
    /** @noinspection StaticCollection*/
    private static final Set<String> s_specialCaseLiterals = new HashSet<String>(NUM_SPECIAL_CASE_LITERALS);

    public boolean m_ignoreInHashCode = true;

    private final IntroduceConstantFix fix = new IntroduceConstantFix();

    static {
        for(String string : s_specialCaseLiteralArray){
            s_specialCaseLiterals.add(string);
        }
    }

    public String getDisplayName() {
        return "\"Magic number\"";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Magic number '#ref' #loc";
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore constants in hashCode() methods",
                                              this, "m_ignoreInHashCode");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MagicNumberVisitor(this, inspectionManager, onTheFly);
    }

    private  class MagicNumberVisitor extends BaseInspectionVisitor {
        private MagicNumberVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (!ClassUtils.isPrimitiveNumericType(type)) {
                return;
            }
            if (type.equals(PsiType.CHAR)) {
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
            if(m_ignoreInHashCode){
                final PsiMethod containingMethod =
                        PsiTreeUtil.getParentOfType(expression,
                                                    PsiMethod.class);
                if(containingMethod != null && "hashCode"
                        .equals(containingMethod.getName())){
                    final PsiParameterList parameterList =
                            containingMethod.getParameterList();
                    if(parameterList != null){
                        final PsiParameter[] parameters = parameterList.getParameters();
                        if(parameters != null && parameters.length == 0){
                            return;
                        }
                    }
                }
            }
            registerError(expression);
        }

        private  boolean isSpecialCase(String text) {
            return s_specialCaseLiterals.contains(text);
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
            final PsiType type = field.getType();
            return ClassUtils.isImmutable(type);
        }

    }

}
