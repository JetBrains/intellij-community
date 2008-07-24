/*
 * Copyright 2008 Bas Leijdekkers
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

import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class CopyConcatenatedStringToClipboardIntention extends Intention {

    @Override @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new SimpleStringConcatenationPredicate();
    }

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        if (!(element instanceof PsiBinaryExpression)) {
            return;
        }
        PsiBinaryExpression concatenationExpression =
                (PsiBinaryExpression) element;
        PsiElement parent = concatenationExpression.getParent();
        while (parent instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) parent;
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (tokenType != JavaTokenType.PLUS) {
                break;
            }
            final PsiType type = binaryExpression.getType();
            if (type == null || !type.equalsToText("java.lang.String")) {
                break;
            }
            concatenationExpression = binaryExpression;
            parent = concatenationExpression.getParent();
        }
        final StringBuilder text = new StringBuilder();
        buildContatenationText(concatenationExpression, text);
        final String string = text.toString();
        final String unescapedString =
                StringUtil.unescapeStringCharacters(string);
        final Transferable contents = new StringSelection(unescapedString);
        CopyPasteManager.getInstance().setContents(contents);
    }

    private static void buildContatenationText(PsiExpression expression,
                                               StringBuilder out) {
        if (expression instanceof PsiLiteralExpression) {
            final String text = expression.getText();
            final PsiType type = expression.getType();
            if (type != null && (type.equalsToText("java.lang.String")
                    || type.equalsToText("char"))) {
                final int textLength = text.length();
                if (textLength > 2) {
                    out.append(text.substring(1, textLength - 1));
                }
            } else {
                out.append(text);
            }
        } else if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            final Project project = expression.getProject();
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            final PsiConstantEvaluationHelper evaluationHelper =
                    facade.getConstantEvaluationHelper();
            final Object result =
                    evaluationHelper.computeConstantExpression(expression);
            if (result != null) {
                out.append(result.toString());
            } else {
                final PsiExpression lhs = binaryExpression.getLOperand();
                buildContatenationText(lhs, out);
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs != null) {
                    buildContatenationText(rhs, out);
                }
            }
        } else {
            out.append("?");
        }
    }
}