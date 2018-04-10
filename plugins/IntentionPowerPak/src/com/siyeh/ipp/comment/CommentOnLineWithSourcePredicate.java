/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.comment;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class CommentOnLineWithSourcePredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiComment) || element instanceof PsiDocComment || element.getTextOffset() == 0) {
      return false;
    }
    final PsiComment comment = (PsiComment)element;
    if (comment instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)comment)) {
      return false;
    }
    final IElementType type = comment.getTokenType();
    if (JavaTokenType.C_STYLE_COMMENT.equals(type)) {
      if (!comment.getText().endsWith("*/")) {
        return false;
      }
    }
    else if (!JavaTokenType.END_OF_LINE_COMMENT.equals(type)) {
      return false; // can't move JSP comments
    }
    final PsiElement prevSibling = PsiTreeUtil.prevLeaf(element);
    if (prevSibling == null || prevSibling.getTextLength() == 0) {
      return false;
    }
    if (!(prevSibling instanceof PsiWhiteSpace)) {
      return true;
    }
    return prevSibling.getText().indexOf('\n') < 0 && PsiTreeUtil.prevLeaf(prevSibling) != null;
  }
}