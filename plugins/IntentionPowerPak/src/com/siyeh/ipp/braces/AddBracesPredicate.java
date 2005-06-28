package com.siyeh.ipp.braces;

import com.siyeh.ipp.base.PsiElementPredicate;
import com.intellij.psi.*;

public class AddBracesPredicate implements PsiElementPredicate
{
	public boolean satisfiedBy(PsiElement element)
	{
		if (!(element instanceof PsiStatement))
		{
			return false;
		}
		if (element instanceof PsiBlockStatement)
		{
			return false;
		}
		final PsiElement parent = element.getParent();
		if (parent instanceof PsiIfStatement)
		{
			final PsiIfStatement ifStatement = (PsiIfStatement)parent;
			final PsiStatement elseBranch = ifStatement.getElseBranch();
			return !(element.equals(elseBranch) && element instanceof PsiIfStatement);
		}
		if (parent instanceof PsiWhileStatement)
		{
			return true;
		}
		if (parent instanceof PsiDoWhileStatement)
		{
			return true;
		}
		if (parent instanceof PsiForStatement)
		{
			final PsiForStatement forStatement = (PsiForStatement)parent;
			return element.equals(forStatement);
		}
		return parent instanceof PsiForeachStatement;
	}
}