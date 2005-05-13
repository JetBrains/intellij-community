package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AutoUnboxingInspection extends ExpressionInspection{
    /**
         * @noinspection StaticCollection
         */
    private static final Map<String,String> s_unboxingMethods = new HashMap<String, String>(8);
    /**
         * @noinspection StaticCollection
         */
    private static final Set<String> s_numberTypes = new HashSet<String>(8);
    private final AutoUnboxingFix fix = new AutoUnboxingFix();

    static{
        s_unboxingMethods.put("int", "intValue");
        s_unboxingMethods.put("short", "shortValue");
        s_unboxingMethods.put("boolean", "booleanValue");
        s_unboxingMethods.put("long", "longValue");
        s_unboxingMethods.put("byte", "byteValue");
        s_unboxingMethods.put("float", "floatValue");
        s_unboxingMethods.put("long", "longValue");
        s_unboxingMethods.put("double", "doubleValue");
        s_unboxingMethods.put("char", "charValue");

        s_numberTypes.add("java.lang.Integer");
        s_numberTypes.add("java.lang.Short");
        s_numberTypes.add("java.lang.Long");
        s_numberTypes.add("java.lang.Double");
        s_numberTypes.add("java.lang.Float");
        s_numberTypes.add("java.lang.Byte");
        s_numberTypes.add("java.lang.Character");
        s_numberTypes.add("java.lang.Number");
    }

    public String getDisplayName(){
        return "Auto-unboxing";
    }

    public String getGroupDisplayName(){
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Auto-unboxing #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new AutoUnboxingVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class AutoUnboxingFix extends InspectionGadgetsFix{
        public String getName(){
            return "Make unboxing explicit";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(descriptor)){
                return;
            }
            final PsiExpression expression =
                    (PsiExpression) descriptor.getPsiElement();
            final PsiType type = expression.getType();

            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression);

            final String expectedTypeText = expectedType.getCanonicalText();
            final String typeText = type.getCanonicalText();
            final String expressionText = expression.getText();
            final String boxClassName = s_unboxingMethods.get(expectedTypeText);
            if(TypeUtils.typeEquals("java.lang.Boolean", type)){
                replaceExpression(expression,
                                  expressionText + '.' + boxClassName + "()");
            } else if(s_numberTypes.contains(typeText)){
                replaceExpression(expression,
                                  expressionText + '.' + boxClassName + "()");
            } else{
                replaceExpression(expression,
                                  "((Number)" + expressionText + ")." + boxClassName + "()");
            }
        }
    }

    private static class AutoUnboxingVisitor extends BaseInspectionVisitor{
        private AutoUnboxingVisitor(BaseInspection inspection,
                                    InspectionManager inspectionManager,
                                    boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitExpression(PsiExpression expression){
            super.visitExpression(expression);
            final PsiType expressionType = expression.getType();
            if(expressionType == null){
                return;
            }
            if(expressionType.getArrayDimensions() > 0){
                return; // a horrible hack to get around what happens when you pass an array to a vararg expression
            }
            if(ClassUtils.isPrimitive(expressionType)){
                return;
            }
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression);

            if(expectedType == null){
                return;
            }
            if(!ClassUtils.isPrimitive(expectedType)){
                return;
            }
            registerError(expression);
        }
    }
}
