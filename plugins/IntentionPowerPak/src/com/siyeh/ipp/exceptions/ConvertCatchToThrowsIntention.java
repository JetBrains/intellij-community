/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ConvertCatchToThrowsIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertCatchtoThrowsPredicate();
    }

    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiCatchSection catchSection =
                (PsiCatchSection) element.getParent();
        final PsiType catchType = catchSection.getCatchType();
        if (!(catchType instanceof PsiClassType)) {
            return;
        }
        final PsiClassType classType = (PsiClassType) catchType;
        final PsiMethod method =
                PsiTreeUtil.getParentOfType(catchSection, PsiMethod.class);
        if (method == null) {
            return;
        }
        // todo warn if method implements or overrides some base method
        //             Warning
        // "Method xx() of class XX implements/overrides method of class
        // YY. Do you want to modify the base method?"
        //                                             [Yes][No][Cancel]
        final PsiReferenceList throwsList = method.getThrowsList();
        final PsiManager manager = element.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        final PsiJavaCodeReferenceElement referenceElement =
                factory.createReferenceElementByType(classType);
        throwsList.add(referenceElement);
        final PsiTryStatement tryStatement = catchSection.getTryStatement();
        final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        if (catchSections.length > 1) {
            catchSection.delete();
        } else {
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final PsiElement parent = tryStatement.getParent();
            final PsiStatement[] statements = tryBlock.getStatements();
            for (PsiStatement statement : statements) {
                parent.addBefore(statement, tryStatement);
            }
            tryStatement.delete();
        }
    }
}