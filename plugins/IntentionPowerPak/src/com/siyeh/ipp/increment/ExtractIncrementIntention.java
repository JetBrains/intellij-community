package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ExtractIncrementIntention
        extends MutablyNamedIntention{
    public String getTextForElement(PsiElement element){
        final PsiJavaToken sign;
        if(element instanceof PsiPostfixExpression){
            sign = ((PsiPostfixExpression) element).getOperationSign();
        } else{
            sign = ((PsiPrefixExpression) element).getOperationSign();
        }
        final String operator = sign.getText();
        return "Extract " + operator;
    }

    public String getFamilyName(){
        return "Extract Increment";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new ExtractIncrementPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiExpression operand;
        if(element instanceof PsiPostfixExpression){
            operand = ((PsiPostfixExpression) element).getOperand();
        } else{
            operand = ((PsiPrefixExpression) element).getOperand();
        }
        final PsiStatement statement = PsiTreeUtil
                .getParentOfType(element, PsiStatement.class);
        assert statement!=null;
        final PsiElement parent = statement.getParent();
        assert parent != null;
        final PsiManager mgr = element.getManager();

        final PsiElementFactory factory = mgr.getElementFactory();
        final String newStatementText = element.getText() + ';';
        final PsiStatement newCall =
                factory.createStatementFromText(newStatementText, null);

        final PsiElement insertedElement;
        if(element instanceof PsiPostfixExpression){
            insertedElement = parent.addAfter(newCall, statement );
        }else{
            insertedElement = parent.addBefore(newCall, statement);
        }

        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
        replaceExpression(operand.getText(), (PsiExpression) element);
    }


}
