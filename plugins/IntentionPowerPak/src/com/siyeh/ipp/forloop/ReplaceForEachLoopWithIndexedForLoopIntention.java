/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceForEachLoopWithIndexedForLoopIntention extends Intention {

    @Override
    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new IndexedForEachLoopPredicate();
    }

    @Override
    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiForeachStatement statement =
                (PsiForeachStatement)element.getParent();
        if (statement == null) {
            return;
        }
        final PsiExpression iteratedValue =
                ParenthesesUtils.stripParentheses(statement.getIteratedValue());
        if (iteratedValue == null) {
            return;
        }
        final PsiParameter iterationParameter =
                statement.getIterationParameter();
        final PsiType type = iterationParameter.getType();
        final PsiType iteratedValueType = iteratedValue.getType();
        if (iteratedValueType == null) {
            return;
        }
        final boolean isArray = iteratedValueType instanceof PsiArrayType;
        final Project project = statement.getProject();
        final JavaCodeStyleManager codeStyleManager =
                JavaCodeStyleManager.getInstance(project);
        final String indexText =
                codeStyleManager.suggestUniqueVariableName("i", statement, true);
        final String variableNameRoot;
        final String iteratedValueText = iteratedValue.getText();
        if (iteratedValue instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)iteratedValue;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if (name == null) {
                return;
            }
            if (name.startsWith("to") && name.length() > 2) {
                variableNameRoot = StringUtil.decapitalize(name.substring(2));
            } else if (name.startsWith("get") && name.length() > 3) {
                variableNameRoot = StringUtil.decapitalize(name.substring(3));
            } else {
                variableNameRoot = name;
            }
        } else if (iteratedValue instanceof PsiTypeCastExpression) {
            final PsiTypeCastExpression castExpression =
                    (PsiTypeCastExpression) iteratedValue;
            final PsiExpression operand = castExpression.getOperand();
            final PsiExpression strippedOperand =
                    ParenthesesUtils.stripParentheses(operand);
            if (strippedOperand == null) {
                variableNameRoot = "";
            } else {
                variableNameRoot = strippedOperand.getText();
            }
        } else if (iteratedValue instanceof PsiJavaCodeReferenceElement) {
            final PsiJavaCodeReferenceElement referenceElement =
                    (PsiJavaCodeReferenceElement) iteratedValue;
            final String referenceName = referenceElement.getReferenceName();
            if (referenceName == null) {
                variableNameRoot = iteratedValueText;
            } else {
                variableNameRoot = referenceName;
            }
        } else {
            variableNameRoot = iteratedValueText;
        }
        final String lengthText;
        if (isArray) {
            lengthText = codeStyleManager.suggestUniqueVariableName(
                    variableNameRoot + "Length", statement, true);
        } else {
            lengthText = codeStyleManager.suggestUniqueVariableName(
                    variableNameRoot + "Size", statement, true);
        }
        final CodeStyleSettings codeStyleSettings =
                CodeStyleSettingsManager.getSettings(project);
        if (iteratedValue instanceof PsiMethodCallExpression) {
            final String variableName =
                    codeStyleManager.suggestUniqueVariableName(
                            variableNameRoot, statement, true);
            final StringBuilder declaration = new StringBuilder();
            if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
                declaration.append("final ");
            }
            declaration.append(iteratedValueType.getCanonicalText());
            declaration.append(' ');
            declaration.append(variableName);
            declaration.append('=');
            declaration.append(iteratedValueText);
            declaration.append(';');
            final PsiElementFactory elementFactory =
                    JavaPsiFacade.getElementFactory(project);
            final PsiStatement declarationStatement =
                    elementFactory.createStatementFromText(
                            declaration.toString(), statement);
            statement.getParent().addBefore(declarationStatement, statement);
        }
        @NonNls final StringBuilder newStatement = new StringBuilder();
        newStatement.append("for(int ");
        newStatement.append(indexText);
        newStatement.append(" = 0, ");

        newStatement.append(lengthText);
        newStatement.append(" = ");
        if (iteratedValue instanceof PsiTypeCastExpression) {
            newStatement.append('(');
            newStatement.append(iteratedValueText);
            newStatement.append(')');
        } else {
            newStatement.append(iteratedValueText);
        }
        if (isArray) {
            newStatement.append(".length;");
        } else {
            newStatement.append(".size();");
        }
        newStatement.append(indexText);
        newStatement.append('<');
        newStatement.append(lengthText);
        newStatement.append(';');
        newStatement.append(indexText);
        newStatement.append("++)");
        newStatement.append("{ ");
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
            newStatement.append("final ");
        }
        newStatement.append(type.getCanonicalText());
        newStatement.append(' ');
        newStatement.append(iterationParameter.getName());
        newStatement.append(" = ");
        if (iteratedValue instanceof PsiTypeCastExpression) {
            newStatement.append('(');
            newStatement.append(iteratedValueText);
            newStatement.append(')');
        } else {
            newStatement.append(iteratedValueText);
        }
        if (isArray) {
            newStatement.append('[');
            newStatement.append(indexText);
            newStatement.append("];");
        } else {
            newStatement.append(".get(");
            newStatement.append(indexText);
            newStatement.append(");");
        }
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