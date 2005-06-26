package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DetailExceptionsIntention extends Intention{
    public String getText(){
        return "Detail exceptions";
    }

    public String getFamilyName(){
        return "Detail Exceptions";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new DetailExceptionsPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiJavaToken token = (PsiJavaToken) element;
        final PsiTryStatement tryStatement =
                (PsiTryStatement) token.getParent();

        assert tryStatement != null;
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
        final List<PsiType> exceptionsAlreadyEmitted = new ArrayList<PsiType>(10);
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
                exceptionsToExpand.removeAll(exceptionsAlreadyEmitted);
                Collections.sort(exceptionsToExpand, comparator);
                for(PsiType thrownType : exceptionsToExpand){
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
                    exceptionsAlreadyEmitted.add(thrownType);
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
