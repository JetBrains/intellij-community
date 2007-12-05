/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AssignmentToCatchBlockParameterInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "assignment.to.catch.block.parameter.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "assignment.to.catch.block.parameter.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new AssignmentToCatchBlockParameterFix();
    }

    private static class AssignmentToCatchBlockParameterFix
            extends InspectionGadgetsFix{

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "assignment.to.catch.block.parameter.extract.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiExpression variable =
                    (PsiExpression) descriptor.getPsiElement();
            final PsiCatchSection catchSection =
                    PsiTreeUtil.getParentOfType(variable,
                                                PsiCatchSection.class);
            assert catchSection != null;
            final PsiCodeBlock body = catchSection.getCatchBlock();
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            final PsiManager psiManager = PsiManager.getInstance(project);
            final CodeStyleManager codeStyleManager =
                    psiManager.getCodeStyleManager();
            final String originalVariableName = variable.getText();
            final SuggestedNameInfo suggestions =
                    JavaCodeStyleManager.getInstance(psiManager.getProject()).suggestVariableName(
                            VariableKind.LOCAL_VARIABLE,
                            originalVariableName + '1', variable, type);
            final String[] names = suggestions.names;
            @NonNls final String baseName;
            if(names != null && names.length > 0){
                baseName = names[0];
            } else{
                baseName = "value";
            }
            final String variableName =
                    JavaCodeStyleManager.getInstance(psiManager.getProject()).suggestUniqueVariableName(
                            baseName, catchSection, false);
            final String className = type.getPresentableText();
            assert body != null;
            final PsiElement[] children = body.getChildren();
            final StringBuffer buffer = new StringBuffer();
            for(int i = 1; i < children.length; i++){
                replaceVariableName(children[i], variableName,
                                    originalVariableName, buffer);
            }
            final String text = '{' + className + ' ' + variableName + " = " +
                                originalVariableName + ';' + buffer;
            final PsiElementFactory elementFactory =
                    psiManager.getElementFactory();
            final PsiCodeBlock block =
                    elementFactory.createCodeBlockFromText(text, null);
            body.replace(block);
            codeStyleManager.reformat(catchSection);
        }

        private static void replaceVariableName(
                PsiElement element, String newName, String originalName,
                StringBuffer out){
            final String text = element.getText();
            if(element instanceof PsiReferenceExpression){
                if(text.equals(originalName)){
                    out.append(newName);
                    return;
                }
            }
            final PsiElement[] children = element.getChildren();
            if(children.length == 0){
                out.append(text);
            } else{
                for(final PsiElement child : children){
                    replaceVariableName(child, newName, originalName, out);
                }
            }
        }

    }

    public BaseInspectionVisitor buildVisitor(){
        return new AssignmentToCatchBlockParameterVisitor();
    }

    private static class AssignmentToCatchBlockParameterVisitor
            extends BaseInspectionVisitor{

        @Override public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            if(!(lhs instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression reference =
                    (PsiReferenceExpression) lhs;
            final PsiElement variable = reference.resolve();
            if(!(variable instanceof PsiParameter)){
                return;
            }
            final PsiParameter parameter = (PsiParameter)variable;
            final PsiElement declarationScope = parameter.getDeclarationScope();
            if(!(declarationScope instanceof PsiCatchSection)){
                return;
            }
            registerError(lhs);
        }
    }
}