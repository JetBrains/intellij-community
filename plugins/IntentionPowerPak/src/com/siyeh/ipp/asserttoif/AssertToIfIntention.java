package com.siyeh.ipp.asserttoif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;

public class AssertToIfIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new AssertStatementPredicate();
    }

    public String getText(){
        return "Replace assert with if statement";
    }

    public String getFamilyName(){
        return "Replace Assert With If Statement";
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }

        final PsiAssertStatement assertStatement =
                (PsiAssertStatement) findMatchingElement(file, editor);
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
        replaceStatement(project, newStatement, assertStatement);
    }
}
