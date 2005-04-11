package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ControlFlowUtils;
import com.siyeh.ipp.psiutils.EquivalenceChecker;

import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

class MergeParallelIfsPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;

        final PsiElement parent = token.getParent();
        if(!(parent instanceof PsiIfStatement)){
            return false;
        }
        final PsiIfStatement ifStatement = (PsiIfStatement) parent;


        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiIfStatement)){
            return false;
        }
        final PsiIfStatement nextIfStatement = (PsiIfStatement) nextStatement;
        return ifStatementsCanBeMerged(ifStatement, nextIfStatement);
    }

    public static boolean ifStatementsCanBeMerged( PsiIfStatement statement1,
                                                   PsiIfStatement statement2){
        final PsiStatement thenBranch = statement1.getThenBranch();
        final PsiStatement elseBranch = statement1.getElseBranch();
        if(thenBranch == null){
            return false;
        }
        final PsiExpression firstCondition = statement1.getCondition();
        final PsiExpression secondCondition = statement2.getCondition();
        if(! EquivalenceChecker.expressionsAreEquivalent(firstCondition,
                                                         secondCondition))
        {
            return false;
        }
        final PsiStatement nextThenBranch = statement2.getThenBranch();
        if(!canBeMerged(thenBranch, nextThenBranch)){
            return false;
        }
        final PsiStatement nextElseBranch = statement2.getElseBranch();
        return elseBranch == null || nextElseBranch == null ||
                       canBeMerged(elseBranch, nextElseBranch);
    }

    public static boolean canBeMerged(PsiStatement statement1, PsiStatement statement2){
        if(!ControlFlowUtils.statementMayCompleteNormally(statement1)){
            return false;
        }
        final Set statement1Declarations = calculateTopLevelDeclarations(statement1);
        if(containsConflictingDeclarations(statement1Declarations, statement2))
        {
            return false;
        }
        final Set statement2Declarations = calculateTopLevelDeclarations(statement2);
        return !containsConflictingDeclarations(statement2Declarations, statement1);
    }

    private static boolean containsConflictingDeclarations(Set declarations,
                                                           PsiStatement statement){
        final DeclarationVisitor visitor = new DeclarationVisitor(declarations);
        statement.accept(visitor);
        return visitor.hasConflict();
    }

    private static Set calculateTopLevelDeclarations(PsiStatement statement){
        final Set out = new HashSet();
        if(statement instanceof PsiDeclarationStatement)
        {
            addDeclarations((PsiDeclarationStatement)statement, out);
        }
        else if(statement instanceof PsiBlockStatement)
        {
            final PsiBlockStatement blockStatement = (PsiBlockStatement) statement;
            final PsiCodeBlock block = blockStatement.getCodeBlock();
            final PsiStatement[] statements = block.getStatements();
            for(int i = 0; i < statements.length; i++){
                if( statements[i] instanceof PsiDeclarationStatement)
                {
                    addDeclarations((PsiDeclarationStatement) statements[i], out);
                }
            }
        }
        return out;
    }

    private static void addDeclarations(PsiDeclarationStatement statement,
                                        Set declaredVars){
        final PsiElement[] elements = statement.getDeclaredElements();
        for(int i = 0; i < elements.length; i++){
            final PsiElement element = elements[i];
            if(element instanceof PsiVariable)
            {
                final PsiVariable variable = (PsiVariable) element;
                final String name = variable.getName();
                declaredVars.add(name);
            }
        }
    }

    private static class DeclarationVisitor extends PsiRecursiveElementVisitor{
        private Set declarations;
        private boolean hasConflict = false;

        DeclarationVisitor(Set declarations){
            super();
            this.declarations = new HashSet(declarations);
        }

        public void visitVariable(PsiVariable variable){
            super.visitVariable(variable);
            final String name = variable.getName();
            for(Iterator iterator = declarations.iterator();
                iterator.hasNext();){
                final String testName = (String) iterator.next();
                if(testName.equals(name))
                {
                    hasConflict = true;
                }
            }
        }

        public boolean hasConflict(){
            return hasConflict;
        }
    }
}
