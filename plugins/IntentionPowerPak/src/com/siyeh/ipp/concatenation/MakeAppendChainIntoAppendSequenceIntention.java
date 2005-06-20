package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MakeAppendChainIntoAppendSequenceIntention extends Intention{
    @NotNull
    protected PsiElementPredicate getElementPredicate(){
        return new AppendChainPredicate();
    }

    public String getText(){
        return "Make .append() chain into .append() sequence";
    }

    public String getFamilyName(){
        return "Make Append Chain Into Append Sequence";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{

        final PsiExpression call =
                (PsiExpression) element;
        final List<String> argsList = new ArrayList<String>();
        PsiExpression currentCall = call;
        while(currentCall instanceof PsiMethodCallExpression &&
                AppendUtil.isAppend((PsiMethodCallExpression) currentCall)){
            final PsiExpressionList args =
                    ((PsiCall) currentCall).getArgumentList();
            final String argText = args.getText();
            argsList.add(argText);
            final PsiReferenceExpression methodExpression =
                    ((PsiMethodCallExpression) currentCall).getMethodExpression();
            currentCall = methodExpression.getQualifierExpression();
        }
        final String targetText;
        final PsiManager mgr = element.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        final PsiStatement statement;
        final String firstStatement;
        if(call.getParent() instanceof PsiExpressionStatement){
            targetText = currentCall.getText();
            statement = (PsiStatement) call.getParent();
            firstStatement = null;
        } else if(call.getParent() instanceof PsiAssignmentExpression &&
                call.getParent()
                        .getParent() instanceof PsiExpressionStatement){
            statement = (PsiStatement) call.getParent().getParent();
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression) call.getParent();
            targetText = assignment.getLExpression().getText();
            firstStatement =
                    assignment.getLExpression().getText() +
                            assignment.getOperationSign().getText() +
                            currentCall.getText() +
                            ';';
        } else{
            statement = (PsiStatement) call.getParent().getParent();
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement) statement;
            final PsiVariable variable =
                    (PsiVariable) declaration.getDeclaredElements()[0];
            targetText = variable.getName();
            if(variable.hasModifierProperty(PsiModifier.FINAL)){
                firstStatement = "final " +
                        variable.getType().getPresentableText() +
                        ' ' + variable.getName() + '=' + currentCall.getText() +
                        ';';
            } else{
                firstStatement =
                        variable.getType().getPresentableText() +
                                ' ' + variable.getName() + '=' + currentCall
                                .getText() +
                                ';';
            }
        }

        for(Object aArgsList : argsList){
            final String arg = (String) aArgsList;
            final String append;
            append = targetText + ".append" + arg + ';';
            final PsiStatement newCall =
                    factory.createStatementFromText(append, null);
            final PsiElement insertedElement = statement.getParent()
                    .addAfter(newCall, statement);
            codeStyleManager.reformat(insertedElement);
        }
        if(firstStatement != null){
            final PsiStatement newCall =
                    factory.createStatementFromText(firstStatement, null);
            final PsiElement insertedElement = statement.getParent()
                    .addAfter(newCall, statement);
            codeStyleManager.reformat(insertedElement);
        }
        statement.delete();
    }
}
