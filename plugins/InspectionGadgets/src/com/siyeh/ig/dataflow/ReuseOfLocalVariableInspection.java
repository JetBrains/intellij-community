/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public class ReuseOfLocalVariableInspection
        extends ExpressionInspection{

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "reuse.of.local.variable.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.DATA_FLOW_ISSUES;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "reuse.of.local.variable.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new ReuseOfLocalVariableFix();
    }

    private static class ReuseOfLocalVariableFix
            extends InspectionGadgetsFix{

        public String getName(){
            return InspectionGadgetsBundle.message(
                    "reuse.of.local.variable.split.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiReferenceExpression ref =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final PsiLocalVariable variable = (PsiLocalVariable) ref.resolve();
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression) ref.getParent();
            assert assignment != null;
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) assignment.getParent();
            final PsiExpression lExpression = assignment.getLExpression();
            final String originalVariableName = lExpression.getText();
            assert variable != null;
            final PsiManager manager = variable.getManager();
            final PsiType type = variable.getType();
            final CodeStyleManager codeStyleManager =
                    manager.getCodeStyleManager();
            final PsiCodeBlock variableBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            final SuggestedNameInfo suggestions =
                    codeStyleManager.suggestVariableName(
                            VariableKind.LOCAL_VARIABLE,
                            originalVariableName,
                            lExpression,
                            type);
            final String[] names = suggestions.names;
            @NonNls final String baseName;
            if(names != null && names.length > 0){
                baseName = names[0];
            } else{
                baseName = "value";
            }
            final String newVariableName =
                    codeStyleManager.suggestUniqueVariableName(baseName,
                                                               variableBlock,
                                                               false);
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final PsiReference[] references =
                    searchHelper.findReferences(variable,
                            variable.getUseScope(), false);
            for (PsiReference reference : references){
                final PsiElement referenceElement = reference.getElement();
                if(referenceElement != null){
                    final TextRange textRange =
                            assignmentStatement.getTextRange();
                    if(referenceElement.getTextOffset() >
                       textRange.getEndOffset()){
                        replaceExpression((PsiExpression) referenceElement,
                                          newVariableName);
                    }
                }
            }
            final PsiExpression rhs = assignment.getRExpression();
            assert rhs != null;
            final String newStatement =
                    type.getPresentableText() + ' '
                    + newVariableName +
                    " =  " + rhs.getText()
                    + ';';
            replaceStatement(assignmentStatement, newStatement);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ReuseOfLocalVariableVisitor();
    }

    private static class ReuseOfLocalVariableVisitor
            extends BaseInspectionVisitor{

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment){
            super.visitAssignmentExpression(assignment);
            if(!WellFormednessUtils.isWellFormed(assignment)){
                return;
            }
            final PsiElement assignmentParent = assignment.getParent();
            if(!(assignmentParent instanceof PsiExpressionStatement)){
                return;
            }
            final PsiExpression lhs = assignment.getLExpression();
            if(!(lhs instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression ref = (PsiReferenceExpression) lhs;
            final PsiElement referent = ref.resolve();
            if(!(referent instanceof PsiLocalVariable)){
                return;
            }
            final PsiVariable variable = (PsiVariable) referent;

            //TODO: this is safe, but can be weakened
            if(variable.getInitializer()==null){
                return;
            }
            final PsiJavaToken sign = assignment.getOperationSign();

            final IElementType tokenType = sign.getTokenType();
            if(!JavaTokenType.EQ.equals(tokenType)){
                return;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if(VariableAccessUtils.variableIsUsed(variable, rhs)){
                return;
            }
            final PsiCodeBlock variableBlock =
                    PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if(variableBlock == null){
                return;
            }

            if(loopExistsBetween(assignment, variableBlock)){
                return;
            }
            if(tryExistsBetween(assignment, variableBlock)){
                // this could be weakened, slightly, if it could be verified
                // that a variable is used in only one branch of a try statement
                return;
            }
            final PsiElement assignmentBlock =
                    assignmentParent.getParent();
            if(assignmentBlock == null){
                return;
            }
            if(variableBlock.equals(assignmentBlock)){
                registerError(lhs);
            }
            final PsiStatement[] statements = variableBlock.getStatements();
            final PsiElement containingStatement =
                    getChildWhichContainsElement(variableBlock, assignment);
            int statementPosition = -1;
            for(int i = 0; i < statements.length; i++){
                if(statements[i].equals(containingStatement)){
                    statementPosition = i;
                    break;
                }
            }
            if(statementPosition == -1){
                return;
            }
            for(int i = statementPosition + 1; i < statements.length; i++){
                if(VariableAccessUtils.variableIsUsed(variable, statements[i])){
                    return;
                }
            }
            registerError(lhs);
        }

        private static boolean loopExistsBetween(
                PsiAssignmentExpression assignment, PsiCodeBlock block){
            PsiElement elementToTest = assignment;
            while(elementToTest != null){
                if(elementToTest.equals(block)){
                    return false;
                }
                if(elementToTest instanceof PsiWhileStatement ||
                   elementToTest instanceof PsiForeachStatement ||
                   elementToTest instanceof PsiForStatement ||
                   elementToTest instanceof PsiDoWhileStatement) {
                    return true;
                }
                elementToTest = elementToTest.getParent();
            }
            return false;
        }

        private static boolean tryExistsBetween(
                PsiAssignmentExpression assignment, PsiCodeBlock block) {
            PsiElement elementToTest = assignment;
            while(elementToTest != null){
                if(elementToTest.equals(block)){
                    return false;
                }
                if(elementToTest instanceof PsiTryStatement){
                    return true;
                }
                elementToTest = elementToTest.getParent();
            }
            return false;
        }

        /** @noinspection AssignmentToMethodParameter*/
        @Nullable
        public static PsiElement getChildWhichContainsElement(
                @NotNull PsiCodeBlock ancestor,
                @NotNull PsiElement descendant){
            PsiElement element = descendant;
            while(!element.equals(ancestor)){
                descendant = element;
                element = descendant.getParent();
                if(element == null){
                    return null;
                }
            }
            return descendant;
        }
    }
}