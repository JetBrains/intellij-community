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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class BooleanConstructorInspection extends ExpressionInspection{
    private final BooleanConstructorFix fix = new BooleanConstructorFix();

    public String getID(){
        return "BooleanConstructorCall";
    }

    public String getDisplayName(){
        return "Boolean constructor call";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "Boolean constructor call #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new BooleanConstructorVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class BooleanConstructorFix extends InspectionGadgetsFix{
        public String getName(){
            return "Simplify";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiNewExpression expression =
                    (PsiNewExpression) descriptor.getPsiElement();
            final PsiExpressionList argList = expression.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            final PsiExpression arg = args[0];
            final String text = arg.getText();
            final PsiManager psiManager = expression.getManager();
            final LanguageLevel languageLevel =
                    psiManager.getEffectiveLanguageLevel();
            final String newExpression;
            if(PsiKeyword.TRUE.equals(text) || "\"true\"".equalsIgnoreCase(text)){
                newExpression = "Boolean.TRUE";
            } else if(PsiKeyword.FALSE.equals(text) || "\"false\"".equalsIgnoreCase(text)){
                newExpression = "Boolean.FALSE";
            } else if(languageLevel.equals(LanguageLevel.JDK_1_3)){
                final PsiType argType = arg.getType();
                if(PsiType.BOOLEAN.equals(argType)){
                    if(ParenthesesUtils.getPrecendence(arg)> ParenthesesUtils.CONDITIONAL_PRECEDENCE)
                    newExpression =   text + "?Boolean.TRUE:Boolean.FALSE";
                    else
                        newExpression = '(' +text + ")?Boolean.TRUE:Boolean.FALSE";

                } else{
                    newExpression = "Boolean.valueOf(" + text + ')';
                }
            } else{
                newExpression = "Boolean.valueOf(" + text + ')';
            }
            replaceExpression(expression, newExpression);
        }
    }

    private static class BooleanConstructorVisitor
            extends BaseInspectionVisitor{

        public void visitNewExpression(@NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if(!TypeUtils.typeEquals("java.lang.Boolean", type)){
                return;
            }
            final PsiClass aClass = ClassUtils.getContainingClass(expression);
            if(aClass!=null && "java.lang.Boolean".equals(aClass.getQualifiedName()))
            {
                return;
            }
            registerError(expression);
        }
    }
}
