package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.TypeUtils;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryTemporaryOnConversionFromStringInspection extends ExpressionInspection {
    private static final Map s_basicTypeMap = new HashMap(6);
    private static final Map s_conversionMap = new HashMap(6);

    static {
        s_basicTypeMap.put("java.lang.Short", "shortValue");
        s_basicTypeMap.put("java.lang.Integer", "intValue");
        s_basicTypeMap.put("java.lang.Long", "longValue");
        s_basicTypeMap.put("java.lang.Float", "floatValue");
        s_basicTypeMap.put("java.lang.Double", "doubleValue");
        s_basicTypeMap.put("java.lang.Boolean", "booleanValue");

        s_conversionMap.put("java.lang.Short", "parseShort");
        s_conversionMap.put("java.lang.Integer", "parseInt");
        s_conversionMap.put("java.lang.Long", "parseLong");
        s_conversionMap.put("java.lang.Float", "parseFloat");
        s_conversionMap.put("java.lang.Double", "parseDouble");
        s_conversionMap.put("java.lang.Boolean", "valueOf");
    }

    public String getDisplayName() {
        return "Unnecessary temporary object in conversion from String";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        final String replacementString = calculateReplacementExpression(location);
        return "#ref can be simplified to " + replacementString + " #loc";
    }

    private static String calculateReplacementExpression(PsiElement location) {
        final PsiMethodCallExpression expression = (PsiMethodCallExpression) location;
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiNewExpression qualifier = (PsiNewExpression) methodExpression.getQualifierExpression();
        final PsiExpressionList argumentList = qualifier.getArgumentList();
        final PsiExpression arg = argumentList.getExpressions()[0];
        final PsiType type = qualifier.getType();
        final String qualifierType = type.getPresentableText();
        final String canonicalType = type.getCanonicalText();

        final String conversionName = (String) s_conversionMap.get(canonicalType);
        if (TypeUtils.typeEquals("java.lang.Boolean", type)) {
            return qualifierType + '.' + conversionName + '(' + arg.getText() + ").booleanValue()";
        } else {
            return qualifierType + '.' + conversionName + '(' + arg.getText() + ')';
        }
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new UnnecessaryTemporaryObjectFix((PsiMethodCallExpression) location);
    }

    private static class UnnecessaryTemporaryObjectFix extends InspectionGadgetsFix {
        private final String m_name;

        private UnnecessaryTemporaryObjectFix(PsiMethodCallExpression location) {
            super();
            m_name = "Replace with " + calculateReplacementExpression(location);
        }

        public String getName() {
            return m_name;
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(project, expression, newExpression);
        }

    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryTemporaryObjectVisitor(this, inspectionManager, onTheFly);
    }

    private static class UnnecessaryTemporaryObjectVisitor extends BaseInspectionVisitor {
        private UnnecessaryTemporaryObjectVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            final Map basicTypeMap = s_basicTypeMap;
            if(!basicTypeMap.containsValue(methodName)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(!(qualifier instanceof PsiNewExpression)){
                return;
            }
            final PsiNewExpression newExp = (PsiNewExpression) qualifier;
            final PsiExpressionList argList = newExp.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if(args.length != 1){
                return;
            }
            final PsiType argType = args[0].getType();
            if(!TypeUtils.isJavaLangString(argType)){
                return;
            }
            final PsiType type = qualifier.getType();
            if(type == null){
                return;
            }
            final String typeText = type.getCanonicalText();
            if(!basicTypeMap.containsKey(typeText)){
                return;
            }
            final Object mappingMethod = basicTypeMap.get(typeText);
            if(!mappingMethod.equals(methodName)){
                return;
            }
            registerError(expression);
        }
    }

}
