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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public class ControlFlowUtils{

    private ControlFlowUtils(){
        super();
    }

    public static boolean statementMayCompleteNormally(
            @Nullable PsiStatement statement){
        if(statement == null){
            return true;
        }
        if(statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiReturnStatement ||
                statement instanceof PsiThrowStatement){
            return false;
        } else if(statement instanceof PsiExpressionListStatement ||
                statement instanceof PsiEmptyStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiDeclarationStatement){
            return true;
        } else if(statement instanceof PsiExpressionStatement){
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement)statement;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            if(!(expression instanceof PsiMethodCallExpression)){
                return true;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)expression;
            final PsiMethod method = methodCallExpression.resolveMethod();
            if(method == null){
                return true;
            }
            @NonNls final String methodName = method.getName();
            if(!methodName.equals("exit")) {
                return true;
            }
            final PsiClass aClass = method.getContainingClass();
            final String className = aClass.getQualifiedName();
            return !"java.lang.System".equals(className);
        } else if(statement instanceof PsiForStatement){
            return forStatementMayReturnNormally((PsiForStatement) statement);
        } else if(statement instanceof PsiForeachStatement){
            return foreachStatementMayReturnNormally(
                    (PsiForeachStatement) statement);
        } else if(statement instanceof PsiWhileStatement){
            return whileStatementMayReturnNormally(
                    (PsiWhileStatement) statement);
        } else if(statement instanceof PsiDoWhileStatement){
            return doWhileStatementMayReturnNormally(
                    (PsiDoWhileStatement) statement);
        } else if(statement instanceof PsiSynchronizedStatement){
            final PsiCodeBlock body =
                    ((PsiSynchronizedStatement) statement).getBody();
            return codeBlockMayCompleteNormally(body);
        } else if(statement instanceof PsiBlockStatement){
            final PsiCodeBlock codeBlock =
                    ((PsiBlockStatement) statement).getCodeBlock();
            return codeBlockMayCompleteNormally(codeBlock);
        } else if(statement instanceof PsiLabeledStatement){
            return labeledStatementMayCompleteNormally(
                    (PsiLabeledStatement) statement);
        } else if(statement instanceof PsiIfStatement){
            return ifStatementMayReturnNormally((PsiIfStatement) statement);
        } else if(statement instanceof PsiTryStatement){
            return tryStatementMayReturnNormally((PsiTryStatement) statement);
        } else if(statement instanceof PsiSwitchStatement){
            return switchStatementMayReturnNormally(
                    (PsiSwitchStatement) statement);
        } else{
            // unknown statement type
            return true;
        }
    }

    private static boolean doWhileStatementMayReturnNormally(
            @NotNull PsiDoWhileStatement loopStatement){
        final PsiExpression test = loopStatement.getCondition();
        final PsiStatement body = loopStatement.getBody();
        return statementMayCompleteNormally(body) && !BoolUtils.isTrue(test)
                || statementIsBreakTarget(loopStatement);
    }

    private static boolean whileStatementMayReturnNormally(
            @NotNull PsiWhileStatement loopStatement){
        final PsiExpression test = loopStatement.getCondition();
        return !BoolUtils.isTrue(test)
                || statementIsBreakTarget(loopStatement);
    }

    private static boolean forStatementMayReturnNormally(
            @NotNull PsiForStatement loopStatement){
        final PsiExpression test = loopStatement.getCondition();
        if(statementIsBreakTarget(loopStatement)){
            return true;
        }
        if(test == null){
            return false;
        }
        return !BoolUtils.isTrue(test);
    }

    private static boolean foreachStatementMayReturnNormally(
            @NotNull PsiForeachStatement loopStatement){
        return true;
    }

    private static boolean switchStatementMayReturnNormally(
            @NotNull PsiSwitchStatement switchStatement){
        if(statementIsBreakTarget(switchStatement)){
            return true;
        }
        final PsiCodeBlock body = switchStatement.getBody();
        if(body == null){
            return true;
        }
        final PsiStatement[] statements = body.getStatements();
        if(statements.length == 0){
            return true;
        }
        int numCases = 0;
        boolean hasDefaultCase = false;
        for(PsiStatement statement : statements){
            if(statement instanceof PsiSwitchLabelStatement){
                final PsiSwitchLabelStatement switchLabelStatement =
                        (PsiSwitchLabelStatement)statement;
                if(switchLabelStatement.isDefaultCase()){
                    hasDefaultCase = true;
                }
            }
            if(statement instanceof PsiBreakStatement) {
                final PsiBreakStatement breakStatement =
                        (PsiBreakStatement)statement;
                if(breakStatement.getLabelIdentifier() == null) {
                    return true;
                }
            }
            numCases++;
        }
        final boolean isEnum = isEnumSwitch(switchStatement);
        if(!hasDefaultCase && !isEnum){
            return true;
        }
        if(!hasDefaultCase && isEnum){
            final PsiExpression expression = switchStatement.getExpression();
            if(expression == null){
                return true;
            }
            final PsiClassType type = (PsiClassType) expression.getType();
            if(type == null){
                return true;
            }
            final PsiClass aClass = type.resolve();
            if(aClass == null){
                return true;
            }
            final PsiField[] fields = aClass.getFields();
            int numEnums = 0;
            for(final PsiField field : fields){
                final PsiType fieldType = field.getType();
                if(fieldType.equals(type)){
                    numEnums++;
                }
            }
            if(numEnums != numCases){
                return true;
            }
        }
        return statementMayCompleteNormally(statements[statements.length - 1]);
    }

    private static boolean isEnumSwitch(PsiSwitchStatement statement){
        final PsiExpression expression = statement.getExpression();
        if(expression == null){
            return false;
        }
        final PsiType type = expression.getType();
        if(type == null){
            return false;
        }
        if(!(type instanceof PsiClassType)){
            return false;
        }
        final PsiClass aClass = ((PsiClassType) type).resolve();
        if(aClass == null){
            return false;
        }
        return aClass.isEnum();
    }

    private static boolean tryStatementMayReturnNormally(
            @NotNull PsiTryStatement tryStatement){
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if(finallyBlock != null){
            if(!codeBlockMayCompleteNormally(finallyBlock)){
                return false;
            }
        }
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if(codeBlockMayCompleteNormally(tryBlock)){
            return true;
        }
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for(final PsiCodeBlock catchBlock : catchBlocks){
            if(codeBlockMayCompleteNormally(catchBlock)){
                return true;
            }
        }
        return false;
    }

    private static boolean ifStatementMayReturnNormally(
            @NotNull PsiIfStatement ifStatement){
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if(statementMayCompleteNormally(thenBranch)){
            return true;
        }
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        return elseBranch == null ||
                statementMayCompleteNormally(elseBranch);
    }

    private static boolean labeledStatementMayCompleteNormally(
            @NotNull PsiLabeledStatement labeledStatement){
        final PsiStatement statement = labeledStatement.getStatement();
        if (statement == null) {
            return false;
        }
        return statementMayCompleteNormally(statement) ||
                statementIsBreakTarget(statement);
    }

    public static boolean codeBlockMayCompleteNormally(
            @Nullable PsiCodeBlock block){
        if(block == null){
            return true;
        }
        final PsiStatement[] statements = block.getStatements();
        for(final PsiStatement statement : statements){
            if(!statementMayCompleteNormally(statement)){
                return false;
            }
        }
        return true;
    }

    private static boolean statementIsBreakTarget(
            @NotNull PsiStatement statement){
        final BreakFinder breakFinder = new BreakFinder(statement);
        statement.accept(breakFinder);
        return breakFinder.breakFound();
    }

    public static boolean statementContainsReturn(
            @NotNull PsiStatement statement){
        final ReturnFinder returnFinder = new ReturnFinder();
        statement.accept(returnFinder);
        return returnFinder.returnFound();
    }

    public static boolean statementIsContinueTarget(
            @NotNull PsiStatement statement){
        final ContinueFinder continueFinder = new ContinueFinder(statement);
        statement.accept(continueFinder);
        return continueFinder.continueFound();
    }

    public static boolean statementContainsSystemExit(
            @NotNull PsiStatement statement){
        final SystemExitFinder systemExitFinder = new SystemExitFinder();
        statement.accept(systemExitFinder);
        return systemExitFinder.exitFound();
    }

    public static boolean isInLoop(@NotNull PsiElement element){
        final PsiLoopStatement loopStatement =
                PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class);
        if (loopStatement == null){
            return false;
        }
        final PsiStatement body = loopStatement.getBody();
        if (body == null) {
            return false;
        }
        return PsiTreeUtil.isAncestor(body, element, true);
    }

    public static boolean isInFinallyBlock(@NotNull PsiElement element){
        PsiElement currentElement = element;
        while(true){
            final PsiTryStatement tryStatement =
                    PsiTreeUtil.getParentOfType(currentElement,
                                                PsiTryStatement.class);
            if(tryStatement == null){
                return false;
            }
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if(finallyBlock != null){
                if(PsiTreeUtil.isAncestor(finallyBlock, currentElement, true)){
                    final PsiMethod elementMethod =
                            PsiTreeUtil.getParentOfType(currentElement,
                                                        PsiMethod.class);
                    final PsiMethod finallyMethod =
                            PsiTreeUtil.getParentOfType(finallyBlock,
                                                        PsiMethod.class);
                    return elementMethod != null &&
                            elementMethod.equals(finallyMethod);
                }
            }
            currentElement = tryStatement;
        }
    }

    public static boolean isInCatchBlock(@NotNull PsiElement element){
        PsiElement currentElement = element;
        while(true){
            final PsiTryStatement tryStatement =
                    PsiTreeUtil.getParentOfType(currentElement,
                                                PsiTryStatement.class);
            if(tryStatement == null){
                return false;
            }
            final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
            for(final PsiCodeBlock catchBlock : catchBlocks){
                if(PsiTreeUtil.isAncestor(catchBlock, currentElement, true)){
                    return true;
                }
            }
            currentElement = tryStatement;
        }
    }

    @Nullable
    public static PsiStatement stripBraces(@Nullable PsiStatement branch){
        if(branch instanceof PsiBlockStatement){
            final PsiBlockStatement block = (PsiBlockStatement) branch;
            final PsiCodeBlock codeBlock = block.getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            if(statements.length == 1){
                return statements[0];
            } else{
                return block;
            }
        } else{
            return branch;
        }
    }

    public static boolean statementCompletesWithStatement(
            @NotNull PsiStatement containingStatement,
            @NotNull PsiStatement statement){
        PsiElement statementToCheck = statement;
        while(true){
            if(statementToCheck.equals(containingStatement)){
                return true;
            }
            final PsiElement container =
                    getContainingStatementOrBlock(statementToCheck);
            if(container == null){
                return false;
            }
            if(container instanceof PsiCodeBlock){
                if(!statementIsLastInBlock((PsiCodeBlock) container,
                                           (PsiStatement) statementToCheck)){
                    return false;
                }
            }
            if(container instanceof PsiLoopStatement){
                return false;
            }
            statementToCheck = container;
        }
    }

    public static boolean blockCompletesWithStatement(
            @NotNull PsiCodeBlock body,
            @NotNull PsiStatement statement){
        PsiElement statementToCheck = statement;
        while(true){
            if(statementToCheck == null){
                return false;
            }
            final PsiElement container =
                    getContainingStatementOrBlock(statementToCheck);
            if(container == null){
                return false;
            }
            if(container instanceof PsiLoopStatement){
                return false;
            }
            if(container instanceof PsiCodeBlock){
                if(!statementIsLastInBlock((PsiCodeBlock) container,
                                           (PsiStatement) statementToCheck)){
                    return false;
                }
                if(container.equals(body)){
                    return true;
                }
                statementToCheck =
                        PsiTreeUtil.getParentOfType(container,
                                                    PsiStatement.class);
            } else{
                statementToCheck = container;
            }
        }
    }

    @Nullable
    private static PsiElement getContainingStatementOrBlock(
            @NotNull PsiElement statement){
      return PsiTreeUtil.getParentOfType(
              statement, PsiStatement.class, PsiCodeBlock.class);
    }

    private static boolean statementIsLastInBlock(
            @NotNull PsiCodeBlock block, @NotNull PsiStatement statement) {
        final PsiStatement[] statements = block.getStatements();
        for(int i = statements.length - 1; i >= 0; i--){
            final PsiStatement childStatement = statements[i];
            if(statement.equals(childStatement)){
                return true;
            }
            if(!(statement instanceof PsiEmptyStatement)){
                return false;
            }
        }
        return false;
    }

    private static class SystemExitFinder extends PsiRecursiveElementVisitor{

        private boolean m_found = false;

        public boolean exitFound() {
            return m_found;
        }

        public void visitClass(@NotNull PsiClass aClass) {
            // do nothing to keep from drilling into inner classes
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            if(m_found){
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            @NonNls final String methodName = method.getName();
            if(!methodName.equals("exit")) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            final String className = aClass.getQualifiedName();
            if(!"java.lang.System".equals(className)) {
                return;
            }
            m_found = true;
        }
    }

    private static class ReturnFinder extends PsiRecursiveElementVisitor{

        private boolean m_found = false;

        public boolean returnFound(){
            return m_found;
        }

        public void visitClass(@NotNull PsiClass psiClass){
            // do nothing, to keep drilling into inner classes
        }

        public void visitReturnStatement(
                @NotNull PsiReturnStatement returnStatement){
            if(m_found){
                return;
            }
            super.visitReturnStatement(returnStatement);
            m_found = true;
        }
    }

    private static class BreakFinder extends PsiRecursiveElementVisitor{

        private boolean m_found = false;
        private final PsiStatement m_target;

        private BreakFinder(@NotNull PsiStatement target){
            super();
            m_target = target;
        }

        public boolean breakFound(){
            return m_found;
        }

        public void visitBreakStatement(
                @NotNull PsiBreakStatement breakStatement){
            if(m_found){
                return;
            }
            super.visitBreakStatement(breakStatement);
            final PsiStatement exitedStatement =
                    breakStatement.findExitedStatement();
            if (exitedStatement == null) {
                return;
            }
            if(PsiTreeUtil.isAncestor(exitedStatement, m_target, false)){
                m_found = true;
            }
        }
    }

    private static class ContinueFinder extends PsiRecursiveElementVisitor{

        private boolean m_found = false;
        private int m_nestingDepth = 0;
        private String m_label = null;

        private ContinueFinder(@NotNull PsiStatement target){
            super();
            if(target.getParent() instanceof PsiLabeledStatement){
                final PsiLabeledStatement labeledStatement =
                        (PsiLabeledStatement) target.getParent();
                final PsiIdentifier identifier =
                        labeledStatement.getLabelIdentifier();
                m_label = identifier.getText();
            }
        }

        public boolean continueFound(){
            return m_found;
        }

        public void visitContinueStatement(
                @NotNull PsiContinueStatement statement){
            if(m_found){
                return;
            }
            super.visitContinueStatement(statement);
            final PsiIdentifier labelIdentifier =
                    statement.getLabelIdentifier();
            if(m_nestingDepth == 1 && labelIdentifier == null){
                m_found = true;
            } else if(labelMatches(labelIdentifier)){
                m_found = true;
            }
        }

        private boolean labelMatches(PsiIdentifier labelIdentifier){
            if(labelIdentifier == null){
                return false;
            }
            final String labelText = labelIdentifier.getText();
            return labelText.equals(m_label);
        }

        public void visitDoWhileStatement(
                @NotNull PsiDoWhileStatement statement){
            if(m_found){
                return;
            }
            m_nestingDepth++;
            super.visitDoWhileStatement(statement);
            m_nestingDepth--;
        }

        public void visitForStatement(@NotNull PsiForStatement statement){
            if(m_found){
                return;
            }
            m_nestingDepth++;
            super.visitForStatement(statement);
            m_nestingDepth--;
        }

        public void visitForeachStatement(
                @NotNull PsiForeachStatement statement){
            if(m_found){
                return;
            }
            m_nestingDepth++;
            super.visitForeachStatement(statement);
            m_nestingDepth--;
        }

        public void visitWhileStatement(@NotNull PsiWhileStatement statement){
            if(m_found){
                return;
            }
            m_nestingDepth++;
            super.visitWhileStatement(statement);
            m_nestingDepth--;
        }
    }

    public static boolean isInExitStatement(@NotNull PsiExpression expression){
        return isInReturnStatementArgument(expression) ||
                isInThrowStatementArgument(expression);
    }

    private static boolean isInReturnStatementArgument(
            @NotNull PsiExpression expression){
        final PsiReturnStatement returnStatement =
                PsiTreeUtil
                        .getParentOfType(expression, PsiReturnStatement.class);
        return returnStatement != null;
    }

    private static boolean isInThrowStatementArgument(
            @NotNull PsiExpression expression){
        final PsiThrowStatement throwStatement =
                PsiTreeUtil.getParentOfType(expression,
                                            PsiThrowStatement.class);
        return throwStatement != null;
    }

    public static boolean methodAlwaysThrowsException(
            @NotNull PsiMethod method){
        final PsiCodeBlock body = method.getBody();
	    if (body == null) {
		    return true;
	    }
        final ReturnFinder returnFinder = new ReturnFinder();
        body.accept(returnFinder);
        if(returnFinder.returnFound()){
            return false;
        }
        return !codeBlockMayCompleteNormally(body);
    }
}