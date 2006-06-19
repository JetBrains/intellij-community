/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ReplaceForEachLoopWithForLoopIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ForEachLoopPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiForeachStatement statement =
                (PsiForeachStatement)element.getParent();
        if (statement == null) {
            return;
        }
        final PsiManager psiManager = statement.getManager();
        final Project project = psiManager.getProject();
        final CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(project);
        final PsiExpression iteratedValue = statement.getIteratedValue();
        if (iteratedValue == null) {
            return;
        }
        @NonNls final StringBuilder newStatement = new StringBuilder();
        final PsiParameter iterationParameter =
                statement.getIterationParameter();
        final PsiType type = iterationParameter.getType();
        if (iteratedValue.getType() instanceof PsiArrayType) {
            final String index =
                    codeStyleManager.suggestUniqueVariableName(
                            "i", statement, true);
            newStatement.append("for(int ");
            newStatement.append(index);
            newStatement.append(" = 0;");
            newStatement.append(index);
            newStatement.append('<');
            newStatement.append(iteratedValue.getText());
            newStatement.append(".length;");
            newStatement.append(index);
            newStatement.append("++)");
            newStatement.append("{ ");
            newStatement.append(type.getPresentableText());
            newStatement.append(' ');
            newStatement.append(iterationParameter.getName());
            newStatement.append(" = ");
            newStatement.append(iteratedValue.getText());
            newStatement.append('[');
            newStatement.append(index);
            newStatement.append("];");
        } else {
            final String iterator =
                    codeStyleManager.suggestUniqueVariableName(
                            "it", statement, true);
            final String typeText = type.getPresentableText();
            newStatement.append("for(java.util.Iterator ");
            newStatement.append(iterator);
            newStatement.append(" = ");
            newStatement.append(iteratedValue.getText());
            newStatement.append(".iterator();");
            newStatement.append(iterator);
            newStatement.append(".hasNext();)");
            newStatement.append('{');
            newStatement.append(typeText);
            newStatement.append(' ');
            newStatement.append(iterationParameter.getName());
            newStatement.append(" = ");
            newStatement.append('(');
            newStatement.append(typeText);
            newStatement.append(')');
            newStatement.append(iterator);
            newStatement.append(".next();");
        }
        final PsiStatement body = statement.getBody();
        if (body == null) {
            return;
        }
        if (body instanceof PsiBlockStatement) {
            final PsiCodeBlock block =
                    ((PsiBlockStatement)body).getCodeBlock();
            final PsiElement[] children = block.getChildren();
            for (int i = 1; i < children.length - 1; i++) {
                //skip the braces
                newStatement.append(children[i].getText());
            }
        } else {
            newStatement.append(body.getText());
        }
        newStatement.append('}');
        replaceStatement(newStatement.toString(), statement);
    }
}