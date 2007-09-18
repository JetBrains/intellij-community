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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableSearchUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryBlockStatementInspection extends BaseInspection{

    @NotNull
    public String getID(){
        return "UnnecessaryCodeBlock";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "unnecessary.code.block.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "unnecessary.block.statement.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessaryBlockStatementVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new UnnecessaryBlockFix();
    }

    private static class UnnecessaryBlockFix extends InspectionGadgetsFix{

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "unnecessary.code.block.unwrap.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement leftBrace = descriptor.getPsiElement();
            final PsiElement parent = leftBrace.getParent();
            if (!(parent instanceof PsiCodeBlock)) {
                return;
            }
            final PsiCodeBlock block = (PsiCodeBlock)parent;
            final PsiBlockStatement blockStatement =
                    (PsiBlockStatement)block.getParent();
            final PsiElement[] children = block.getChildren();
            if (children.length > 2) {
                final PsiElement element = blockStatement.getParent();
                element.addRangeBefore(children[1],
                        children[children.length - 2], blockStatement);
            }
            blockStatement.delete();
        }
    }

    private static class UnnecessaryBlockStatementVisitor
            extends BaseInspectionVisitor {

        public void visitBlockStatement(PsiBlockStatement blockStatement){
            super.visitBlockStatement(blockStatement);
            final PsiElement parent = blockStatement.getParent();
            if(!(parent instanceof PsiCodeBlock)){
                return;
            }
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            final PsiJavaToken brace = codeBlock.getLBrace();
            if(brace == null){
                return;
            }
            final PsiCodeBlock parentBlock = (PsiCodeBlock) parent;
            if(parentBlock.getStatements().length > 1 &&
                    VariableSearchUtils.containsConflictingDeclarations(
                            codeBlock, parentBlock)){
                return;
            }
            registerError(brace);
            final PsiJavaToken rbrace = codeBlock.getRBrace();
            if(rbrace != null){
                registerError(rbrace);
            }
        }
    }
}