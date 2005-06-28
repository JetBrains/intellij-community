package com.siyeh.ipp.braces;

import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddBracesIntention extends MutablyNamedIntention
{
	@NotNull
	protected PsiElementPredicate getElementPredicate()
	{
		return new AddBracesPredicate();
	}

	protected String getTextForElement(PsiElement element){
		final PsiElement parent = element.getParent();
		assert parent != null;
		final String keyword;
		if (parent instanceof PsiIfStatement)
		{
			final PsiIfStatement ifStatement = (PsiIfStatement)parent;
			final PsiStatement elseBranch = ifStatement.getElseBranch();
			if (element.equals(elseBranch))
			{
				keyword = "else";
			}
			else
			{
				keyword = "if";
			}
		}
		else
		{
			final PsiElement firstChild = parent.getFirstChild();
			assert firstChild != null;
			keyword = firstChild.getText();
		}
		return "Add Braces to '" + keyword + "' statement";
	}

	public String getFamilyName()
	{
		return "Add Braces";
	}

	protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException
	{
		final PsiStatement statement = (PsiStatement)element;
		final String text = element.getText();
		if (element.getLastChild() instanceof PsiComment)
		{
			replaceStatement('{' + text + "\n}", statement);
		}
		else
		{
			replaceStatement('{' + text + '}', statement);
		}
	}
}