package com.siyeh.ipp.base;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.psiutils.*;

public abstract class Intention implements IntentionAction{
    private final PsiElementPredicate predicate;

    protected Intention(){
        super();
        predicate = getElementPredicate();
    }

    protected abstract PsiElementPredicate getElementPredicate();

    protected static void replaceExpression(Project project,
                                            String newExpression,
                                            PsiExpression exp)
            throws IncorrectOperationException{
        final PsiManager mgr = PsiManager.getInstance(project);
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiExpression newCall =
                factory.createExpressionFromText(newExpression, null);
        final PsiElement insertedElement = exp.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpression(Project project,
                                                                 PsiExpression newExpression,
                                                                 PsiExpression exp)
            throws IncorrectOperationException{
        final PsiManager mgr = PsiManager.getInstance(project);
        final PsiElementFactory factory = mgr.getElementFactory();

        PsiExpression expressionToReplace = exp;
        final String expString;
        final String newExpressionText = newExpression.getText();
        if(BoolUtils.isNegated(exp)){
            expressionToReplace = BoolUtils.findNegation(exp);
            expString = newExpressionText;
        } else if(ComparisonUtils.isComparison(newExpression)){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) newExpression;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final String operator = sign.getText();
            final String negatedComparison =
                    ComparisonUtils.getNegatedComparison(operator);
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            expString = lhs.getText() + negatedComparison + rhs.getText();
        } else{
            if(ParenthesesUtils.getPrecendence(newExpression) >
                    ParenthesesUtils.PREFIX_PRECEDENCE){
                expString = "!(" + newExpressionText + ')';
            } else{
                expString = '!' + newExpressionText;
            }
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, null);
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpressionString(Project project,
                                                                       String newExpression,
                                                                       PsiExpression exp)
            throws IncorrectOperationException{
        final PsiManager mgr = PsiManager.getInstance(project);
        final PsiElementFactory factory = mgr.getElementFactory();

        PsiExpression expressionToReplace = exp;
        final String expString;
        if(BoolUtils.isNegated(exp)){
            expressionToReplace = BoolUtils.findNegation(exp);
            expString = newExpression;
        } else{
            expString = "!(" + newExpression + ')';
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, null);
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatement(Project project, String newStatement,
                                           PsiStatement statement)
            throws IncorrectOperationException{
        final PsiManager mgr = PsiManager.getInstance(project);
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiStatement newCall =
                factory.createStatementFromText(newStatement, null);
        final PsiElement insertedElement = statement.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected PsiElement findMatchingElement(PsiFile file, Editor editor){
        final CaretModel caretModel = editor.getCaretModel();
        final int position = caretModel.getOffset();
        PsiElement element = file.findElementAt(position);
        while(element != null){
            if(element.isWritable() && predicate.satisfiedBy(element)){
                return element;
            } else{
                element = element.getParent();
            }
        }
        return null;
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file){
        return findMatchingElement(file, editor) != null;
    }

    public boolean startInWriteAction(){
        return true;
    }
}
