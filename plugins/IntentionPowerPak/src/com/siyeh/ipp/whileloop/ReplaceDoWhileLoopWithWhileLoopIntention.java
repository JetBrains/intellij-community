/*
 * Copyright 2006-2007 Bas Leijdekkers
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
package com.siyeh.ipp.whileloop;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceDoWhileLoopWithWhileLoopIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new DoWhileLoopPredicate();
    }

    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiDoWhileStatement doWhileStatement =
                (PsiDoWhileStatement)element.getParent();
        if (doWhileStatement == null) {
            return;
        }
        final PsiStatement body = doWhileStatement.getBody();
        final PsiElement parent = doWhileStatement.getParent();
        final StringBuilder whileStatementText = new StringBuilder("while(");
        final PsiExpression condition = doWhileStatement.getCondition();
        if (condition != null) {
            whileStatementText.append(condition.getText());
        }
        whileStatementText.append(')');
        if (body instanceof PsiBlockStatement) {
            whileStatementText.append('{');
            final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            final PsiElement[] children = codeBlock.getChildren();
            if (children.length > 2) {
                for (int i = 1; i < children.length - 1; i++) {
                    final PsiElement child = children[i];
                    parent.addBefore(child, doWhileStatement);
                    if (child instanceof PsiDeclarationStatement) {
                        final PsiDeclarationStatement declarationStatement =
                                (PsiDeclarationStatement)child;
                        final PsiElement[] declaredElements =
                                declarationStatement.getDeclaredElements();
                        for (PsiElement declaredElement : declaredElements) {
                            if (declaredElement instanceof PsiVariable) {
                                // prevent duplicate variable declarations.
                                final PsiVariable variable =
                                        (PsiVariable)declaredElement;
                                final PsiExpression initializer =
                                        variable.getInitializer();
                                if (initializer != null) {
                                    final String name = variable.getName();
                                    whileStatementText.append(name);
                                    whileStatementText.append(" = ");
                                    whileStatementText.append(
                                            initializer.getText());
                                    whileStatementText.append(';');
                                }
                            }
                        }
                    } else {
                        whileStatementText.append(child.getText());
                    }
                }
            }
            whileStatementText.append('}');
        } else if (body != null) {
            parent.addBefore(body, doWhileStatement);
            whileStatementText.append(body.getText());
        }
        replaceStatement(whileStatementText.toString(), doWhileStatement);
    }
}
