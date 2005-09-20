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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ArrayEqualsInspection extends ExpressionInspection{

    private InspectionGadgetsFix fix = new ArrayEqualsFix();

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("equals.called.on.array.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
      return InspectionGadgetsBundle.message("equals.called.on.array.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ArrayEqualsFix extends InspectionGadgetsFix{

        public String getName(){
          return InspectionGadgetsBundle.message("equals.called.on.array.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiIdentifier name =
                    (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) name.getParent();
            assert expression != null;
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) expression.getParent();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String qualifierText = qualifier.getText();
            assert call != null;
            final PsiExpressionList argumentList = call.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();
            final String argText = args[0].getText();
            @NonNls final String newExpressionText =
                    "java.util.Arrays.equals(" + qualifierText + ", " +
                    argText + ')';
            replaceExpressionAndShorten(call, newExpressionText);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ArrayEqualsVisitor();
    }

    private static class ArrayEqualsVisitor extends BaseInspectionVisitor{

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(!IsEqualsUtil.isEquals(expression)){
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpressionList argumentList = expression.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();
            final PsiExpression arg = args[0];
            if(arg == null){
                return;
            }
            final PsiType argType = arg.getType();
            if(!(argType instanceof PsiArrayType)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null){
                return;
            }
            final PsiType qualifierType = qualifier.getType();
            if(!(qualifierType instanceof PsiArrayType)){
                return;
            }
            registerMethodCallError(expression);
        }
    }
}