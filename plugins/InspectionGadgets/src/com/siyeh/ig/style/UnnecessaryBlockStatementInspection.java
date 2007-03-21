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
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

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
                    containsConflictingDeclarations(codeBlock, parentBlock)){
                return;
            }
            registerError(brace);
            final PsiJavaToken rbrace = codeBlock.getRBrace();
            if(rbrace != null){
                registerError(rbrace);
            }
        }

        private static boolean containsConflictingDeclarations(
                PsiCodeBlock block, PsiCodeBlock parentBlock){
            final PsiStatement[] statements = block.getStatements();
            final Set<PsiElement> declaredVars = new HashSet<PsiElement>();
            for(final PsiStatement statement : statements){
                if(statement instanceof PsiDeclarationStatement){
                    final PsiDeclarationStatement declaration =
                            (PsiDeclarationStatement) statement;
                    final PsiElement[] vars = declaration.getDeclaredElements();
                    for(PsiElement var : vars){
                        if(var instanceof PsiLocalVariable){
                            declaredVars.add(var);
                        }
                    }
                }
            }
            for(Object declaredVar : declaredVars){
                final PsiLocalVariable variable =
                        (PsiLocalVariable) declaredVar;
                final String variableName = variable.getName();
                if(variableName != null &&
                        conflictingDeclarationExists(variableName, parentBlock,
                                block)){
                    return true;
                }
            }
            return false;
        }

        private static boolean conflictingDeclarationExists(
                @NotNull String name, PsiCodeBlock parentBlock,
                PsiCodeBlock exceptBlock){
            final ConflictingDeclarationVisitor visitor =
                    new ConflictingDeclarationVisitor(name, exceptBlock);
            parentBlock.accept(visitor);
            return visitor.hasConflictingDeclaration();
        }
    }

    private static class ConflictingDeclarationVisitor
            extends PsiRecursiveElementVisitor{

        private final String variableName;
        private final PsiCodeBlock exceptBlock;
        private boolean hasConflictingDeclaration = false;

        ConflictingDeclarationVisitor(@NotNull String variableName,
                                      PsiCodeBlock exceptBlock){
            super();
            this.variableName = variableName;
            this.exceptBlock = exceptBlock;
        }

        public void visitElement(@NotNull PsiElement element){
            if(!hasConflictingDeclaration){
                super.visitElement(element);
            }
        }

        public void visitCodeBlock(PsiCodeBlock block){
            if(hasConflictingDeclaration){
                return;
            }
            if(block.equals(exceptBlock)){
                return;
            }
            super.visitCodeBlock(block);
        }

        public void visitVariable(@NotNull PsiVariable variable){
            if(hasConflictingDeclaration){
                return;
            }
            super.visitVariable(variable);
            final String name = variable.getName();
            if(variableName.equals(name)){
                hasConflictingDeclaration = true;
            }
        }

        public boolean hasConflictingDeclaration(){
            return hasConflictingDeclaration;
        }
    }
}