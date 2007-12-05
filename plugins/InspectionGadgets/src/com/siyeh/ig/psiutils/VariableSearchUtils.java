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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class VariableSearchUtils {

    private VariableSearchUtils() {}

    public static boolean existsLocalOrParameter(@NotNull String variableName,
                                                 @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        if (existsParameter(variableName, context)) {
            return true;
        }
        if (existsLocal(variableName, context)) {
            return true;
        }
        if (existsCatchSectionLocal(variableName, context)) {
            return true;
        }
        if (existsForLoopLocal(variableName, context)) {
            return true;
        }
        return existsForeachLoopLocal(variableName, context);
    }

    private static boolean existsParameter(@NotNull String variableName,
                                           PsiElement context) {
        PsiMethod ancestor =
                PsiTreeUtil.getParentOfType(context, PsiMethod.class);
        while (ancestor != null) {
            final PsiParameterList parameterList = ancestor.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            for (final PsiParameter parameter : parameters) {
                final String parameterName = parameter.getName();
                if (variableName.equals(parameterName)) {
                    return true;
                }
            }
            ancestor = PsiTreeUtil.getParentOfType(ancestor, PsiMethod.class);
        }
        return false;
    }

    private static boolean existsLocal(@NotNull String variableName,
                                       PsiElement context) {
        PsiCodeBlock ancestor =
                PsiTreeUtil.getParentOfType(context, PsiCodeBlock.class);
        while (ancestor != null) {
            final PsiStatement[] statements = ancestor.getStatements();
            for (final PsiStatement statement : statements) {
                if (statement instanceof PsiDeclarationStatement) {
                    final PsiDeclarationStatement declarationStatement =
                            (PsiDeclarationStatement) statement;
                    final PsiElement[] elements =
                            declarationStatement.getDeclaredElements();
                    for (PsiElement element : elements) {
                        if (!(element instanceof PsiLocalVariable)) {
                            continue;
                        }
                        final PsiLocalVariable localVariable =
                                (PsiLocalVariable) element;
                        final String localVariableName =
                                localVariable.getName();
                        if(variableName.equals(localVariableName)) {
                            return true;
                        }
                    }
                }
            }
            ancestor =
                    PsiTreeUtil.getParentOfType(ancestor, PsiCodeBlock.class);
        }
        return false;
    }

    private static boolean existsCatchSectionLocal(@NotNull String variableName,
                                                   PsiElement context) {
        PsiCatchSection catchSectionAncestor =
                PsiTreeUtil.getParentOfType(context, PsiCatchSection.class);
        while (catchSectionAncestor != null) {
            final PsiParameter parameter =
                    catchSectionAncestor.getParameter();
            if (parameter != null) {
                final String parameterName = parameter.getName();
                if (variableName.equals(parameterName)) {
                    return true;
                }
            }
            catchSectionAncestor =
                    PsiTreeUtil.getParentOfType(catchSectionAncestor,
                            PsiCatchSection.class);
        }
        return false;
    }

    private static boolean existsForLoopLocal(@NotNull String variableName, 
                                              PsiElement context) {
        PsiForStatement forLoopAncestor =
                PsiTreeUtil.getParentOfType(context, PsiForStatement.class);
        while (forLoopAncestor != null) {
            final PsiStatement initialization =
                    forLoopAncestor.getInitialization();
            if (initialization instanceof PsiDeclarationStatement) {
                final PsiDeclarationStatement declarationStatement =
                        (PsiDeclarationStatement) initialization;
                final PsiElement[] elements =
                        declarationStatement.getDeclaredElements();
                for (PsiElement element : elements) {
                    final PsiLocalVariable localVariable =
                            (PsiLocalVariable) element;
                    final String localVariableName = localVariable.getName();
                    if (variableName.equals(localVariableName)) {
                        return true;
                    }
                }
            }
            forLoopAncestor = PsiTreeUtil.getParentOfType(forLoopAncestor,
                    PsiForStatement.class);
        }
        return false;
    }

    private static boolean existsForeachLoopLocal(@NotNull String variableName,
                                                  PsiElement context) {
        PsiForeachStatement forLoopAncestor =
                PsiTreeUtil.getParentOfType(context, PsiForeachStatement.class);
        while (forLoopAncestor != null) {
            final PsiParameter parameter =
                    forLoopAncestor.getIterationParameter();
            final String parameterName = parameter.getName();
            if (variableName.equals(parameterName)) {
                return true;
            }
            forLoopAncestor = PsiTreeUtil.getParentOfType(forLoopAncestor,
                    PsiForeachStatement.class);
        }
        return false;
    }

    public static boolean containsConflictingDeclarations(
            PsiCodeBlock block, PsiCodeBlock parentBlock){
        final PsiStatement[] statements = block.getStatements();
        final Set<String> variableNames = new HashSet<String>();
        for(final PsiStatement statement : statements){
            if (!(statement instanceof PsiDeclarationStatement)) {
                continue;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement) statement;
            final PsiElement[] declaredElements =
                    declaration.getDeclaredElements();
            for(PsiElement declaredElement : declaredElements){
                if (!(declaredElement instanceof PsiLocalVariable)) {
                    continue;
                }
                final PsiLocalVariable variable =
                        (PsiLocalVariable)declaredElement;
                final String variableName = variable.getName();
                if (variableName == null) {
                    continue;
                }
                variableNames.add(variableName);
            }
        }
        final ConflictingDeclarationVisitor visitor =
                new ConflictingDeclarationVisitor(variableNames, block);
        parentBlock.accept(visitor);
        return visitor.hasConflictingDeclaration();
    }

    private static class ConflictingDeclarationVisitor
            extends PsiRecursiveElementVisitor{

        private final Set<String> variableNames;
        private final PsiCodeBlock exceptBlock;
        private boolean hasConflictingDeclaration = false;

        ConflictingDeclarationVisitor(@NotNull Set<String> variableNames,
                                      PsiCodeBlock exceptBlock){
            this.variableNames = variableNames;
            this.exceptBlock = exceptBlock;
        }

        @Override public void visitElement(@NotNull PsiElement element){
            if (hasConflictingDeclaration) {
                return;
            }
            super.visitElement(element);
        }

        @Override public void visitCodeBlock(PsiCodeBlock block){
            if(hasConflictingDeclaration){
                return;
            }
            if(block.equals(exceptBlock)){
                return;
            }
            super.visitCodeBlock(block);
        }

        @Override public void visitVariable(@NotNull PsiVariable variable){
            if(hasConflictingDeclaration){
                return;
            }
            super.visitVariable(variable);
            final String name = variable.getName();
            if(variableNames.contains(name)){
                hasConflictingDeclaration = true;
            }
        }

        public boolean hasConflictingDeclaration(){
            return hasConflictingDeclaration;
        }
    }
}