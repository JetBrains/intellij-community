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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddBracesIntention extends MutablyNamedIntention {

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        final PsiStatement statement = getBody(element);
        return statement != null && !(statement instanceof PsiBlockStatement);
      }
    };
  }

  protected String getTextForElement(PsiElement element) {
    final PsiElement body = getBody(element);
    if (body == null) {
      return null;
    }

    return IntentionPowerPackBundle.message("add.braces.intention.name", getKeyword(body.getParent(), body));
  }

  @NotNull
  private static String getKeyword(@NotNull PsiElement parent, @NotNull PsiElement element) {
    if (parent instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)parent;
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      return element.equals(elseBranch) ? PsiKeyword.ELSE : PsiKeyword.IF;
    }
    final PsiElement firstChild = parent.getFirstChild();
    assert firstChild != null;
    return firstChild.getText();
  }

  protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
    final PsiStatement statement = getBody(element);
    if (statement == null) {
      return;
    }
    final String newStatement = "{\n" + statement.getText() + "\n}";
    replaceStatement(newStatement, statement);
  }

  @Nullable
  private static PsiStatement getBody(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiIfStatement) {
      final PsiIfStatement ifStatement = (PsiIfStatement)parent;
      if (isBetweenThen(ifStatement, element)) {
        return ifStatement.getThenBranch();
      }

      if (isBetweenElse(ifStatement, element)) {
        return ifStatement.getElseBranch();
      }
    }
    if (parent instanceof PsiWhileStatement) {
      return ((PsiWhileStatement)parent).getBody();
    }
    if (parent instanceof PsiDoWhileStatement) {
      return ((PsiDoWhileStatement)parent).getBody();
    }
    if (parent instanceof PsiForStatement) {
      return ((PsiForStatement)parent).getBody();
    }
    if (parent instanceof PsiForeachStatement) {
      return ((PsiForeachStatement)parent).getBody();
    }
    return null;
  }

  private static boolean isBetweenThen(@NotNull PsiIfStatement ifStatement, @NotNull PsiElement element) {
    final PsiElement rParenth = ifStatement.getRParenth();
    final PsiElement elseElement = ifStatement.getElseElement();

    if (rParenth == null) {
      return false;
    }

    if (elseElement == null) {
      return true;
    }

    final TextRange rParenthTextRangeTextRange = rParenth.getTextRange();
    final TextRange elseElementTextRange = elseElement.getTextRange();
    final TextRange elementTextRange = element.getTextRange();

    return new TextRange(rParenthTextRangeTextRange.getEndOffset(), elseElementTextRange.getStartOffset()).contains(elementTextRange);
  }

  private static boolean isBetweenElse(@NotNull PsiIfStatement ifStatement, @NotNull PsiElement element) {
    final PsiElement elseElement = ifStatement.getElseElement();

    if (elseElement == null) {
      return false;
    }

    final TextRange ifStatementTextRange = ifStatement.getTextRange();
    final TextRange elseElementTextRange = elseElement.getTextRange();
    final TextRange elementTextRange = element.getTextRange();

    return new TextRange(elseElementTextRange.getStartOffset(), ifStatementTextRange.getEndOffset()).contains(elementTextRange);
  }
}