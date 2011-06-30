/*
 * Copyright 2009-2011 Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

// todo handle variable initialization in loops
public class FinalUtils {

    private FinalUtils() {}

    public static boolean canBeFinal(PsiField field) {
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        final boolean fieldIsStatic =
                field.hasModifierProperty(PsiModifier.STATIC);
        final PsiField[] fields = containingClass.getFields();
        final DefiniteAssignmentVisitor visitor =
                new DefiniteAssignmentVisitor(field);
        if (field.hasInitializer()) {
            visitor.setDefiniteAssignment(true, false);
        }
        for (PsiField aField : fields) {
            final PsiExpression initializer = aField.getInitializer();
            if (initializer != null) {
                initializer.accept(visitor);
                if (!visitor.isFinalCandidate()) {
                    return false;
                }
            }
        }
        final PsiClassInitializer[] initializers =
                containingClass.getInitializers();
        for (PsiClassInitializer initializer : initializers) {
            initializer.accept(visitor);
            if (!visitor.isFinalCandidate()) {
                return false;
            }
        }
        if (!fieldIsStatic) {
            final boolean da = visitor.isDefinitelyAssigned();
            final boolean du = visitor.isDefinitelyUnassigned();
            final PsiMethod[] constructors = containingClass.getConstructors();
            for (PsiMethod constructor : constructors) {
                final PsiCodeBlock body = constructor.getBody();
                if (body != null) {
                    visitor.setDefiniteAssignment(da, du);
                    body.accept(visitor);
                }
                if (!visitor.isDefinitelyAssigned()) {
                    return false;
                }
                if (!visitor.isFinalCandidate()) {
                    return false;
                }
            }
        }
        if (!visitor.isDefinitelyAssigned()) {
            return false;
        }
        if (!visitor.isFinalCandidate()) {
            return false;
        }
        checkMembers(fieldIsStatic, containingClass, null, visitor);
        if (!visitor.isFinalCandidate()) {
            return false;
        }
        PsiClass aClass = containingClass.getContainingClass();
        while (aClass != null) {
            visitor.setSkipClass(containingClass);
            aClass.accept(visitor);
            if (!visitor.isFinalCandidate()) {
                return false;
            }
            containingClass = aClass;
            aClass = containingClass.getContainingClass();
        }
        return true;
    }

    private static void checkMembers(boolean checkConstructors,
                                     PsiClass containingClass,
                                     @Nullable PsiClass skipClass,
                                     DefiniteAssignmentVisitor visitor) {
        final PsiMethod[] methods = containingClass.getMethods();
        for (PsiMethod method : methods) {
            if (!checkConstructors && method.isConstructor()) {
                continue;
            }
            method.accept(visitor);
            if (!visitor.isFinalCandidate()) {
                return;
            }
        }
        final PsiClass[] innerClasses = containingClass.getInnerClasses();
        for (PsiClass innerClass : innerClasses) {
            if (innerClass == skipClass) {
                continue;
            }
            innerClass.accept(visitor);
            if (!visitor.isFinalCandidate()) {
                return;
            }
        }
    }

    private static boolean isReadAccess(PsiReferenceExpression expression) {
        final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression,
                PsiParenthesizedExpression.class);
        if (!(parent instanceof PsiAssignmentExpression)) {
            return true;
        }
        final PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression) parent;
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!PsiTreeUtil.isAncestor(lhs, expression, false)) {
            return true;
        }
        final IElementType tokenType =
                assignmentExpression.getOperationTokenType();
        return tokenType != JavaTokenType.EQ;
    }

    private static boolean isWriteAccess(PsiReferenceExpression expression) {
        final PsiElement parent =
                PsiTreeUtil.skipParentsOfType(expression,
                        PsiParenthesizedExpression.class);
        if (parent instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) parent;
            final PsiExpression lhs = assignmentExpression.getLExpression();
            return PsiTreeUtil.isAncestor(lhs, expression, false);
        } else if (parent instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression) parent;
            final IElementType tokenType =
                    prefixExpression.getOperationTokenType();
            return tokenType == JavaTokenType.PLUSPLUS ||
                    tokenType == JavaTokenType.MINUSMINUS;
        } else if (parent instanceof PsiPostfixExpression) {
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression) parent;
            final IElementType tokenType =
                    postfixExpression.getOperationTokenType();
            return tokenType == JavaTokenType.PLUSPLUS ||
                    tokenType == JavaTokenType.MINUSMINUS;
        }
        return false;
    }

    private static class DefiniteAssignmentVisitor
            extends JavaRecursiveElementVisitor {

        private static final byte NOT_CONSTANT = 0;
        private static final byte CONSTANT_TRUE = 1;
        private static final byte CONSTANT_FALSE = 2;

        private final PsiField field;
        private final Set<PsiStatement> exits = new HashSet();

        private byte constant = NOT_CONSTANT;
        private boolean definitelyAssigned = false;
        private boolean definitelyUnassigned = true;
        private boolean finalCandidate = true;
        private PsiClass skipClass = null;

        private DefiniteAssignmentVisitor(PsiField field) {
            this.field = field;
        }

        public boolean isDefinitelyAssigned() {
            return definitelyAssigned;
        }

        public boolean isDefinitelyUnassigned() {
            return definitelyUnassigned;
        }

        public void setSkipClass(PsiClass skipClass) {
            this.skipClass = skipClass;
        }

        public void setDefiniteAssignment(boolean da, boolean du) {
            definitelyAssigned = da;
            definitelyUnassigned = du;
        }

        public boolean isFinalCandidate() {
            return finalCandidate;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (!isFinalCandidate()) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitMethod(PsiMethod method) {
            definitelyAssigned = true;
            definitelyUnassigned = false;
            super.visitMethod(method);
        }

        @Override
        public void visitClass(PsiClass aClass) {
            if (aClass == skipClass) {
                return;
            }
            final boolean da = definitelyAssigned;
            final boolean du = definitelyUnassigned;
            definitelyAssigned = true;
            definitelyUnassigned = false;
            super.visitClass(aClass);
            definitelyAssigned = da;
            definitelyUnassigned = du;
        }

        @Override
        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            if (PsiType.BOOLEAN.equals(expression.getType())) {
                final Object constant =
                        ExpressionUtils.computeConstantExpression(expression);
                if (Boolean.TRUE == constant) {
                    this.constant = CONSTANT_TRUE;
                } else if (Boolean.FALSE == constant) {
                    this.constant = CONSTANT_FALSE;
                }
            }
            final PsiExpression qualifierExpression =
                    expression.getQualifierExpression();
            if (qualifierExpression != null &&
                    !(qualifierExpression instanceof PsiThisExpression)) {
                final PsiElement target = expression.resolve();
                if (!field.equals(target)) {
                    return;
                }
                if (isWriteAccess(expression)) {
                    finalCandidate = false;
                }
                return;
            }
            if (isPrePostFixExpression(expression)) {
                final PsiElement target = expression.resolve();
                if (!field.equals(target)) {
                    return;
                }
                if (!definitelyAssigned || !definitelyUnassigned) {
                    finalCandidate = false;
                } else {
                    definitelyUnassigned = false;
                }
            } else if (isReadAccess(expression)) {
                final PsiElement target = expression.resolve();
                if (!field.equals(target)) {
                    return;
                }
                if (!definitelyAssigned) {
                    finalCandidate = false;
                }
            }
            super.visitReferenceExpression(expression);
        }

        @Override
        public void visitAssignmentExpression(
                PsiAssignmentExpression expression) {
            if (!finalCandidate) {
                return;
            }
            final PsiExpression rhs = expression.getRExpression();
            if (rhs != null) {
                rhs.accept(this);
            }
            final PsiExpression lhs = ParenthesesUtils.stripParentheses(
                    expression.getLExpression());
            if (!(lhs instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) lhs;
            final PsiExpression qualifierExpression =
                    referenceExpression.getQualifierExpression();
            if (qualifierExpression != null &&
                    !(qualifierExpression instanceof PsiThisExpression)) {
                visitReferenceExpression(referenceExpression);
                return;
            }
            final PsiElement target = referenceExpression.resolve();
            if (!field.equals(target)) {
                return;
            }
            final IElementType tokenType = expression.getOperationTokenType();
            if (!JavaTokenType.EQ.equals(tokenType)) {
                finalCandidate = false;
            }
            if (definitelyUnassigned) {
                definitelyAssigned = true;
                definitelyUnassigned = false;
            } else {
                finalCandidate = false;
            }
        }

        @Override
        public void visitAssertStatement(PsiAssertStatement statement) {
            final PsiExpression condition = statement.getAssertCondition();
            final boolean da = definitelyAssigned;
            final boolean du = definitelyUnassigned;
            if (condition != null) {
                condition.accept(this);
            }
            final PsiExpression description = statement.getAssertDescription();
            if (description != null) {
                description.accept(this);
            }
            definitelyAssigned &= da;
            definitelyUnassigned &= du;
        }

        @Override
        public void visitLiteralExpression(PsiLiteralExpression expression) {
            final Object value = expression.getValue();
            if (value instanceof Boolean) {
                final Boolean aBoolean = (Boolean) value;
                if (Boolean.TRUE == aBoolean) {
                    constant = CONSTANT_TRUE;
                } else if (Boolean.FALSE == aBoolean) {
                    constant = CONSTANT_FALSE;
                } else {
                    throw new AssertionError();
                }
            } else {
                constant = NOT_CONSTANT;
            }
        }

        @Override
        public void visitPrefixExpression(PsiPrefixExpression expression) {
            final IElementType tokenType = expression.getOperationTokenType();
            if (JavaTokenType.EXCL != tokenType) {

            }
            final PsiExpression operand = expression.getOperand();
            if (operand != null) {
                operand.accept(this);
            }
            if (constant == CONSTANT_FALSE) {
                constant = CONSTANT_TRUE;
            } else if (constant == CONSTANT_TRUE) {
                constant = CONSTANT_FALSE;
            }
        }

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            final PsiExpression condition = statement.getCondition();
            final PsiStatement body = statement.getBody();
            final boolean da = definitelyAssigned;
            final boolean du = definitelyUnassigned;
            for (int i = 0; i < 2; i++) {
                if (condition != null) {
                    condition.accept(this);
                }
                final byte constant = this.constant;
                if (constant == CONSTANT_FALSE) {
                    satisfyVacuously();
                }
                if (body != null) {
                    body.accept(this);
                }
            }
            if (constant != CONSTANT_TRUE /*|| exits.remove(statement)*/) {
                definitelyAssigned &= da;
                definitelyUnassigned &= du;
            }
        }

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            final PsiExpression condition = statement.getCondition();
            constant = NOT_CONSTANT;
            if (condition != null) {
                condition.accept(this);
            }
            final byte constant = this.constant;
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiStatement elseBranch = statement.getElseBranch();
            if (thenBranch == null) {
                return;
            }
            final boolean da = definitelyAssigned;
            final boolean du = definitelyUnassigned;
            if (constant == CONSTANT_FALSE) {
                satisfyVacuously();
            }
            thenBranch.accept(this);
            if (elseBranch == null) {
                if (constant != CONSTANT_TRUE) {
                    definitelyAssigned &= da;
                    definitelyUnassigned &= du;
                }
                return;
            }
            final boolean thenDa = definitelyAssigned;
            final boolean thenDu = definitelyUnassigned;
            definitelyAssigned = da;
            definitelyUnassigned = du;
            if (constant == CONSTANT_TRUE) {
                satisfyVacuously();
            }
            elseBranch.accept(this);
            definitelyAssigned &= thenDa;
            definitelyUnassigned &= thenDu;
        }

        @Override
        public void visitConditionalExpression(
                PsiConditionalExpression expression) {
            final PsiType type = expression.getType();
            final boolean booleanConditional = PsiType.BOOLEAN.equals(type);
            final PsiExpression condition = expression.getCondition();
            constant = NOT_CONSTANT;
            condition.accept(this);
            final byte constant = this.constant;
            final PsiExpression thenExpression = expression.getThenExpression();
            final PsiExpression elseExpression = expression.getElseExpression();
            final boolean da = definitelyAssigned;
            final boolean du = definitelyUnassigned;
            if (constant == CONSTANT_FALSE) {
                satisfyVacuously();
            }
            if (thenExpression != null) {
                thenExpression.accept(this);
            }
            final boolean thenDa = definitelyAssigned;
            final boolean thenDu = definitelyUnassigned;
            definitelyAssigned = da;
            definitelyUnassigned = du;
            byte constantOut = NOT_CONSTANT;
            if (constant == CONSTANT_TRUE) {
                if (booleanConditional) {
                    constantOut = this.constant;
                }
                satisfyVacuously();
            }
            if (elseExpression != null) {
                elseExpression.accept(this);
            }
            if (constant == CONSTANT_TRUE) {
                definitelyAssigned = thenDa;
                definitelyUnassigned = thenDu;
                this.constant = constantOut;
            } else if (constant != CONSTANT_FALSE) {
                definitelyAssigned &= thenDa;
                definitelyUnassigned &= thenDu;
                this.constant = NOT_CONSTANT;
            }
        }

        @Override
        public void visitBinaryExpression(PsiBinaryExpression expression) {
            final IElementType tokenType = expression.getOperationTokenType();
            if (JavaTokenType.ANDAND.equals(tokenType)) {
                final PsiExpression lhs = expression.getLOperand();
                constant = NOT_CONSTANT;
                lhs.accept(this);
                final byte constant = this.constant;
                final boolean da = definitelyAssigned;
                final boolean du = definitelyUnassigned;
                if (constant == CONSTANT_FALSE) {
                    satisfyVacuously();
                }
                final PsiExpression rhs = expression.getROperand();
                if (rhs != null) {
                    rhs.accept(this);
                }
                if (this.constant == CONSTANT_TRUE) {
                    this.constant = constant;
                } else if (constant == CONSTANT_FALSE) {
                    this.constant = CONSTANT_FALSE;
                    definitelyAssigned = da;
                    definitelyUnassigned = du;
                } else if (constant == NOT_CONSTANT) {
                    this.constant = NOT_CONSTANT;
                }
            } else if (JavaTokenType.OROR.equals(tokenType)) {
                final PsiExpression lhs = expression.getLOperand();
                constant = NOT_CONSTANT;
                lhs.accept(this);
                final int constant = this.constant;
                final boolean da = definitelyAssigned;
                final boolean du = definitelyUnassigned;
                if (constant == CONSTANT_TRUE) {
                    satisfyVacuously();
                }
                final PsiExpression rhs = expression.getROperand();
                if (rhs != null) {
                    rhs.accept(this);
                }
                if (constant == CONSTANT_TRUE) {
                    this.constant = CONSTANT_TRUE;
                } else if (constant == NOT_CONSTANT) {
                    this.constant = NOT_CONSTANT;
                }
                if (constant == CONSTANT_TRUE) {
                    definitelyAssigned = da;
                    definitelyUnassigned = du;
                }
            } else {
                final PsiType type = expression.getType();
                if (PsiType.BOOLEAN.equals(type)) {
                    final Object constant =
                            ExpressionUtils.computeConstantExpression(
                                    expression);
                    if (constant instanceof Boolean) {
                        if (Boolean.TRUE == constant) {
                            this.constant = CONSTANT_TRUE;
                        } else if (Boolean.FALSE == constant) {
                            this.constant = CONSTANT_FALSE;
                        } else {
                            this.constant = NOT_CONSTANT;
                        }
                    } else {
                        this.constant = NOT_CONSTANT;
                    }
                }
                if (constant == NOT_CONSTANT) {
                    super.visitBinaryExpression(expression);
                }
            }
        }

        @Override
        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (qualifierExpression != null) {
                return;
            }
            @NonNls final String referenceName =
                    methodExpression.getReferenceName();
            if ("this".equals(referenceName)) {
                definitelyUnassigned = false;
                definitelyAssigned = true;
            }
        }

        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
            final PsiExpression returnValue = statement.getReturnValue();
            if (returnValue != null) {
                returnValue.accept(this);
            }
            if (!definitelyAssigned || definitelyUnassigned) {
                finalCandidate = false;
            }
            satisfyVacuously();
        }

        @Override
        public void visitThrowStatement(PsiThrowStatement statement) {
            final PsiExpression exception = statement.getException();
            if (exception != null) {
                exception.accept(this);
            }
            satisfyVacuously();
        }

        @Override
        public void visitBreakStatement(PsiBreakStatement statement) {
            final PsiStatement exit = statement.findExitedStatement();
            if (exit != null) {
                exits.add(exit);
            }
            satisfyVacuously();
        }

        @Override
        public void visitContinueStatement(PsiContinueStatement statement) {
            satisfyVacuously();
        }

        private void satisfyVacuously() {
            definitelyAssigned = true;
            definitelyUnassigned = true;
        }

        private static boolean isPrePostFixExpression(
                PsiReferenceExpression expression) {
            final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression,
                    PsiParenthesizedExpression.class);
            if (parent instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression) parent;
                final IElementType tokenType =
                        prefixExpression.getOperationTokenType();
                if (tokenType == JavaTokenType.PLUSPLUS ||
                        tokenType == JavaTokenType.MINUSMINUS) {
                    return true;
                }
            }
            else if (parent instanceof PsiPostfixExpression) {
                final PsiPostfixExpression postfixExpression =
                        (PsiPostfixExpression) parent;
                final IElementType tokenType =
                        postfixExpression.getOperationTokenType();
                if (tokenType == JavaTokenType.PLUSPLUS ||
                        tokenType == JavaTokenType.MINUSMINUS) {
                    return true;
                }
            }
            return false;
        }
    }
}
