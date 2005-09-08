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
package com.siyeh.ig.assignment;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class AssignmentToCatchBlockParameterInspection
        extends ExpressionInspection{

    private final AssignmentToCatchBlockParameterFix fix =
            new AssignmentToCatchBlockParameterFix();

    public String getDisplayName(){
        return "Assignment to catch block parameter";
    }

    public String getGroupDisplayName(){
        return GroupNames.ASSIGNMENT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Assignment to catch block parameter '#ref' #loc ";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class AssignmentToCatchBlockParameterFix
            extends InspectionGadgetsFix{

        public String getName(){
            return "Extract parameter as local variable";
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

            final PsiManager psiManager = PsiManager.getInstance(project);

            final CodeStyleManager codeStyleManager =
                    psiManager.getCodeStyleManager();
            final String originalVariableName = variable.getText();
            final SuggestedNameInfo suggestions =
                    codeStyleManager.suggestVariableName(
                            VariableKind.LOCAL_VARIABLE,
                            originalVariableName + '1', variable, type);
            final String[] names = suggestions.names;
            final String baseName;
            if(names != null && names.length > 0){
                baseName = names[0];
            } else{
                baseName = "value";
            }
            final String variableName =
                    codeStyleManager.suggestUniqueVariableName(baseName,
                                                               catchSection,
                                                               false);
            final String className = type.getPresentableText();
            assert body != null;
            final PsiElement[] children = body.getChildren();
            final StringBuffer buffer = new StringBuffer();
            for(int i = 1; i < children.length; i++){
                replaceVariableName(children[i], variableName,
                                    originalVariableName, buffer);
            }
            final String text = '{' + className + ' ' + variableName + " = " +
                    originalVariableName +
                    ';' +
                    buffer;

            final PsiElementFactory elementFactory =
                    psiManager.getElementFactory();
            final PsiCodeBlock block =
                    elementFactory.createCodeBlockFromText(text,
                                                           null);
            body.replace(block);
            codeStyleManager.reformat(catchSection);
        }

        private static void replaceVariableName(PsiElement element,
                                                String newName,
                                                String originalName,
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
                    replaceVariableName(child, newName,
                                        originalName, out);
                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AssignmentToCatchBlockParameterVisitor();
    }

    private static class AssignmentToCatchBlockParameterVisitor
            extends BaseInspectionVisitor{

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            if(!(lhs instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression ref = (PsiReferenceExpression) lhs;
            final PsiElement variable = ref.resolve();
            if(!(variable instanceof PsiParameter)){
                return;
            }
            if(!(((PsiParameter) variable)
                    .getDeclarationScope() instanceof PsiCatchSection)){
                return;
            }
            registerError(lhs);
        }
    }
}