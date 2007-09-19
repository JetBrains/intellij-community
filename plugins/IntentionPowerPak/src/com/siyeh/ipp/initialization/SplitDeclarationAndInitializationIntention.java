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
import com.siyeh.ipp.psiutils.HighlightUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class SplitDeclarationAndInitializationIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new SplitDeclarationAndInitializationPredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiField field = (PsiField)element.getParent();
        field.normalizeDeclaration();
        final PsiExpression initializer = field.getInitializer();
        if (initializer == null) {
            return;
        }
        final String initializerText = initializer.getText();
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }
        final boolean fieldIsStatic =
                field.hasModifierProperty(PsiModifier.STATIC);
        final PsiClassInitializer[] classInitializers =
                containingClass.getInitializers();
        PsiClassInitializer classInitializer = null;
        final int fieldOffset = field.getTextOffset();
        for (PsiClassInitializer existingClassInitializer : classInitializers) {
            final int initializerOffset =
                    existingClassInitializer.getTextOffset();
            if (initializerOffset <= fieldOffset) {
                continue;
            }
            final boolean initializerIsStatic =
                    existingClassInitializer.hasModifierProperty(
                            PsiModifier.STATIC);
            if (initializerIsStatic == fieldIsStatic) {
                classInitializer = existingClassInitializer;
                break;
            }
        }
        final PsiManager manager = field.getManager();
        final PsiElementFactory elementFactory = manager.getElementFactory();
        if (classInitializer == null) {
            classInitializer = elementFactory.createClassInitializer();
            classInitializer = (PsiClassInitializer)
                    containingClass.addAfter(classInitializer, field);
        }
        final PsiCodeBlock body = classInitializer.getBody();
        @NonNls final String initializationStatementText =
                field.getName() + " = " + initializerText + ';';
        final PsiExpressionStatement statement =
                (PsiExpressionStatement)elementFactory.createStatementFromText(
                        initializationStatementText, body);
        final PsiElement addedElement = body.add(statement);
        if (fieldIsStatic) {
            final PsiModifierList modifierList =
                    classInitializer.getModifierList();
            if (modifierList != null) {
                modifierList.setModifierProperty(PsiModifier.STATIC, true);
            }
        }
        initializer.delete();
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        codeStyleManager.reformat(field);
        codeStyleManager.reformat(classInitializer);
        HighlightUtil.highlightElements(Collections.singleton(addedElement));
    }
}