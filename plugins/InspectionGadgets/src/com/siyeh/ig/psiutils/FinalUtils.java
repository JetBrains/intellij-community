/*
 * Copyright 2009-2010 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FinalUtils {

    private FinalUtils() {
    }

    public static boolean canFieldBeFinal(PsiField field) {
        if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
            return false;
        }
        if (field.hasModifierProperty(PsiModifier.FINAL)) {
            return false;
        }
        final ImplicitUsageProvider[] implicitUsageProviders =
                Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
        for(ImplicitUsageProvider provider: implicitUsageProviders){
            if(provider.isImplicitWrite(field)){
                return false;
            }
        }
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            if (!staticFieldMayBeFinal(field)) {
                return false;
            }
        } else {
            if (!fieldMayBeFinal(field)) {
                return false;
            }
        }
        return true;
    }

    private static boolean fieldMayBeFinal(PsiField field) {
        final PsiClass aClass = field.getContainingClass();
        if (aClass == null) {
            return false;
        }
        final PsiExpression initializer = field.getInitializer();
        final PsiClassInitializer[] classInitializers =
                aClass.getInitializers();
        boolean assignedInInitializer = initializer != null;
        boolean isInitialized = assignedInInitializer;
        for (PsiClassInitializer classInitializer : classInitializers) {
            if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (VariableAccessUtils.variableIsAssigned(field,
                    classInitializer, false)) {
                if (assignedInInitializer) {
                    return false;
                } else if (variableIsAssignedOnceAndOnlyOnce(field,
                        classInitializer)){
                    isInitialized = true;
                }
                assignedInInitializer = true;
            }
        }
        final PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (method.isConstructor() && !assignedInInitializer) {
                if (!VariableAccessUtils.variableIsAssigned(field, method,
                        false)) {
                    return false;
                } else if (variableIsAssignedOnceAndOnlyOnce(field, method)){
                    isInitialized = true;
                }
            } else if (VariableAccessUtils.variableIsAssigned(field, method,
                    false)) {
                return false;
            }
        }
        final PsiField[] fields = aClass.getFields();
        for (PsiField aField : fields) {
            if (aField.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (aField.equals(field)) {
                continue;
            }
            final PsiExpression expression = aField.getInitializer();
            if (expression == null) {
                continue;
            }
            if (VariableAccessUtils.variableIsAssigned(field, expression)) {
                if (assignedInInitializer || isInitialized) {
                    return false;
                } else {
                    isInitialized = true;
                }
            }
        }
        if (!isInitialized) {
            return false;
        }
        final PsiElement[] children = aClass.getChildren();
        final ClassVisitor visitor = new ClassVisitor(field);
        for (PsiElement child : children) {
            child.accept(visitor);
            if (visitor.isVariableAssignedInClass()) {
                return false;
            }
        }
        PsiClass containingClass = aClass.getContainingClass();
        final AssignmentVisitor assignmentVisitor =
                new AssignmentVisitor(field, aClass);
        while (containingClass != null) {
            containingClass.accept(assignmentVisitor);
            if (assignmentVisitor.isVariableAssigned()) {
                return false;
            }
            containingClass = containingClass.getContainingClass();
        }
        return true;
    }

    private static boolean staticFieldMayBeFinal(PsiField field) {
        final PsiExpression initializer = field.getInitializer();
        final PsiClass aClass = field.getContainingClass();
        if (aClass == null) {
            return false;
        }
        final PsiClassInitializer[] classInitializers =
                aClass.getInitializers();
        boolean assignedInInitializer = initializer != null;
        for (PsiClassInitializer classInitializer : classInitializers) {
            if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
                if (VariableAccessUtils.variableIsAssigned(field,
                        classInitializer, false)) {
                    if (assignedInInitializer) {
                        return false;
                    } else if (variableIsAssignedOnceAndOnlyOnce(field,
                            classInitializer)) {
                        assignedInInitializer = true;
                    }
                }
            } else if (VariableAccessUtils.variableIsAssigned(field,
                    classInitializer,  false)) {
                return false;
            }
        }
        final PsiField[] fields = aClass.getFields();
        for (PsiField aField : fields) {
            if (aField.equals(field)) {
                continue;
            }
            final PsiExpression expression = aField.getInitializer();
            if (expression == null) {
                continue;
            }
            if (VariableAccessUtils.variableIsAssigned(field, expression)) {
                if (!aField.hasModifierProperty(PsiModifier.STATIC)) {
                    return false;
                } else if (assignedInInitializer) {
                    return false;
                } else {
                    assignedInInitializer = true;
                }
            }
        }
        if (!assignedInInitializer) {
            return false;
        }
        final PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
            if (VariableAccessUtils.variableIsAssigned(field, method,
                    false)) {
                return false;
            }
        }
        final PsiElement[] children = aClass.getChildren();
        final ClassVisitor visitor = new ClassVisitor(field);
        for (PsiElement child : children) {
            child.accept(visitor);
            if (visitor.isVariableAssignedInClass()) {
                return false;
            }
        }
        PsiClass containingClass = aClass.getContainingClass();
        final AssignmentVisitor assignmentVisitor =
                new AssignmentVisitor(field, aClass);
        while (containingClass != null) {
            containingClass.accept(assignmentVisitor);
            if (assignmentVisitor.isVariableAssigned()) {
                return false;
            }
            containingClass = containingClass.getContainingClass();
        }
        return true;
    }

    private static boolean variableIsAssignedOnceAndOnlyOnce(
            @NotNull PsiVariable variable, @Nullable PsiElement contenxt) {
        if (contenxt == null) {
            return false;
        }
        final AssignmentCountVisitor visitor =
                new AssignmentCountVisitor(variable);
        contenxt.accept(visitor);
        final int count = visitor.getAssignmentCount();
        return count == 1;
    }

    private static class ClassVisitor extends JavaRecursiveElementVisitor {

        private final PsiVariable variable;
        private boolean variableAssignedInClass = false;

        ClassVisitor(PsiVariable variable) {
            this.variable = variable;
        }

        @Override
        public void visitClass(PsiClass aClass) {
            if (variableAssignedInClass) {
                return;
            }
            if (VariableAccessUtils.variableIsAssigned(variable, aClass)) {
                variableAssignedInClass = true;
            }
        }

        @Override
        public void visitElement(PsiElement element) {
            if (variableAssignedInClass) {
                return;
            }
            super.visitElement(element);
        }

        public boolean isVariableAssignedInClass() {
            return variableAssignedInClass;
        }
    }

    private static class AssignmentVisitor
            extends JavaRecursiveElementVisitor {

        private final PsiVariable variable;
        private final PsiClass excludedClass;
        private boolean variableAssigned = false;

        AssignmentVisitor(PsiVariable variable, PsiClass excludedClass) {
            this.variable = variable;
            this.excludedClass = excludedClass;
        }

        @Override
        public void visitClass(PsiClass aClass) {
            if (variableAssigned) {
                return;
            }
            if (aClass.equals(excludedClass)) {
                return;
            }
            super.visitClass(aClass);
        }

        @Override
        public void visitMethod(PsiMethod method) {
            if (variableAssigned) {
                return;
            }
            super.visitMethod(method);
            if (VariableAccessUtils.variableIsAssigned(variable, method)) {
                variableAssigned = true;
            }
        }

        @Override
        public void visitClassInitializer(PsiClassInitializer initializer) {
            if (variableAssigned) {
                return;
            }
            super.visitClassInitializer(initializer);
            if (VariableAccessUtils.variableIsAssigned(variable,
                    initializer)) {
                variableAssigned = true;
            }
        }

        public boolean isVariableAssigned() {
            return variableAssigned;
        }
    }

    private static class AssignmentCountVisitor
            extends JavaRecursiveElementVisitor {

        private final PsiVariable variable;
        private int assignmentCount = 0;

        public AssignmentCountVisitor(PsiVariable variable) {
            this.variable = variable;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (assignmentCount > 1) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitAssignmentExpression(
                PsiAssignmentExpression assignment) {
            final PsiExpression arg = assignment.getLExpression();
            if(VariableAccessUtils.mayEvaluateToVariable(arg, variable)){
                assignmentCount++;
            }
            super.visitAssignmentExpression(assignment);
        }

        @Override
        public void visitPrefixExpression(PsiPrefixExpression expression) {
            final PsiJavaToken operationSign = expression.getOperationSign();
            final IElementType tokenType = operationSign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(VariableAccessUtils.mayEvaluateToVariable(operand, variable)){
                assignmentCount++;
            }
            super.visitPrefixExpression(expression);
        }

        @Override
        public void visitPostfixExpression(PsiPostfixExpression expression) {
            final PsiJavaToken operationSign = expression.getOperationSign();
            final IElementType tokenType = operationSign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(VariableAccessUtils.mayEvaluateToVariable(operand, variable)){
                assignmentCount++;
            }
            super.visitPostfixExpression(expression);
        }

        @Override
        public void visitForeachStatement(PsiForeachStatement statement) {
            final PsiStatement body = statement.getBody();
            if (VariableAccessUtils.variableIsAssigned(variable, body)) {
                assignmentCount += 2;
            }
        }

        @Override
        public void visitForStatement(PsiForStatement statement) {
            final PsiStatement initialization = statement.getInitialization();
            if (initialization != null) {
                initialization.accept(this);
            }
            final PsiExpression condition = statement.getCondition();
            if (VariableAccessUtils.variableIsAssigned(variable, condition)) {
                assignmentCount += 2;
            }
            final PsiStatement update = statement.getUpdate();
            if (VariableAccessUtils.variableIsAssigned(variable, update)) {
                assignmentCount += 2;
            }
            final PsiStatement body = statement.getBody();
            if (VariableAccessUtils.variableIsAssigned(variable, body)) {
                assignmentCount += 2;
            }
        }

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            if (VariableAccessUtils.variableIsAssigned(variable, statement)) {
                assignmentCount += 2;
            }
        }

        @Override
        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            if (VariableAccessUtils.variableIsAssigned(variable, statement)) {
                assignmentCount += 2;
            }
        }

        @Override
        public void visitTryStatement(PsiTryStatement statement) {
            final int tmp = assignmentCount;
            final PsiCodeBlock block = statement.getTryBlock();
            if (block != null) {
                block.accept(this);
            }
            if (assignmentCount < 2 && assignmentCount > tmp) {
                final PsiCodeBlock[] blocks = statement.getCatchBlocks();
                for (PsiCodeBlock catchBlock : blocks) {
                    if (!ExceptionUtils.blockThrowsException(catchBlock)) {
                        assignmentCount += 2;
                    }
                }
            }
            final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
            if (finallyBlock != null) {
                finallyBlock.accept(this);
            }
        }

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            final PsiExpression condition = statement.getCondition();
            if (condition != null) {
                condition.accept(this);
            }
            final Object constant =
                    ExpressionUtils.computeConstantExpression(condition);
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiStatement elseBranch = statement.getElseBranch();
            if (constant == Boolean.TRUE) {
                if (thenBranch != null) {
                    final AssignmentCountVisitor visitor =
                            new AssignmentCountVisitor(variable);
                    thenBranch.accept(visitor);
                    assignmentCount += visitor.getAssignmentCount();
                }
            } else if (constant == Boolean.FALSE) {
                if (elseBranch != null) {
                    final AssignmentCountVisitor visitor =
                            new AssignmentCountVisitor(variable);
                    elseBranch.accept(visitor);
                    assignmentCount += visitor.getAssignmentCount();
                }
            } else {
                final int thenAssignmentCount;
                if (thenBranch != null) {
                    final AssignmentCountVisitor visitor =
                            new AssignmentCountVisitor(variable);
                    thenBranch.accept(visitor);
                    thenAssignmentCount = visitor.getAssignmentCount();
                } else {
                    thenAssignmentCount = 0;
                }
                final int elseAssignmentCount;
                if (elseBranch != null) {
                    final AssignmentCountVisitor visitor =
                            new AssignmentCountVisitor(variable);
                    elseBranch.accept(visitor);
                    elseAssignmentCount = visitor.getAssignmentCount();
                } else {
                    elseAssignmentCount = 0;
                }
                if (thenAssignmentCount != elseAssignmentCount ||
                        thenAssignmentCount > 1) {
                    assignmentCount += 2;
                } else {
                    assignmentCount += thenAssignmentCount;
                }
            }
        }

        /**
         * @return does not return numbers greater than 3
         */
        public int getAssignmentCount() {
            return assignmentCount;
        }
    }
}
