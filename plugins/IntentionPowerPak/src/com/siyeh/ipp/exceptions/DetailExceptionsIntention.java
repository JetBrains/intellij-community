package com.siyeh.ipp.exceptions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.*;

public class DetailExceptionsIntention extends Intention
{

    public String getText()
    {
        return "Detail exceptions";
    }

    public String getFamilyName()
    {
        return "Detail Exceptions";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new DetailExceptionsPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        final PsiJavaToken token = (PsiJavaToken) findMatchingElement(file, editor);
        final PsiTryStatement tryStatement = (PsiTryStatement) token.getParent();

        final String text = tryStatement.getText();
        final int length = text.length();
        final StringBuffer newTryStatement = new StringBuffer(length);
        newTryStatement.append("try");
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        final String tryBlockText = tryBlock.getText();
        newTryStatement.append(tryBlockText);

        final Set exceptionsThrown = new HashSet(10);

        final PsiManager mgr = PsiManager.getInstance(project);
        final PsiElementFactory factory = mgr.getElementFactory();
        ExceptionUtils.calculateExceptionsThrownForCodeBlock(tryBlock, exceptionsThrown, factory);

        final HeirarchicalTypeComparator comparator = new HeirarchicalTypeComparator();
        PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        for (int i = 0; i < catchSections.length; i++) {
            final PsiParameter param = catchSections[i].getParameter();
            final PsiCodeBlock block = catchSections[i].getCatchBlock();
            if (param == null || block == null) continue;

            final PsiType caughtType = param.getType();
            final List exceptionsToExpand = new ArrayList(10);
            for(Iterator iterator = exceptionsThrown.iterator(); iterator.hasNext();)
            {
                final PsiType thrownType = (PsiType) iterator.next();
                if(caughtType.isAssignableFrom(thrownType))
                {
                    exceptionsToExpand.add(thrownType);
                }
            }
            Collections.sort(exceptionsToExpand, comparator);
            for(Iterator iterator = exceptionsToExpand.iterator(); iterator.hasNext();)
            {
                final PsiType thrownType = (PsiType) iterator.next();
                newTryStatement.append("catch(");
                final String exceptionType = thrownType.getPresentableText();
                newTryStatement.append(exceptionType);
                newTryStatement.append(' ');
                final String parameterName = param.getName();
                newTryStatement.append(parameterName);
                newTryStatement.append(')');
                final String blockText = block.getText();
                newTryStatement.append(blockText);
            }
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if(finallyBlock != null)
        {
            newTryStatement.append("finally");
            final String finallyBlockText = finallyBlock.getText();
            newTryStatement.append(finallyBlockText);
        }
        final String newStatement = newTryStatement.toString();
        replaceStatement(project, newStatement, tryStatement);
    }
}
