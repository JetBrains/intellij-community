package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class DetailExceptionsPredicate implements PsiElementPredicate{
    DetailExceptionsPredicate(){
        super();
    }

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }

        final IElementType tokenType = ((PsiJavaToken) element).getTokenType();
        if(!JavaTokenType.TRY_KEYWORD.equals(tokenType)){
            return false;
        }
        if(!(element.getParent() instanceof PsiTryStatement)){
            return false;
        }

        final PsiTryStatement tryStatement =
                (PsiTryStatement) element.getParent();
        if(ErrorUtil.containsError( tryStatement)){
            return false;
        }
        final Set exceptionsThrown = new HashSet(10);
        final PsiManager mgr = element.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        ExceptionUtils.calculateExceptionsThrownForCodeBlock(tryBlock,
                                                             exceptionsThrown,
                                                             factory);
        final Set exceptionsCaught =
                ExceptionUtils.getExceptionTypesHandled(tryStatement);
        for(Object aExceptionsThrown : exceptionsThrown){
            final PsiType typeThrown = (PsiType) aExceptionsThrown;
            if(!exceptionsCaught.contains(typeThrown)){
                for(Object aExceptionsCaught : exceptionsCaught){
                    final PsiType typeCaught = (PsiType) aExceptionsCaught;
                    if(typeCaught.isAssignableFrom(typeThrown)){
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
