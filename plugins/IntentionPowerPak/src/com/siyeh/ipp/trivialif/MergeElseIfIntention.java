/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MergeElseIfIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new MergeElseIfPredicate();
  }

  @Override
  public void processIntention(PsiElement element) {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiIfStatement parentStatement = (PsiIfStatement)token.getParent();
    assert parentStatement != null;
    final PsiBlockStatement elseBranch = (PsiBlockStatement)parentStatement.getElseBranch();
    assert elseBranch != null;
    final PsiElement lastChild = elseBranch.getLastChild();
    final PsiCodeBlock elseBranchBlock = elseBranch.getCodeBlock();
    final PsiStatement elseBranchContents = elseBranchBlock.getStatements()[0];
    final PsiElement grandParent = parentStatement.getParent();
    if (lastChild instanceof PsiComment) {
      addElementAndPrevWhiteSpace(lastChild, grandParent, parentStatement);
    }
    final List<PsiComment> before = new ArrayList<>(1);
    final List<PsiComment> after = new ArrayList<>(1);
    collectComments(elseBranchContents, before, after);
    for (PsiComment comment : before) {
      addElementAndPrevWhiteSpace(comment, parentStatement, token);
    }
    for (PsiComment comment : after) {
      grandParent.addAfter(comment, parentStatement);
    }
    PsiReplacementUtil.replaceStatement(elseBranch, elseBranchContents.getText());
  }

  private static void addElementAndPrevWhiteSpace(PsiElement element, PsiElement container, PsiElement anchor) {
    final PsiElement sibling = element.getPrevSibling();
    container.addAfter(element, anchor);
    if (sibling instanceof PsiWhiteSpace) {
      container.addAfter(sibling, anchor);
    }
  }

  /**
   * Before comments are added in reverse order of appearance in the code.
   */
  private static void collectComments(PsiStatement statement, List<PsiComment> before, List<PsiComment> after) {
    PsiElement prevSibling = statement.getPrevSibling();
    while (prevSibling != null) {
      if (prevSibling instanceof PsiComment) {
        before.add((PsiComment)prevSibling);
      }
      prevSibling = prevSibling.getPrevSibling();
    }
    PsiElement nextSibing = statement.getNextSibling();
    while (nextSibing != null) {
      if (nextSibing instanceof PsiComment) {
        after.add((PsiComment)nextSibing);
      }
      nextSibing = nextSibing.getNextSibling();
    }
  }
}