package com.siyeh.ipp.constant;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class ConstantExpressionIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new ConstantExpressionPredicate();
    }

    public String getText(){
        return "Compute constant value";
    }

    public String getFamilyName(){
        return "Compute Constant Value";
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }

        final PsiExpression expression =
                (PsiExpression) findMatchingElement(file, editor);
        final PsiManager psiManager = expression.getManager();
        final PsiConstantEvaluationHelper helper =
                psiManager.getConstantEvaluationHelper();
        final Object value = helper.computeConstantExpression(expression);
        final String newExpression;
        if(value instanceof String){
            newExpression = '\"' + StringUtil.escapeStringCharacters((String) value) +
                    '\"';
        } else if(value == null){
            newExpression = "null";
        } else{
            newExpression = String.valueOf(value);
        }
        replaceExpression(project, newExpression, expression);
    }
}
