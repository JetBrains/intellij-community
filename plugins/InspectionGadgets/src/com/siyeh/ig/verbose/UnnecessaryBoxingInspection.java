package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.*;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.*;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryBoxingInspection extends ExpressionInspection {
    private static final Map s_boxingArgs = new HashMap(8);
    private final UnnecessaryBoxingFix fix = new UnnecessaryBoxingFix();

    static {
        s_boxingArgs.put("java.lang.Integer", "int");
        s_boxingArgs.put("java.lang.Short", "short");
        s_boxingArgs.put("java.lang.Boolean", "boolean");
        s_boxingArgs.put("java.lang.Long", "long");
        s_boxingArgs.put("java.lang.Byte", "byte");
        s_boxingArgs.put("java.lang.Float", "float");
        s_boxingArgs.put("java.lang.Double", "double");
        s_boxingArgs.put("java.lang.Long", "long");
        s_boxingArgs.put("java.lang.Character", "char");
    }

    public String getDisplayName() {
        return "Unnecessary boxing (J2SDK 5.0 only)";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unnecessary boxing #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryBoxingVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessaryBoxingFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove boxing";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiNewExpression expression = (PsiNewExpression) descriptor.getPsiElement();
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            final String newExpression = args[0].getText();
            replaceExpression(project, expression, newExpression);
        }
    }

    private static class UnnecessaryBoxingVisitor extends BaseInspectionVisitor {
        private UnnecessaryBoxingVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiManager manager = expression.getManager();
            final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
            if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)) {
                return;
            }
            final PsiType constructorType = expression.getType();
            if (constructorType == null) {
                return;
            }
            final String constructorTypeText = constructorType.getCanonicalText();
            if (!s_boxingArgs.containsKey(constructorTypeText)) {
                return;
            }

            final PsiMethod constructor = expression.resolveConstructor();
            if (constructor == null) {
                return;
            }
            final PsiParameterList parameterList = constructor.getParameterList();

            if (parameterList == null) {
                return;
            }
            final PsiParameter[] args = parameterList.getParameters();
            if (args == null) {
                return;
            }
            if (args.length != 1) {
                return;
            }
            final PsiParameter arg = args[0];
            final PsiType argumentType = arg.getType();
            if (argumentType == null) {
                return;
            }

            final String argumentTypeText = argumentType.getCanonicalText();
            final Object boxableConstructorType = s_boxingArgs.get(constructorTypeText);
            if (!boxableConstructorType.equals(argumentTypeText)) {
                return;
            }
            registerError(expression);
        }
    }

}
