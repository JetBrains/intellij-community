/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
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
    final PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    if (tryStatement == null) return;
    CommentTracker commentTracker = new CommentTracker();
    @NonNls final StringBuilder newTryStatement = new StringBuilder("try");
    final Set<PsiClassType> exceptionsThrown = new HashSet<>();
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      newTryStatement.append(commentTracker.text(resourceList));
      ExceptionUtils.calculateExceptionsThrown(resourceList, exceptionsThrown);
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    final String tryBlockText = commentTracker.text(tryBlock);
    newTryStatement.append(tryBlockText);
    ExceptionUtils.calculateExceptionsThrown(tryBlock, exceptionsThrown);
    final Comparator<PsiType> comparator = new HierarchicalTypeComparator();
    final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    for (PsiCatchSection catchSection : catchSections) {
      final PsiParameter parameter = catchSection.getParameter();
      final PsiCodeBlock block = catchSection.getCatchBlock();
      if (parameter != null && block != null) {
        final PsiType caughtType = parameter.getType();
        List<PsiClassType> exceptionsToExpand = new ArrayList<>(exceptionsThrown.size());
        for (PsiClassType aExceptionsThrown : exceptionsThrown) {
          if (caughtType.isAssignableFrom(aExceptionsThrown)) {
            exceptionsToExpand.add(aExceptionsThrown);
          }
        }
        exceptionsThrown.removeAll(exceptionsToExpand);

        PsiClassType commonSuperType = null;
        PsiClass commonSuper = ObscureThrownExceptionsIntention.findCommonSuperClass(exceptionsToExpand.toArray(PsiClassType.EMPTY_ARRAY));
        if (commonSuper != null) {
          commonSuperType = JavaPsiFacade.getElementFactory(commonSuper.getProject()).createType(commonSuper);
          if (commonSuperType.equals(caughtType)) {
            commonSuperType = null;
          }
        }

        if (commonSuperType != null) {
          exceptionsToExpand = Collections.singletonList(commonSuperType);
        } else {
          Collections.sort(exceptionsToExpand, comparator);
        }
        for (PsiClassType thrownType : exceptionsToExpand) {
          newTryStatement.append("catch(").append(thrownType.getCanonicalText()).append(' ').append(parameter.getName()).append(')');
          newTryStatement.append(commentTracker.text(block));
        }
      }
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      newTryStatement.append("finally").append(commentTracker.text(finallyBlock));
    }
    final String newStatement = newTryStatement.toString();

    PsiReplacementUtil.replaceStatementAndShortenClassNames(tryStatement, newStatement, commentTracker);
  }
}