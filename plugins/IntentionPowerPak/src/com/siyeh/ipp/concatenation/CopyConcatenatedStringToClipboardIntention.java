/*
 * Copyright 2008-2011 Bas Leijdekkers
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

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class CopyConcatenatedStringToClipboardIntention extends Intention {

    @Override @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new SimpleStringConcatenationPredicate(false);
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
        buildConcatenationText(concatenationExpression, text);
        final Transferable contents = new StringSelection(text.toString());
        CopyPasteManager.getInstance().setContents(contents);
    }

    private static void buildConcatenationText(PsiExpression expression,
                                               StringBuilder out) {
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            buildConcatenationText(lhs, out);
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs != null) {
                buildConcatenationText(rhs, out);
            }
        } else {
            final Object value =
                    ExpressionUtils.computeConstantExpression(expression);
            if (value == null) {
                out.append('?');
            } else {
                out.append(value.toString());
            }
        }
    }
}
