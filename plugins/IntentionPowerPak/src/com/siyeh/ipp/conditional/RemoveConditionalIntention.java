package com.siyeh.ipp.conditional;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;

public class RemoveConditionalIntention extends Intention{
    public String getText(){
        return "Simplify ?:";
    }

    public String getFamilyName(){
        return "Remove Pointless Conditional";
    }

    public PsiElementPredicate getElementPredicate(){
        return new RemoveConditionalPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }
        final PsiConditionalExpression exp =
                (PsiConditionalExpression) findMatchingElement(file, editor);
        final PsiExpression condition = exp.getCondition();
        final PsiExpression thenExpression = exp.getThenExpression();
        final String thenExpressionText = thenExpression.getText();
        if("true".equals(thenExpressionText)){
            final String newExpression = condition.getText();
            replaceExpression(project, newExpression, exp);
        } else{
            final String newExpression =
                    BoolUtils.getNegatedExpressionText(condition);
            replaceExpression(project, newExpression, exp);
        }
    }
}
