/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public abstract class BaseBracesIntention extends MutablyNamedIntention {

  protected final String getTextForElement(PsiElement element) {
    final PsiElement body = getSurroundingStatement(element);
    if (body == null) {
      return null;
    }

    return IntentionPowerPackBundle.message(getMessageKey(), getKeyword(body.getParent(), body));
  }

  @NotNull
  protected abstract String getMessageKey();

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


  @Nullable
  protected static PsiStatement getSurroundingStatement(@NotNull PsiElement element) {
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
