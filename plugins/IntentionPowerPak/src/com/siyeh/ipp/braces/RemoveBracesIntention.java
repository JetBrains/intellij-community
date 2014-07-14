/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.braces;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.util.FileTypeUtils;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class RemoveBracesIntention extends BaseBracesIntention {

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        final PsiStatement statement = getSurroundingStatement(element);
        if (statement == null || !(statement instanceof PsiBlockStatement)) {
          return false;
        }

        final PsiStatement[] statements = ((PsiBlockStatement)statement).getCodeBlock().getStatements();
        if (statements.length != 1 || statements[0] instanceof PsiDeclarationStatement) {
          return false;
        }
        final PsiFile file = statement.getContainingFile();
        //this intention doesn't work in JSP files, as it can't tell about tags
        // inside the braces
        return !FileTypeUtils.isInServerPageFile(file);
      }
    };
  }

  @NotNull
  @Override
  protected String getMessageKey() {
    return "remove.braces.intention.name";
  }

  protected void processIntention(@NotNull PsiElement element)
    throws IncorrectOperationException {
    final PsiStatement body = getSurroundingStatement(element);
    if (body == null || !(body instanceof PsiBlockStatement)) return;
    final PsiBlockStatement blockStatement = (PsiBlockStatement)body;

    final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
    final PsiStatement[] statements = codeBlock.getStatements();
    final PsiStatement statement = statements[0];

    handleComments(blockStatement, codeBlock);

    final String text = statement.getText();
    PsiReplacementUtil.replaceStatement(blockStatement, text);
  }

  private static void handleComments(PsiBlockStatement blockStatement, PsiCodeBlock codeBlock) {
    final PsiElement parent = blockStatement.getParent();
    assert parent != null;
    final PsiElement grandParent = parent.getParent();
    assert grandParent != null;
    PsiElement sibling = codeBlock.getFirstChild();
    assert sibling != null;
    sibling = sibling.getNextSibling();
    while (sibling != null) {
      if (sibling instanceof PsiComment) {
        grandParent.addBefore(sibling, parent);
      }
      sibling = sibling.getNextSibling();
    }
    final PsiElement lastChild = blockStatement.getLastChild();
    if (lastChild instanceof PsiComment) {
      final PsiElement nextSibling = parent.getNextSibling();
      grandParent.addAfter(lastChild, nextSibling);
    }
  }
}