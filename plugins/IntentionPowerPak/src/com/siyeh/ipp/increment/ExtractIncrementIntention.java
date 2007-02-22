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
package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ExtractIncrementIntention extends MutablyNamedIntention {

    public String getTextForElement(PsiElement element) {
        final PsiJavaToken sign;
        if (element instanceof PsiPostfixExpression) {
            sign = ((PsiPostfixExpression)element).getOperationSign();
        } else {
            sign = ((PsiPrefixExpression)element).getOperationSign();
        }
        final String operator = sign.getText();
        return IntentionPowerPackBundle.message(
                "extract.increment.intention.name", operator);
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ExtractIncrementPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiExpression operand;
        if (element instanceof PsiPostfixExpression) {
            operand = ((PsiPostfixExpression)element).getOperand();
        } else {
            operand = ((PsiPrefixExpression)element).getOperand();
        }
        if (operand == null) {
            return;
        }
        PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
        assert statement != null;
        PsiElement parent = statement.getParent();
        if (parent == null) {
            return;
        }

        final PsiManager manager = element.getManager();
        final PsiElementFactory factory = manager.getElementFactory();

        if (parent instanceof PsiIfStatement || parent instanceof PsiLoopStatement) {
            int elementOffsetInStatement = element.getTextRange().getStartOffset() - statement.getTextRange().getStartOffset();
            int elementLength = element.getTextLength();

            assert elementOffsetInStatement >= 0;
            assert elementLength > 0;

            PsiCodeBlock codeBlock = factory.createCodeBlockFromText("{ " + statement.getText() + "}", parent);
            codeBlock = (PsiCodeBlock) statement.replace(codeBlock);
            statement = codeBlock.getStatements()[0];
            parent = statement.getParent();

            element = statement.getContainingFile().findElementAt(statement.getTextRange().getStartOffset() + elementOffsetInStatement);

            while (element != null && element.getTextLength() < elementLength) element = element.getParent();
            if (element == null) return; // Shall not happen BTW
        }
      
        final String newStatementText = element.getText() + ';';
        final PsiStatement newCall =
                factory.createStatementFromText(newStatementText, null);
        final PsiElement insertedElement;
        if (element instanceof PsiPostfixExpression) {
            insertedElement = parent.addAfter(newCall, statement);
        } else {
            insertedElement = parent.addBefore(newCall, statement);
        }
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
        replaceExpression(operand.getText(), (PsiExpression)element);
    }
}