package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ParenthesesUtils;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryUnboxingInspection extends ExpressionInspection {
    private static final Map<String,String> s_unboxingMethods = new HashMap<String, String>(8);
    private final UnnecessaryUnboxingFix fix = new UnnecessaryUnboxingFix();

    static {
        s_unboxingMethods.put("java.lang.Integer", "intValue");
        s_unboxingMethods.put("java.lang.Short", "shortValue");
        s_unboxingMethods.put("java.lang.Boolean", "booleanValue");
        s_unboxingMethods.put("java.lang.Long", "longValue");
        s_unboxingMethods.put("java.lang.Byte", "byteValue");
        s_unboxingMethods.put("java.lang.Float", "floatValue");
        s_unboxingMethods.put("java.lang.Long", "longValue");
        s_unboxingMethods.put("java.lang.Double", "doubleValue");
        s_unboxingMethods.put("java.lang.Character", "charValue");
    }

    public String getDisplayName() {
        return "Unnecessary unboxing (J2SDK 5.0 only)";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "Unnecessary unboxing #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryUnboxingVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryUnboxingFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unboxing";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            final PsiElement parent = methodCall.getParent();
            if (parent instanceof PsiExpression) {
                final PsiExpression strippedQualifier =
                        ParenthesesUtils.stripParentheses(qualifier);
                final String strippedQualifierText = strippedQualifier.getText();
                if (ParenthesesUtils.getPrecendence(strippedQualifier) >
                        ParenthesesUtils.getPrecendence((PsiExpression) parent)) {
                    replaceExpression(project, methodCall, strippedQualifierText);
                } else {
                    replaceExpression(project, methodCall,
                            '(' + strippedQualifierText + ')');
                }
            } else {
                final PsiExpression strippedQualifier =
                        ParenthesesUtils.stripParentheses(qualifier);
                final String strippedQualiferText = strippedQualifier.getText();
                replaceExpression(project, methodCall, strippedQualiferText);
            }
        }
    }

    private static class UnnecessaryUnboxingVisitor extends BaseInspectionVisitor {
        private UnnecessaryUnboxingVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiManager manager = expression.getManager();
            final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
            if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiType qualifierType = qualifier.getType();
            if (qualifierType == null) {
                return;
            }
            final String qualifierTypeName = qualifierType.getCanonicalText();
            if (!s_unboxingMethods.containsKey(qualifierTypeName)) {
                return;
            }
            final String unboxingMethod = s_unboxingMethods.get(qualifierTypeName);
            if (!unboxingMethod.equals(methodName)) {
                return;
            }
            registerError(expression);
        }
    }

}
