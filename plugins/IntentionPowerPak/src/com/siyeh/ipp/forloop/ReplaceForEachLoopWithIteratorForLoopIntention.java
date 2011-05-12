/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceForEachLoopWithIteratorForLoopIntention extends Intention {

    @Override
    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new IterableForEachLoopPredicate();
    }

    @Override
    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiForeachStatement statement =
                (PsiForeachStatement)element.getParent();
        if (statement == null) {
            return;
        }
        final Project project = statement.getProject();
        final JavaCodeStyleManager codeStyleManager =
                JavaCodeStyleManager.getInstance(project);
        final PsiExpression iteratedValue = statement.getIteratedValue();
        if (iteratedValue == null) {
            return;
        }
        @NonNls final StringBuilder newStatement = new StringBuilder();
        final PsiParameter iterationParameter =
                statement.getIterationParameter();
        final PsiType parameterType = iterationParameter.getType();
        final String iterator =
                codeStyleManager.suggestUniqueVariableName("iterator",
                        statement, true);
        final String typeText = parameterType.getCanonicalText();
        newStatement.append("for(java.util.Iterator");
        newStatement.append('<');
        newStatement.append(typeText);
        newStatement.append("> ");
        newStatement.append(iterator);
        newStatement.append(" = ");
        if (iteratedValue instanceof PsiTypeCastExpression) {
            newStatement.append('(');
            newStatement.append(iteratedValue.getText());
            newStatement.append(')');
        } else {
            newStatement.append(iteratedValue.getText());
        }
        newStatement.append(".iterator();");
        newStatement.append(iterator);
        newStatement.append(".hasNext();) {");
        final CodeStyleSettings codeStyleSettings =
                CodeStyleSettingsManager.getSettings(project);
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
            newStatement.append("final ");
        }
        newStatement.append(typeText);
        newStatement.append(' ');
        newStatement.append(iterationParameter.getName());
        newStatement.append(" = ");
        newStatement.append(iterator);
        newStatement.append(".next();");
        final PsiStatement body = statement.getBody();
        if (body == null) {
            return;
        }
        if (body instanceof PsiBlockStatement) {
            final PsiCodeBlock block = ((PsiBlockStatement)body).getCodeBlock();
            final PsiElement[] children = block.getChildren();
            for (int i = 1; i < children.length - 1; i++) {
                //skip the braces
                newStatement.append(children[i].getText());
            }
        } else {
            newStatement.append(body.getText());
        }
        newStatement.append('}');
        replaceStatementAndShorten(newStatement.toString(), statement);
    }
}