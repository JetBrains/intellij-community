package com.siyeh.ipp.switchtoif;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import com.siyeh.ipp.psiutils.SideEffectChecker;

import java.util.*;

class CaseUtil{
    private CaseUtil(){
        super();
    }

    private static boolean canBeCaseLabel(PsiExpression expression){
        if(expression == null){
            return false;
        }
        final PsiType type = expression.getType();
        if(type == null){
            return false;
        }
        if(!type.equals(PsiType.INT) &&
                !type.equals(PsiType.CHAR) &&
                !type.equals(PsiType.LONG) &&
                !type.equals(PsiType.SHORT)){
            return false;
        }
        return PsiUtil.isConstantExpression(expression);
    }

    public static boolean containsHiddenBreak(PsiStatement statement){
        return containsHiddenBreak(statement, true);
    }

    private static boolean containsHiddenBreak(PsiStatement statement,
                                               boolean isTopLevel){
        if(statement instanceof PsiBlockStatement){
            final PsiCodeBlock codeBlock =
                    ((PsiBlockStatement) statement).getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            for(int i = 0; i < statements.length; i++){
                final PsiStatement childStatement = statements[i];
                if(containsHiddenBreak(childStatement, false)){
                    return true;
                }
            }
        } else if(statement instanceof PsiIfStatement){
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            return containsHiddenBreak(thenBranch, false) ||
                    containsHiddenBreak(elseBranch, false);
        } else if(statement instanceof PsiBreakStatement){
            if(isTopLevel){
                return false;
            }
            final PsiIdentifier identifier =
                    ((PsiBreakStatement) statement).getLabelIdentifier();
            if(identifier == null){
                return true;
            }
            final String text = identifier.getText();
            return "".equals(text);
        }
        return false;
    }

    public static boolean isUsedByStatementList(PsiLocalVariable var,
                                                List statements){
        for(Iterator iterator = statements.iterator(); iterator.hasNext();){
            final PsiElement statement = (PsiElement) iterator.next();
            if(isUsedByStatement(var, statement)){
                return true;
            }
        }
        return false;
    }

    private static boolean isUsedByStatement(PsiLocalVariable var,
                                             PsiElement statement){
        final LocalVariableUsageVisitor visitor =
        new LocalVariableUsageVisitor(var);
        statement.accept(visitor);
        return visitor.isUsed();
    }

    public static String findUniqueLabel(PsiStatement statement,
                                         String baseName){
        PsiElement ancestor = statement;
        while(ancestor.getParent() != null){
            if(ancestor instanceof PsiMethod
                    || ancestor instanceof PsiClass
                    || ancestor instanceof PsiFile){
                break;
            }
            ancestor = ancestor.getParent();
        }
        if(!checkForLabel(baseName, ancestor)){
            return baseName;
        }
        int val = 1;
        while(true){
            final String name = baseName + val;
            if(!checkForLabel(name, ancestor)){
                return name;
            }
            val++;
        }
    }

    private static boolean checkForLabel(String name, PsiElement ancestor){
        final LabelSearchVisitor visitor = new LabelSearchVisitor(name);
        ancestor.accept(visitor);
        return visitor.isUsed();
    }

    public static PsiExpression getCaseExpression(PsiIfStatement statement){
        final PsiExpression condition = statement.getCondition();
        final List possibleCaseExpressions =
                determinePossibleCaseExpressions(condition);
        if(possibleCaseExpressions == null){
            return null;
        }
        for(Iterator iterator = possibleCaseExpressions.iterator();
            iterator.hasNext();){
            final PsiExpression caseExp = (PsiExpression) iterator.next();
            if(!SideEffectChecker.mayHaveSideEffects(caseExp)){
                PsiIfStatement statementToCheck = statement;
                while(true){
                    final PsiExpression caseCondition =
                            statementToCheck.getCondition();
                    if(canBeMadeIntoCase(caseCondition, caseExp)){
                        final PsiStatement elseBranch =
                                statementToCheck.getElseBranch();
                        if(elseBranch == null ||
                                !(elseBranch instanceof PsiIfStatement)){
                            return caseExp;
                        }
                        statementToCheck = (PsiIfStatement) elseBranch;
                    } else{
                        break;
                    }
                }
            }
        }
        return null;
    }

    private static List determinePossibleCaseExpressions(PsiExpression exp){
        final List out = new ArrayList(10);
        PsiExpression expToCheck = exp;
        while(expToCheck instanceof PsiParenthesizedExpression){
            expToCheck = ((PsiParenthesizedExpression) exp).getExpression();
        }
        if(!(expToCheck instanceof PsiBinaryExpression)){
            return out;
        }
        final PsiBinaryExpression binaryExp = (PsiBinaryExpression) expToCheck;
        final PsiJavaToken sign = binaryExp.getOperationSign();
        final IElementType operation = sign.getTokenType();
        final PsiExpression lhs = binaryExp.getLOperand();

        final PsiExpression rhs = binaryExp.getROperand();
        if(operation.equals(JavaTokenType.OROR)){
            return determinePossibleCaseExpressions(lhs);
        } else if(operation.equals(JavaTokenType.EQEQ)){
            if(canBeCaseLabel(lhs)){
                out.add(rhs);
            }
            if(canBeCaseLabel(rhs)){
                out.add(lhs);
            }
        }
        return out;
    }

    private static boolean canBeMadeIntoCase(PsiExpression exp,
                                             PsiExpression caseExpression){
        PsiExpression expToCheck = exp;
        while(expToCheck instanceof PsiParenthesizedExpression){
            expToCheck = ((PsiParenthesizedExpression) exp).getExpression();
        }
        if(!(expToCheck instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExp = (PsiBinaryExpression) expToCheck;
        final PsiJavaToken sign = binaryExp.getOperationSign();
        final IElementType operation = sign.getTokenType();
        final PsiExpression lOperand = binaryExp.getLOperand();
        final PsiExpression rhs = binaryExp.getROperand();
        if(!(operation != JavaTokenType.OROR)){
            return canBeMadeIntoCase(lOperand, caseExpression) &&
                    canBeMadeIntoCase(rhs, caseExpression);
        } else if(operation.equals(JavaTokenType.EQEQ)){
            if(canBeCaseLabel(lOperand) &&
                    EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                                                                rhs)){
                return true;
            } else if(canBeCaseLabel(rhs) &&
                    EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                                                                lOperand)){
                return true;
            }
            return false;
        } else{
            return false;
        }
    }
}
