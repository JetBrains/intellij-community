package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.*;

public class DetailExceptionsIntention extends Intention{
    public String getText(){
        return "Detail exceptions";
    }

    public String getFamilyName(){
        return "Detail Exceptions";
    }

    public PsiElementPredicate getElementPredicate(){
        return new DetailExceptionsPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiJavaToken token = (PsiJavaToken) element;
        final PsiTryStatement tryStatement =
                (PsiTryStatement) token.getParent();

        final String text = tryStatement.getText();
        final int length = text.length();
        final StringBuffer newTryStatement = new StringBuffer(length);
        newTryStatement.append("try");
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        final String tryBlockText = tryBlock.getText();
        newTryStatement.append(tryBlockText);

        final Set<PsiType> exceptionsThrown = new HashSet<PsiType>(10);

        ExceptionUtils.calculateExceptionsThrownForCodeBlock(tryBlock,
                                                             exceptionsThrown);

        final HeirarchicalTypeComparator comparator =
                new HeirarchicalTypeComparator();
        final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        for(PsiCatchSection catchSection : catchSections){
            final PsiParameter param = catchSection.getParameter();
            final PsiCodeBlock block = catchSection.getCatchBlock();
            if(param != null && block != null){
                final PsiType caughtType = param.getType();
                final List<PsiType> exceptionsToExpand = new ArrayList<PsiType>(10);
                for(Object aExceptionsThrown : exceptionsThrown){
                    final PsiType thrownType = (PsiType) aExceptionsThrown;
                    if(caughtType.isAssignableFrom(thrownType)){
                        exceptionsToExpand.add(thrownType);
                    }
                }
                Collections.sort(exceptionsToExpand, comparator);
                for(Object aExceptionsToExpand : exceptionsToExpand){
                    final PsiType thrownType = (PsiType) aExceptionsToExpand;
                    newTryStatement.append("catch(");
                    final String exceptionType =
                            thrownType.getPresentableText();
                    newTryStatement.append(exceptionType);
                    newTryStatement.append(' ');
                    final String parameterName = param.getName();
                    newTryStatement.append(parameterName);
                    newTryStatement.append(')');
                    final String blockText = block.getText();
                    newTryStatement.append(blockText);
                }
            }
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if(finallyBlock != null){
            newTryStatement.append("finally");
            final String finallyBlockText = finallyBlock.getText();
            newTryStatement.append(finallyBlockText);
        }
        final String newStatement = newTryStatement.toString();
        replaceStatement(newStatement, tryStatement);
    }
}
