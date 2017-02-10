/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DetailExceptionsIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new DetailExceptionsPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken token = (PsiJavaToken)element;
    PsiElement parent = token.getParent();
    if (parent instanceof PsiCatchSection) {
      parent = parent.getParent();
    }
    if (!(parent instanceof PsiTryStatement)) {
      return;
    }
    final PsiTryStatement tryStatement = (PsiTryStatement)parent;
    @NonNls final StringBuilder newTryStatement = new StringBuilder("try");
    final Set<PsiClassType> exceptionsThrown = new HashSet<>();
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      newTryStatement.append(resourceList.getText());
      ExceptionUtils.calculateExceptionsThrown(resourceList, exceptionsThrown);
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    final String tryBlockText = tryBlock.getText();
    newTryStatement.append(tryBlockText);
    ExceptionUtils.calculateExceptionsThrown(tryBlock, exceptionsThrown);
    final Comparator<PsiType> comparator = new HierarchicalTypeComparator();
    final List<PsiType> exceptionsAlreadyEmitted = new ArrayList<>();
    final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    for (PsiCatchSection catchSection : catchSections) {
      final PsiParameter parameter = catchSection.getParameter();
      final PsiCodeBlock block = catchSection.getCatchBlock();
      if (parameter != null && block != null) {
        final PsiType caughtType = parameter.getType();
        final List<PsiType> exceptionsToExpand = new ArrayList<>(10);
        for (Object aExceptionsThrown : exceptionsThrown) {
          final PsiType thrownType = (PsiType)aExceptionsThrown;
          if (caughtType.isAssignableFrom(thrownType)) {
            exceptionsToExpand.add(thrownType);
          }
        }
        exceptionsToExpand.removeAll(exceptionsAlreadyEmitted);
        Collections.sort(exceptionsToExpand, comparator);
        for (PsiType thrownType : exceptionsToExpand) {
          newTryStatement.append("catch(").append(thrownType.getCanonicalText()).append(' ').append(parameter.getName()).append(')');
          newTryStatement.append(block.getText());
          exceptionsAlreadyEmitted.add(thrownType);
        }
      }
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      newTryStatement.append("finally").append(finallyBlock.getText());
    }
    final String newStatement = newTryStatement.toString();
    PsiReplacementUtil.replaceStatementAndShortenClassNames(tryStatement, newStatement);
  }
}