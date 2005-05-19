package com.siyeh.ig.jdk;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;

import java.util.HashMap;
import java.util.Map;

public class AutoBoxingInspection extends ExpressionInspection {
    /** @noinspection StaticCollection*/
    private static final Map<String,String> s_boxingClasses = new HashMap<String, String>(8);
    private final AutoBoxingFix fix = new AutoBoxingFix();

    static {
        s_boxingClasses.put("int", "Integer");
        s_boxingClasses.put("short", "Short");
        s_boxingClasses.put("boolean", "Boolean");
        s_boxingClasses.put("long", "Long");
        s_boxingClasses.put("byte", "Byte");
        s_boxingClasses.put("float", "Float");
        s_boxingClasses.put("double", "Double");
        s_boxingClasses.put("char", "Character");
    }

    public String getDisplayName() {
        return "Auto-boxing";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Auto-boxing #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AutoBoxingVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class AutoBoxingFix extends InspectionGadgetsFix {
        public String getName() {
            return "Make boxing explicit";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiExpression expression = (PsiExpression) descriptor.getPsiElement();
            final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression);
            final String newExpression;
            if (expectedType.equals(PsiType.BOOLEAN)) {
                newExpression = "Boolean.valueOf(" + expression.getText() + ')';
            } else if (s_boxingClasses.containsValue(expectedType.getPresentableText())) {
                final String classToConstruct = expectedType.getPresentableText();
                newExpression = "new " + classToConstruct + '(' + expression.getText() + ')';
            } else {
                final String classToConstruct = s_boxingClasses.get(expression.getType().getPresentableText());
                newExpression = "new " + classToConstruct + '(' + expression.getText() + ')';
            }
            replaceExpression(expression, newExpression);
        }
    }

    private static class AutoBoxingVisitor extends BaseInspectionVisitor {

        public void visitExpression(PsiExpression expression) {
            super.visitExpression(expression);
            final PsiType expressionType = expression.getType();
            if(expressionType == null){
                return;
            }
            if(!ClassUtils.isPrimitive(expressionType)){
                return;
            }
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression);
            if(expectedType == null){
                return;
            }

            if(ClassUtils.isPrimitive(expectedType)){
                return;
            }
            registerError(expression);
        }

    }

}
