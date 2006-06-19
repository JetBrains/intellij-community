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
package com.siyeh.ipp.initialization;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class SplitDeclarationAndInitializationIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new SplitDeclarationAndInitializationPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiField field = (PsiField)element;
        field.normalizeDeclaration();
        final PsiExpression initializer = field.getInitializer();
        if (initializer == null) {
            return;
        }
        final String initializerText = initializer.getText();
        final PsiManager manager = field.getManager();
        final PsiElementFactory elementFactory = manager.getElementFactory();
        PsiClassInitializer classInitializer =
                elementFactory.createClassInitializer();
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }
        classInitializer =
                (PsiClassInitializer)containingClass.addAfter(classInitializer,
                        field);
        final PsiCodeBlock body = classInitializer.getBody();
        final String initializationStatementText =
                field.getName() + " = " + initializerText + ';';
        final PsiExpressionStatement statement =
                (PsiExpressionStatement)elementFactory.createStatementFromText(
                        initializationStatementText, body);
        body.add(statement);
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            final PsiModifierList modifierList =
                    classInitializer.getModifierList();
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
        }
        initializer.delete();
        final CodeStyleManager codeStyleManager =
                manager.getCodeStyleManager();
        codeStyleManager.reformat(field);
        codeStyleManager.reformat(classInitializer);
    }
}