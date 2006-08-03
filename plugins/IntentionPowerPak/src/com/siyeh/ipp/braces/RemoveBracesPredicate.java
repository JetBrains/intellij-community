/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.braces;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.jsp.JspFile;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

class RemoveBracesPredicate implements PsiElementPredicate
{
	public boolean satisfiedBy(@NotNull PsiElement element)
	{
        if (!(element instanceof PsiBlockStatement))
		{
			return false;
		}
		final PsiBlockStatement blockStatement = (PsiBlockStatement)element;
		final PsiElement parent = blockStatement.getParent();
		if(!(parent instanceof PsiIfStatement ||
                parent instanceof PsiWhileStatement ||
                parent instanceof PsiDoWhileStatement ||
                parent instanceof PsiForStatement ||
                parent instanceof PsiForeachStatement))
		{
			return false;
		}
		final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
		final PsiStatement[] statements = codeBlock.getStatements();
        if(statements.length != 1 ||
                statements[0] instanceof PsiDeclarationStatement)
        {
            return false;
        }
        final PsiFile file = element.getContainingFile();
        //this intention doesn't work in JSP files, as it can't tell about tags
        // inside the braeces
        return !PsiUtil.isInJspFile(file);
    }
}