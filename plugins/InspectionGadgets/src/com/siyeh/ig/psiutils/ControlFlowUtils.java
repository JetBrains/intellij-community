package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public class ControlFlowUtils {
    private ControlFlowUtils() {
        super();

    }

    public static boolean statementMayCompleteNormally(PsiStatement statement) {
        if (statement == null) {
            return true;
        }
        if (statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiReturnStatement ||
                statement instanceof PsiThrowStatement) {
            return false;
        } else if (statement instanceof PsiExpressionListStatement ||
                statement instanceof PsiExpressionStatement ||
                statement instanceof PsiEmptyStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiDeclarationStatement) {
            return true;
        } else if (statement instanceof PsiForStatement) {
            return forStatementMayReturnNormally((PsiForStatement) statement);
        } else if (statement instanceof PsiForeachStatement) {
            return foreachStatementMayReturnNormally((PsiForeachStatement) statement);
        } else if (statement instanceof PsiWhileStatement) {
            return whileStatementMayReturnNormally((PsiWhileStatement) statement);
        } else if (statement instanceof PsiDoWhileStatement) {
            return doWhileStatementMayReturnNormally((PsiDoWhileStatement) statement);
        } else if (statement instanceof PsiSynchronizedStatement) {
            final PsiCodeBlock body = ((PsiSynchronizedStatement) statement).getBody();
            return codeBlockMayCompleteNormally(body);
        } else if (statement instanceof PsiBlockStatement) {
            final PsiCodeBlock codeBlock = ((PsiBlockStatement) statement).getCodeBlock();
            return codeBlockMayCompleteNormally(codeBlock);
        } else if (statement instanceof PsiLabeledStatement) {
            return labeledStatementMayCompleteNormally((PsiLabeledStatement) statement);
        } else if (statement instanceof PsiIfStatement) {
            return ifStatementMayReturnNormally((PsiIfStatement) statement);
        } else if (statement instanceof PsiTryStatement) {
            return tryStatementMayReturnNormally((PsiTryStatement) statement);
        } else if (statement instanceof PsiSwitchStatement) {
            return switchStatementMayReturnNormally((PsiSwitchStatement) statement);
        } else   // unknown statement type
        {
            return true;
        }
    }

    private static boolean doWhileStatementMayReturnNormally(PsiDoWhileStatement loopStatement) {
        final PsiExpression test = loopStatement.getCondition();
        final PsiStatement body = loopStatement.getBody();
        return statementMayCompleteNormally(body) && !BoolUtils.isTrue(test)
                || statementIsBreakTarget(loopStatement);
    }

    private static boolean whileStatementMayReturnNormally(PsiWhileStatement loopStatement) {
        final PsiExpression test = loopStatement.getCondition();
        return !BoolUtils.isTrue(test)
                || statementIsBreakTarget(loopStatement);
    }

    private static boolean forStatementMayReturnNormally(PsiForStatement loopStatement) {
        final PsiExpression test = loopStatement.getCondition();
        if (statementIsBreakTarget(loopStatement)) {
            return true;
        }
        if (test == null) {
            return false;
        }
        if (BoolUtils.isTrue(test)) {
            return false;
        }
        return true;
    }

    private static boolean foreachStatementMayReturnNormally(PsiForeachStatement loopStatement) {
        return true;
    }

    private static boolean switchStatementMayReturnNormally(PsiSwitchStatement switchStatement) {
        if (statementIsBreakTarget(switchStatement)) {
            return true;
        }
        final PsiCodeBlock body = switchStatement.getBody();
        if (body == null) {
            return true;
        }
        final PsiStatement[] statements = body.getStatements();
        if (statements == null) {
            return true;
        }
        if (statements.length == 0) {
            return true;
        }

        return statementMayCompleteNormally(statements[statements.length - 1]);
    }

    private static boolean tryStatementMayReturnNormally(PsiTryStatement tryStatement) {
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
            if (!codeBlockMayCompleteNormally(finallyBlock)) {
                return false;
            }
        }
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (codeBlockMayCompleteNormally(tryBlock)) {
            return true;
        }
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for (int i = 0; i < catchBlocks.length; i++) {
            final PsiCodeBlock catchBlock = catchBlocks[i];
            if (codeBlockMayCompleteNormally(catchBlock)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ifStatementMayReturnNormally(PsiIfStatement ifStatement) {
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if (statementMayCompleteNormally(thenBranch)) {
            return true;
        }
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch == null ||
                statementMayCompleteNormally(elseBranch)) {
            return true;
        }
        return false;
    }

    private static boolean labeledStatementMayCompleteNormally(PsiLabeledStatement labeledStatement) {
        final PsiStatement statement = labeledStatement.getStatement();
        return statementMayCompleteNormally(statement) || statementIsBreakTarget(statement);
    }

    private static boolean codeBlockMayCompleteNormally(PsiCodeBlock block) {
        if (block == null) {
            return true;
        }
        final PsiStatement[] statements = block.getStatements();
        for (int i = 0; i < statements.length; i++) {
            final PsiStatement statement = statements[i];
            if (!statementMayCompleteNormally(statement)) {
                return false;
            }
        }
        return true;
    }

    private static boolean statementIsBreakTarget(PsiStatement statement) {
        final BreakFinder breakFinder = new BreakFinder(statement);
        statement.accept(breakFinder);
        return breakFinder.breakFound();
    }

    public static boolean statementContainsReturn(PsiStatement statement) {
        final ReturnFinder returnFinder = new ReturnFinder();
        statement.accept(returnFinder);
        return returnFinder.returnFound();
    }

    public static boolean statementIsContinueTarget(PsiStatement statement) {
        final ContinueFinder continueFinder = new ContinueFinder(statement);
        statement.accept(continueFinder);
        return continueFinder.continueFound();
    }

    public static boolean isInLoop(PsiElement element) {
        return isInForStatementBody(element) ||
                isInForeachStatementBody(element) ||
                isInWhileStatementBody(element) ||
                isInDoWhileStatementBody(element);
    }

    public static boolean isInFinallyBlock(PsiElement element) {
        PsiElement currentElement = element;
        while (true) {
            final PsiTryStatement tryStatement =
                    (PsiTryStatement) PsiTreeUtil.getParentOfType(currentElement, PsiTryStatement.class);
            if (tryStatement == null) {
                return false;
            }
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock != null) {
                if (PsiTreeUtil.isAncestor(finallyBlock, currentElement, true)) {
                    return true;
                }
            }
            currentElement = tryStatement;
        }
    }

    public static boolean isInCatchBlock(PsiElement element) {
        PsiElement currentElement = element;
        while (true) {
            final PsiTryStatement tryStatement =
                    (PsiTryStatement) PsiTreeUtil.getParentOfType(currentElement, PsiTryStatement.class);
            if (tryStatement == null) {
                return false;
            }
            final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
            for (int i = 0; i < catchBlocks.length; i++) {
                final PsiCodeBlock catchBlock = catchBlocks[i];
                if (PsiTreeUtil.isAncestor(catchBlock, currentElement, true)) {
                    return true;
                }
            }
            currentElement = tryStatement;
        }
    }

    private static boolean isInWhileStatementBody(PsiElement element) {
        final PsiWhileStatement whileStatement =
                (PsiWhileStatement) PsiTreeUtil.getParentOfType(element, PsiWhileStatement.class);
        if (whileStatement == null) {
            return false;
        }
        final PsiStatement body = whileStatement.getBody();
        return PsiTreeUtil.isAncestor(body, element, true);
    }

    private static boolean isInDoWhileStatementBody(PsiElement element) {
        final PsiDoWhileStatement doWhileStatement =
                (PsiDoWhileStatement) PsiTreeUtil.getParentOfType(element, PsiDoWhileStatement.class);
        if (doWhileStatement == null) {
            return false;
        }
        final PsiStatement body = doWhileStatement.getBody();
        return PsiTreeUtil.isAncestor(body, element, true);
    }

    private static boolean isInForStatementBody(PsiElement element) {
        final PsiForStatement forStatement =
                (PsiForStatement) PsiTreeUtil.getParentOfType(element, PsiForStatement.class);
        if (forStatement == null) {
            return false;
        }
        final PsiStatement body = forStatement.getBody();
        return PsiTreeUtil.isAncestor(body, element, true);
    }

    private static boolean isInForeachStatementBody(PsiElement element) {
        final PsiForeachStatement foreachStatement =
                (PsiForeachStatement) PsiTreeUtil.getParentOfType(element, PsiForeachStatement.class);
        if (foreachStatement == null) {
            return false;
        }
        final PsiStatement body = foreachStatement.getBody();
        return PsiTreeUtil.isAncestor(body, element, true);
    }

    public static PsiStatement stripBraces(PsiStatement branch) {
        if (branch instanceof PsiBlockStatement) {
            final PsiBlockStatement block = (PsiBlockStatement) branch;
            final PsiCodeBlock codeBlock = block.getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            if (statements.length == 1) {
                return statements[0];
            } else {
                return block;
            }
        } else {
            return branch;
        }
    }

    public static boolean blockCompletesWithStatement(PsiCodeBlock body,
                                                      PsiStatement statement){
        PsiElement statementToCheck = statement;
        while(true)
        {
            final PsiElement container =
                    getContainingStatementOrBlock(statementToCheck);
            if(container == null)
            {
                return false;
            }
            if(container instanceof PsiCodeBlock)
            {
                if(!statementIsLastInBlock((PsiCodeBlock) container, (PsiStatement)statementToCheck))
                {
                    return false;
                }
                if(container.equals(body))
                {
                    return true;
                }
            }
            if(container instanceof PsiWhileStatement ||
                    container instanceof PsiDoWhileStatement ||
                    container instanceof PsiForeachStatement ||
                    container instanceof PsiForStatement)
            {
                return false;
            }
            statementToCheck = container;
        }
    }

    private static PsiElement getContainingStatementOrBlock(PsiElement statement){
       return PsiTreeUtil.getParentOfType(statement, new Class[]{PsiStatement.class, PsiCodeBlock.class});
    }

    private static boolean statementIsLastInBlock(PsiCodeBlock block,
                                           PsiStatement statement){
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


    private static class ReturnFinder extends PsiRecursiveElementVisitor {
        private boolean m_found = false;

        private ReturnFinder() {
            super();
        }

        public boolean returnFound() {
            return m_found;
        }

        public void visitClass(PsiClass psiClass) {
            // do nothing, to keep drilling into inner classes
        }

        public void visitReferenceExpression(PsiReferenceExpression ref) {
        }

        public void visitReturnStatement(PsiReturnStatement returnStatement) {
            super.visitReturnStatement(returnStatement);
            m_found = true;
        }
    }

    private static class BreakFinder extends PsiRecursiveElementVisitor {
        private boolean m_found = false;
        private final PsiStatement m_target;

        private BreakFinder(PsiStatement target) {
            super();
            m_target = target;
        }

        public boolean breakFound() {
            return m_found;
        }

        public void visitReferenceExpression(PsiReferenceExpression ref) {
        }

        public void visitBreakStatement(PsiBreakStatement breakStatement) {
            super.visitBreakStatement(breakStatement);
            final PsiStatement exitedStatement = breakStatement.findExitedStatement();
            if (m_target.equals(exitedStatement)) {
                m_found = true;
            }
        }
    }

    private static class ContinueFinder extends PsiRecursiveElementVisitor {
        private boolean m_found = false;
        private int m_nestingDepth = 0;
        private String m_label = null;

        private ContinueFinder(PsiStatement target) {
            super();
            if (target.getParent() instanceof PsiLabeledStatement) {
                final PsiLabeledStatement labeledStatement = (PsiLabeledStatement) target.getParent();
                final PsiIdentifier identifier = labeledStatement.getLabelIdentifier();
                m_label = identifier.getText();
            }
        }

        public boolean continueFound() {
            return m_found;
        }

        public void visitReferenceExpression(PsiReferenceExpression ref) {
        }

        public void visitContinueStatement(PsiContinueStatement statement) {
            super.visitContinueStatement(statement);
            final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            if (m_nestingDepth == 1 && labelIdentifier == null) {
                m_found = true;
            } else if (labelMatches(labelIdentifier)) {
                m_found = true;
            }
        }

        private boolean labelMatches(PsiIdentifier labelIdentifier) {
            if (labelIdentifier == null) {
                return false;
            }
            final String labelText = labelIdentifier.getText();
            return labelText.equals(m_label);
        }

        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            m_nestingDepth++;
            super.visitDoWhileStatement(statement);
            m_nestingDepth--;
        }

        public void visitForStatement(PsiForStatement statement) {
            m_nestingDepth++;
            super.visitForStatement(statement);
            m_nestingDepth--;
        }

        public void visitForeachStatement(PsiForeachStatement statement) {
            m_nestingDepth++;
            super.visitForeachStatement(statement);
            m_nestingDepth--;
        }

        public void visitWhileStatement(PsiWhileStatement statement) {
            m_nestingDepth++;
            super.visitWhileStatement(statement);
            m_nestingDepth--;
        }

    }

    public static boolean isInExitStatement(PsiExpression expression) {
        return isInReturnStatementArgument(expression) ||
                isInThrowStatementArgument(expression);
    }

    private static boolean isInReturnStatementArgument(PsiExpression expression) {
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) PsiTreeUtil.getParentOfType(expression, PsiReturnStatement.class);
        return returnStatement != null;
    }

    private static boolean isInThrowStatementArgument(PsiExpression expression) {
        final PsiThrowStatement throwStatement =
                (PsiThrowStatement) PsiTreeUtil.getParentOfType(expression, PsiThrowStatement.class);
        return throwStatement != null;
    }

    public static boolean methodAlwaysThrowsException(PsiMethod method) {
        final PsiCodeBlock body = method.getBody();
        final ReturnFinder returnFinder = new ReturnFinder();
        body.accept(returnFinder);
        if (returnFinder.returnFound()) {
            return false;
        }
        if (codeBlockMayCompleteNormally(body)) {
            return false;
        }
        return true;
    }

}
