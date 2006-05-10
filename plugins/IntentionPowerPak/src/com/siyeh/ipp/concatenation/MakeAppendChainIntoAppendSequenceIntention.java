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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class MakeAppendChainIntoAppendSequenceIntention extends Intention {

	@NotNull
	protected PsiElementPredicate getElementPredicate() {
		return new AppendChainPredicate();
	}

	public void processIntention(PsiElement element)
			throws IncorrectOperationException {
		final PsiExpression call = (PsiExpression)element;
		final List<String> argumentsList = new ArrayList<String>();
		PsiExpression currentCall = call;
		while (AppendUtil.isAppendCall(currentCall)) {
			final PsiMethodCallExpression methodCallExpression =
					(PsiMethodCallExpression)currentCall;
			final PsiExpressionList arguments = methodCallExpression.getArgumentList();
			final String argumentsText = arguments.getText();
			argumentsList.add(argumentsText);
			final PsiReferenceExpression methodExpression =
					methodCallExpression.getMethodExpression();
			currentCall = methodExpression.getQualifierExpression();
			if (currentCall == null) {
				return;
			}
		}
		final String targetText;
		final PsiStatement appendStatement;
		@NonNls final String firstStatement;
		if (call.getParent() instanceof PsiExpressionStatement) {
			targetText = currentCall.getText();
			appendStatement = (PsiStatement)call.getParent();
			firstStatement = null;
		} else if (call.getParent() instanceof PsiAssignmentExpression &&
		         call.getParent().getParent() instanceof PsiExpressionStatement) {
			appendStatement = (PsiStatement)call.getParent().getParent();
			final PsiAssignmentExpression assignment =
					(PsiAssignmentExpression)call.getParent();
			targetText = assignment.getLExpression().getText();
			firstStatement =
					assignment.getLExpression().getText() +
					assignment.getOperationSign().getText() +
					currentCall.getText() + ';';
		} else {
			appendStatement = (PsiStatement)call.getParent().getParent();
			final PsiDeclarationStatement declaration = (PsiDeclarationStatement)appendStatement;
			final PsiVariable variable =
					(PsiVariable)declaration.getDeclaredElements()[0];
			targetText = variable.getName();
			if (variable.hasModifierProperty(PsiModifier.FINAL)) {
				firstStatement = "final " + variable.getType().getPresentableText() +
				                 ' ' + variable.getName() + '=' + currentCall.getText() + ';';
			} else {
				firstStatement = variable.getType().getPresentableText() +
						' ' + variable.getName() + '=' + currentCall.getText() + ';';
			}
		}
		final StringBuilder builder = new StringBuilder("{");
		if (firstStatement != null) {
			builder.append(firstStatement);
		}
		Collections.reverse(argumentsList);
		for (String argument : argumentsList) {
			builder.append(targetText);
			builder.append(".append");
			builder.append(argument);
			builder.append(';');
		}
		builder.append('}');
		final PsiManager manager = element.getManager();
		final PsiElementFactory factory = manager.getElementFactory();
		final PsiElement parent = appendStatement.getParent();
		final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
		final PsiCodeBlock codeBlock =
				factory.createCodeBlockFromText(builder.toString(), appendStatement);
		if (parent instanceof PsiLoopStatement || parent instanceof PsiIfStatement) {
			final PsiElement insertedStatement = appendStatement.replace(codeBlock);
			codeStyleManager.reformat(insertedStatement);
		} else {
			final PsiStatement[] statements = codeBlock.getStatements();
			for (PsiStatement statement : statements) {
				final PsiElement insertedStatement = parent.addBefore(statement, appendStatement);
				codeStyleManager.reformat(insertedStatement);
			}
			appendStatement.delete();
		}
	}
}
