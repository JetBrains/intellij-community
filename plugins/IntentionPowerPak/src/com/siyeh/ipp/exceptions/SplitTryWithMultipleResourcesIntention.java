/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class SplitTryWithMultipleResourcesIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new TryWithMultipleResourcesPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiTryStatement tryStatement = (PsiTryStatement)element.getParent();
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null) {
      return;
    }
    final StringBuilder newTryStatementText = new StringBuilder();
    int count = 0;
    for (PsiResourceListElement resource : resourceList) {
      if (count > 0) {
        newTryStatementText.append("{\n");
      }
      ++count;
      newTryStatementText.append("try (").append(resource.getText()).append(")");
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    newTryStatementText.append(tryBlock.getText());
    for (int i = 1; i < count; i++) {
      newTryStatementText.append("\n}");
    }
    final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    for (PsiCatchSection catchSection : catchSections) {
      newTryStatementText.append(catchSection.getText());
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      newTryStatementText.append("finally").append(finallyBlock.getText());
    }
    PsiReplacementUtil.replaceStatement(tryStatement, newTryStatementText.toString());
  }
}
