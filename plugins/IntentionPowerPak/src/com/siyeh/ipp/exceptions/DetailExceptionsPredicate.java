package com.siyeh.ipp.exceptions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.PsiElementPredicate;

import java.util.*;

class DetailExceptionsPredicate implements PsiElementPredicate
{
    private final Project m_project;

    DetailExceptionsPredicate(Project project)
    {
        super();
        m_project = project;
    }

    public boolean satisfiedBy(PsiElement element)
    {
        if(!(element instanceof PsiJavaToken))
        {
            return false;
        }
        final IElementType tokenType = ((PsiJavaToken)element).getTokenType();
        if(tokenType!=JavaTokenType.TRY_KEYWORD)
        {
            return false;
        }
        if(!(element.getParent() instanceof PsiTryStatement))
        {
            return false;
        }
        final PsiTryStatement tryStatement = (PsiTryStatement) element.getParent();
        final Set exceptionsThrown = new HashSet(10);
        final PsiManager mgr = PsiManager.getInstance(m_project);
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        ExceptionUtils.calculateExceptionsThrownForCodeBlock(tryBlock, exceptionsThrown, factory);
        final Set exceptionsCaught = ExceptionUtils.getExceptionTypesHandled(tryStatement);
        for(Iterator iterator = exceptionsThrown.iterator(); iterator.hasNext();)
        {
            final PsiType typeThrown = (PsiType) iterator.next();
            if(!exceptionsCaught.contains(typeThrown))
            {
                for(Iterator caught = exceptionsCaught.iterator(); caught.hasNext();)
                {
                    final PsiType typeCaught = (PsiType) caught.next();
                    if(typeCaught.isAssignableFrom(typeThrown))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
