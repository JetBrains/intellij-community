/*
 * Copyright 2006 Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceForLoopWithWhileLoopIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new ForLoopPredicate();
    }

    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiForStatement forStatement =
                (PsiForStatement)element.getParent();
        if (forStatement == null) {
            return;
        }
        final PsiStatement initialization = forStatement.getInitialization();
        if (initialization != null) {
            final PsiElement parent = forStatement.getParent();
            parent.addBefore(initialization, forStatement);
        }
	    final PsiManager manager = element.getManager();
	    final PsiElementFactory factory = manager.getElementFactory();
	    final PsiWhileStatement whileStatement =
			    (PsiWhileStatement)factory.createStatementFromText("while(true) {}", element);
	    final PsiExpression forCondition = forStatement.getCondition();
	    final PsiExpression whileCondition = whileStatement.getCondition();
	    final PsiStatement body = forStatement.getBody();
	    whileCondition.replace(forCondition);
	    final PsiElement newBody;
	    if (body instanceof PsiBlockStatement) {
	        final PsiStatement whileBody = whileStatement.getBody();
		    final PsiBlockStatement newWhileBody = (PsiBlockStatement)whileBody.replace(body);
		    newBody = newWhileBody.getCodeBlock();
        } else {
	        final PsiBlockStatement blockStatement =
			        (PsiBlockStatement)factory.createStatementFromText("{}", element);
	        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
		    if (body != null) {
			    codeBlock.addAfter(body, codeBlock.getFirstChild());
		    }
		    newBody = codeBlock;
        }
        final PsiStatement update = forStatement.getUpdate();
        if (update != null) {
	        final PsiStatement updateStatement = factory.createStatementFromText(
			        update.getText() + ';', element);
	        newBody.addBefore(updateStatement, newBody.getLastChild());
        }
	    forStatement.replace(whileStatement);
    }
}
