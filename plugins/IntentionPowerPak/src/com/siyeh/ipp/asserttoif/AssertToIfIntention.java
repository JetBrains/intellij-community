package com.siyeh.ipp.asserttoif;

import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;

public class AssertToIfIntention extends Intention{
    @NotNull
    protected PsiElementPredicate getElementPredicate(){
        return new AssertStatementPredicate();
    }

    public String getText(){
        return "Replace assert with if statement";
    }

    public String getFamilyName(){
        return "Replace Assert With If Statement";
    }

    public void processIntention(PsiElement element)
       throws IncorrectOperationException{
        final PsiAssertStatement assertStatement = (PsiAssertStatement) element;
        assert assertStatement != null;
        final PsiExpression condition = assertStatement.getAssertCondition();
        final PsiExpression description =
                assertStatement.getAssertDescription();

        final String newStatement;
        final String negatedConditionString =
                BoolUtils.getNegatedExpressionText(condition);
        if(description == null){
            newStatement = "if(" + negatedConditionString +
                    "){ throw new IllegalArgumentException();}";
        } else{
            newStatement = "if(" + negatedConditionString +
                    "){ throw new IllegalArgumentException(" +
                    description.getText() + ");}";
        }
        replaceStatement(newStatement, assertStatement);
    }
}
