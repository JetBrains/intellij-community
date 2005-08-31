/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NonNls;

public class AutoUnboxingInspection extends ExpressionInspection{
    /**
         * @noinspection StaticCollection
         */
    @NonNls private static final Map<String,String> s_unboxingMethods = new HashMap<String, String>(8);
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
      return InspectionGadgetsBundle.message("auto.unboxing.display.name");
  }

    public String getGroupDisplayName(){
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("auto.unboxing.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AutoUnboxingVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class AutoUnboxingFix extends InspectionGadgetsFix{
        public String getName(){
            return InspectionGadgetsBundle.message("auto.unboxing.make.unboxing.explicit.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiExpression expression =
                    (PsiExpression) descriptor.getPsiElement();
            final PsiType type = expression.getType();

            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, false);
            assert expectedType != null;
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
              @NonNls final String numberKlass = "Number";
              replaceExpression(expression,
                                "((" + numberKlass + ")" + expressionText + ")." + boxClassName + "()");
            }
        }
    }

    private static class AutoUnboxingVisitor extends BaseInspectionVisitor{

        public void visitConditionalExpression(PsiConditionalExpression expression)
        {
            super.visitConditionalExpression(expression);
            checkExpression(expression);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression){
            super.visitReferenceExpression(expression);
            checkExpression(expression);
        }

        public void visitNewExpression(PsiNewExpression expression){
            super.visitNewExpression(expression);
            checkExpression(expression);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression)
        {
            super.visitMethodCallExpression(expression);
            checkExpression(expression);
        }

        public void visitTypeCastExpression(PsiTypeCastExpression expression){
            super.visitTypeCastExpression(expression);
            checkExpression(expression);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression)
        {
            super.visitAssignmentExpression(expression);
            checkExpression(expression);
        }

        public void visitParenthesizedExpression(PsiParenthesizedExpression expression)
        {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(PsiExpression expression){
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
                    ExpectedTypeUtils.findExpectedType(expression, false);

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
