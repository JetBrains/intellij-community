package com.siyeh.ipp.braces;

import com.siyeh.ipp.base.PsiElementPredicate;
import com.intellij.psi.*;

public class RemoveBracesPredicate implements PsiElementPredicate
{
	public boolean satisfiedBy(PsiElement element)
	{
		if (!(element instanceof PsiBlockStatement))
		{
			return false;
		}
		final PsiBlockStatement blockStatement = (PsiBlockStatement)element;
		final PsiElement parent = blockStatement.getParent();
		if(!(parent instanceof PsiIfStatement || parent instanceof PsiWhileStatement ||
			parent instanceof PsiDoWhileStatement || parent instanceof PsiForStatement ||
			parent instanceof PsiForeachStatement))
		{
			return false;
		}
		final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
		final PsiStatement[] statements = codeBlock.getStatements();
		return statements.length == 1;
	}
}