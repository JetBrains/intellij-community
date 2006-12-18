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
import org.jetbrains.annotations.NonNls;

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
        final PsiStatement body = forStatement.getBody();
        @NonNls final StringBuilder whileStatementText =
                new StringBuilder("while(");
        final PsiExpression condition = forStatement.getCondition();
        if (condition != null) {
            whileStatementText.append(condition.getText());
        }
        whileStatementText.append(") {\n");
        if (body instanceof PsiBlockStatement) {
            final PsiBlockStatement blockStatement = (PsiBlockStatement)body;
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            for (PsiStatement statement : statements) {
                whileStatementText.append(statement.getText());
                whileStatementText.append('\n');
            }
        } else if (body != null) {
            whileStatementText.append(body.getText());
        }
        final PsiStatement update = forStatement.getUpdate();
        if (update != null) {
            whileStatementText.append(update.getText());
            whileStatementText.append(";\n");
        }
        whileStatementText.append('}');
        replaceStatement(whileStatementText.toString(), forStatement);
    }
}
