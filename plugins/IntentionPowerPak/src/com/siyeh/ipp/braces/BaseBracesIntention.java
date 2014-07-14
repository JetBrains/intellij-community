/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final int offset = element.getTextOffset();
      if (thenBranch != null && offset > thenBranch.getTextOffset()) {
        final PsiKeyword elseElement = ifStatement.getElseElement();
        if (elseElement == null || offset < elseElement.getTextOffset()) {
          // no 'else' branch or after 'then' branch but before 'else' keyword
          return null;
        }
        return ifStatement.getElseBranch();
      }
      return thenBranch;
    }
    if (parent instanceof PsiLoopStatement) {
      return ((PsiLoopStatement)parent).getBody();
    }
    return null;
  }
}
