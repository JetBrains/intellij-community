package com.siyeh.ipp.braces;

import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class RemoveBracesIntention extends MutablyNamedIntention
{
	@NotNull
	protected PsiElementPredicate getElementPredicate()
	{
		return new RemoveBracesPredicate();
	}

	public String getFamilyName()
	{
		return "Remove Braces";
	}

	protected String getTextForElement(PsiElement element)
	{
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
		return "Remove braces from '" + keyword + "' statement";
	}

	protected void processIntention(@NotNull PsiElement element)
		throws IncorrectOperationException
	{
		final PsiBlockStatement blockStatement = (PsiBlockStatement)element;
		final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
		final PsiStatement[] statements = codeBlock.getStatements();
		final PsiStatement statement = statements[0];

		// handle comments
		final PsiElement parent = blockStatement.getParent();
		assert parent != null;
		final PsiElement grandParent = parent.getParent();
		assert grandParent != null;
		PsiElement sibling = codeBlock.getFirstChild();
		assert sibling != null;
		sibling = sibling.getNextSibling();
		while (sibling != null && !sibling.equals(statement))
		{
			if (sibling instanceof PsiComment)
			{
				grandParent.addBefore(sibling, parent);
			}
			sibling = sibling.getNextSibling();
		}
		final PsiElement lastChild = blockStatement.getLastChild();
		if (lastChild instanceof PsiComment)
		{
			final PsiElement nextSibling = parent.getNextSibling();
			grandParent.addAfter(lastChild, nextSibling);
		}

		final String text = statement.getText();
		replaceStatement(text, blockStatement);
	}
}