/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper class to implement quick-fix which collects removed comments from the PSI and can restore them at once.
 *
 * After this object restores comments, it becomes unusable.
 *
 * @author Tagir Valeev
 */
public class CommentTracker {
  private List<PsiElement> ignoredParents = new ArrayList<>();
  private List<PsiComment> comments = new ArrayList<>();

  /**
   * Marks the element as used and returns its text. The comments from used elements will not be extracted.
   *
   * @param element element to return the text
   * @return a text to be inserted into refactored code
   */
  public @NotNull String text(@NotNull PsiElement element) {
    checkState();
    ignoredParents.add(element);
    return element.getText();
  }

  /**
   * Marks the element as used and returns it. The comments from used elements will not be extracted.
   *
   * @param element element to mark
   * @param <T> the type of the element
   * @return the passed argument
   */
  public @NotNull <T extends PsiElement> T markUsed(@NotNull T element) {
    checkState();
    ignoredParents.add(element);
    return element;
  }

  /**
   * Deletes given PsiElement collecting all the comments inside it.
   *
   * @param element element to delete
   */
  public void delete(@NotNull PsiElement element) {
    grabComments(element);
    element.delete();
  }

  /**
   * Deletes given PsiElement replacing it with the comments including comments inside the deleted element
   * and previously gathered comments.
   *
   * <p>After calling this method the tracker cannot be used anymore.</p>
   *
   * @param element element to delete
   */
  public void deleteAndRestoreComments(@NotNull PsiElement element) {
    grabComments(element);
    insertCommentsBefore(element);
    element.delete();
  }

  /**
   * Replaces given PsiElement collecting all the comments inside it.
   *
   * @param element element to replace
   * @param replacement replacement element
   * @return the element which was actually inserted in the tree (either <code>replacement</code> or its copy)
   */
  public @NotNull PsiElement replace(@NotNull PsiElement element, @NotNull PsiElement replacement) {
    grabComments(element);
    return element.replace(replacement);
  }

  /**
   * Replaces given PsiElement collecting all the comments inside it and restore comments putting them
   * to the appropriate place before replaced element.
   *
   * <p>After calling this method the tracker cannot be used anymore.</p>
   *
   * @param element element to replace
   * @param replacement replacement element
   * @return the element which was actually inserted in the tree (either <code>replacement</code> or its copy)
   */
  public @NotNull PsiElement replaceAndRestoreComments(@NotNull PsiElement element, @NotNull PsiElement replacement) {
    PsiElement result = replace(element, replacement);
    PsiElement anchor = PsiTreeUtil.getNonStrictParentOfType(result, PsiStatement.class, PsiLambdaExpression.class, PsiVariable.class);
    if(anchor instanceof PsiLambdaExpression && anchor != result) {
      anchor = ((PsiLambdaExpression)anchor).getBody();
    }
    if(anchor == null) anchor = result;
    insertCommentsBefore(anchor);
    return result;
  }

  /**
   * Inserts gathered comments just before given anchor element
   *
   * <p>After calling this method the tracker cannot be used anymore.</p>
   *
   * @param anchor
   */
  public void insertCommentsBefore(@NotNull PsiElement anchor) {
    checkState();
    if(!comments.isEmpty()) {
      PsiElement parent = anchor.getParent();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
      for(PsiComment comment : comments) {
        PsiElement added = parent.addBefore(factory.createCommentFromText(comment.getText(), anchor), anchor);
        PsiElement prevSibling = added.getPrevSibling();
        if(prevSibling instanceof PsiWhiteSpace) {
          PsiWhiteSpace whiteSpaceBefore = (PsiWhiteSpace)prevSibling;
          PsiElement prev = anchor.getPrevSibling();
          if (prev instanceof PsiWhiteSpace) {
            prev.replace(whiteSpaceBefore);
          }
          else {
            parent.addBefore(whiteSpaceBefore, anchor);
          }
        }
      }
    }
    comments = null;
  }

  private void grabComments(PsiElement element) {
    checkState();
    for(PsiComment comment : PsiTreeUtil.collectElementsOfType(element, PsiComment.class)) {
      if(ignoredParents.stream().noneMatch(parent -> PsiTreeUtil.isAncestor(parent, comment, false))) {
        comments.add(comment);
      }
    }
  }

  private void checkState() {
    if(comments == null) {
      throw new IllegalStateException(getClass().getSimpleName()+" has been already used");
    }
  }
}
