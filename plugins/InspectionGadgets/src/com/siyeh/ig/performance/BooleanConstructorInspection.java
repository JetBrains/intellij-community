/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class BooleanConstructorInspection extends BaseInspection {

    @Override
    @NotNull
    public String getID(){
        return "BooleanConstructorCall";
    }

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "boolean.constructor.display.name");
    }

    @Override
    public boolean isEnabledByDefault(){
        return true;
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "boolean.constructor.problem.descriptor");
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new BooleanConstructorVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos){
        return new BooleanConstructorFix();
    }

    private static class BooleanConstructorFix extends InspectionGadgetsFix{

        private static final String TRUE = '\"' + PsiKeyword.TRUE + '\"';
        private static final String FALSE = '\"' + PsiKeyword.FALSE + '\"';

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "boolean.constructor.simplify.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiNewExpression expression =
                    (PsiNewExpression) descriptor.getPsiElement();
            final PsiExpressionList argList = expression.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            final PsiExpression arg = args[0];
            final String text = arg.getText();
            final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
            @NonNls final String newExpression;
            if(PsiKeyword.TRUE.equals(text) ||
                    TRUE.equalsIgnoreCase(text)){
                newExpression = "Boolean.TRUE";
            } else if(PsiKeyword.FALSE.equals(text) ||
                    FALSE.equalsIgnoreCase(text)){
                newExpression = "Boolean.FALSE";
            } else if(languageLevel.equals(LanguageLevel.JDK_1_3)){
                final PsiType argType = arg.getType();
                if(PsiType.BOOLEAN.equals(argType)){
                    if(ParenthesesUtils.getPrecedence(arg) >
                            ParenthesesUtils.CONDITIONAL_PRECEDENCE){
                        newExpression = text + "?Boolean.TRUE:Boolean.FALSE";
                    } else{
                        newExpression =
                                '(' + text + ")?Boolean.TRUE:Boolean.FALSE";
                    }
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

        @Override public void visitNewExpression(@NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if (type == null || !type.equalsToText("java.lang.Boolean")){
                return;
            }
            final PsiClass aClass = ClassUtils.getContainingClass(expression);
            if(aClass!=null &&
                    "java.lang.Boolean".equals(aClass.getQualifiedName())){
                return;
            }
            registerError(expression);
        }
    }
}