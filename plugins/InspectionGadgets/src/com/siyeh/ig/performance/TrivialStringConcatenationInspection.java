package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.TypeUtils;

import java.util.HashMap;
import java.util.Map;

public class TrivialStringConcatenationInspection extends ExpressionInspection {
    private static final Map s_typeToWrapperMap = new HashMap(6);

    static {
        s_typeToWrapperMap.put("short", "Short");
        s_typeToWrapperMap.put("int", "Integer");
        s_typeToWrapperMap.put("long", "Long");
        s_typeToWrapperMap.put("float", "Float");
        s_typeToWrapperMap.put("double", "Double");
        s_typeToWrapperMap.put("boolean", "Boolean");
        s_typeToWrapperMap.put("byte", "Byte");
    }

    public String getDisplayName() {
        return "Concatenation with empty string";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final String replacementString = calculateReplacementExpression(location);
        return "#ref can be simplified to " + replacementString + " #loc";
    }

    private static String calculateReplacementExpression(PsiElement location) {
        final PsiBinaryExpression expression = (PsiBinaryExpression) location;
        final PsiExpression lOperand = expression.getLOperand();
        final PsiExpression rOperand = expression.getROperand();
        final PsiExpression replacement;
        if (isEmptyString(lOperand)) {
            replacement = rOperand;
        } else {

            replacement = lOperand;
        }
        final PsiType type = replacement.getType();
        final String text = type.getCanonicalText();
        if (s_typeToWrapperMap.containsKey(text)) {
            return s_typeToWrapperMap.get(text) + ".toString(" + replacement.getText() + ')';
        } else {
            return replacement.getText();
        }

    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new UnnecessaryTemporaryObjectFix((PsiBinaryExpression) location);
    }

    private static class UnnecessaryTemporaryObjectFix extends InspectionGadgetsFix {
        private final String m_name;

        private UnnecessaryTemporaryObjectFix(PsiBinaryExpression expression) {
            super();
            m_name = "Replace  with " + calculateReplacementExpression(expression);
        }

        public String getName() {
            return m_name;
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiBinaryExpression expression = (PsiBinaryExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(project, expression, newExpression);
        }

    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TrivialStringConcatenationVisitor(this, inspectionManager, onTheFly);
    }

    private static class TrivialStringConcatenationVisitor extends BaseInspectionVisitor {
        private TrivialStringConcatenationVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression exp) {
            super.visitBinaryExpression(exp);
            if (!TypeUtils.expressionHasType("java.lang.String", exp)) {
                return;
            }
            final PsiExpression lhs = exp.getLOperand();
            if (lhs == null) {
                return;
            }
            final PsiExpression rhs = exp.getROperand();
            if (rhs == null) {
                return;
            }
            if (!isEmptyString(lhs) && !isEmptyString(rhs)) {
                return;
            }
            registerError(exp);
        }
    }

    private static boolean isEmptyString(PsiExpression exp) {

        final String text = exp.getText();
        return "\"\"".equals(text);
    }

}
