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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class DetailExceptionsIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new DetailExceptionsPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiJavaToken token = (PsiJavaToken)element;
        final PsiTryStatement tryStatement =
                (PsiTryStatement)token.getParent();
        if (tryStatement == null) {
            return;
        }
        final String text = tryStatement.getText();
        final int length = text.length();
        @NonNls final StringBuilder newTryStatement = new StringBuilder(length);
        newTryStatement.append("try");
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (tryBlock == null) {
            return;
        }
        final String tryBlockText = tryBlock.getText();
        newTryStatement.append(tryBlockText);
        final Set<PsiType> exceptionsThrown = new HashSet<PsiType>(10);
        ExceptionUtils.calculateExceptionsThrownForCodeBlock(tryBlock,
                exceptionsThrown);
        final HeirarchicalTypeComparator comparator =
                new HeirarchicalTypeComparator();
        final List<PsiType> exceptionsAlreadyEmitted =
                new ArrayList<PsiType>(10);
        final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        for (PsiCatchSection catchSection : catchSections) {
            final PsiParameter param = catchSection.getParameter();
            final PsiCodeBlock block = catchSection.getCatchBlock();
            if (param != null && block != null) {
                final PsiType caughtType = param.getType();
                final List<PsiType> exceptionsToExpand =
                        new ArrayList<PsiType>(10);
                for (Object aExceptionsThrown : exceptionsThrown) {
                    final PsiType thrownType = (PsiType)aExceptionsThrown;
                    if (caughtType.isAssignableFrom(thrownType)) {
                        exceptionsToExpand.add(thrownType);
                    }
                }
                exceptionsToExpand.removeAll(exceptionsAlreadyEmitted);
                Collections.sort(exceptionsToExpand, comparator);
                for (PsiType thrownType : exceptionsToExpand) {
                    newTryStatement.append("catch(");
                    final String exceptionType =
                            thrownType.getPresentableText();
                    newTryStatement.append(exceptionType);
                    newTryStatement.append(' ');
                    final String parameterName = param.getName();
                    newTryStatement.append(parameterName);
                    newTryStatement.append(')');
                    final String blockText = block.getText();
                    newTryStatement.append(blockText);
                    exceptionsAlreadyEmitted.add(thrownType);
                }
            }
        }
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
            newTryStatement.append("finally");
            final String finallyBlockText = finallyBlock.getText();
            newTryStatement.append(finallyBlockText);
        }
        final String newStatement = newTryStatement.toString();
        replaceStatement(newStatement, tryStatement);
    }
}