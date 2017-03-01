/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A helper class to implement quick-fix which collects removed comments from the PSI and can restore them at once.
 *
 * After this object restores comments, it becomes unusable.
 *
 * @author Tagir Valeev
 */
public class CommentTracker {
  private Set<PsiElement> ignoredParents = new HashSet<>();
  private List<PsiComment> comments = new ArrayList<>();

  /**
   * Marks the element as unchanged and returns its text. The unchanged elements are assumed to be preserved
   * in the resulting code as is, so the comments from them will not be extracted.
   *
   * @param element element to return the text
   * @return a text to be inserted into refactored code
   */
  public @NotNull String text(@NotNull PsiElement element) {
    checkState();
    addIgnored(element);
    return element.getText();
  }

  /**
   * Marks the element as unchanged and returns it. The unchanged elements are assumed to be preserved
   * in the resulting code as is, so the comments from them will not be extracted.
   *
   * @param element element to mark
   * @param <T> the type of the element
   * @return the passed argument
   */
  public @NotNull <T extends PsiElement> T markUnchanged(@NotNull T element) {
    checkState();
    addIgnored(element);
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
   * Deletes all given PsiElement's collecting all the comments inside them.
   *
   * @param elements elements to delete (all not null)
   */
  public void delete(@NotNull PsiElement... elements) {
    for(PsiElement element : elements) {
      delete(element);
    }
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
    insertCommentsBefore(element instanceof PsiVariable ? element.getParent() : element);
    element.delete();
  }

  /**
   * Replaces given PsiElement collecting all the comments inside it.
   *
   * @param element element to replace
   * @param replacement replacement element
   * @return the element which was actually inserted in the tree (either {@code replacement} or its copy)
   */
  public @NotNull PsiElement replace(@NotNull PsiElement element, @NotNull PsiElement replacement) {
    grabComments(element);
    return element.replace(replacement);
  }

  /**
   * Creates a replacement element from the text and replaces given element,
   * collecting all the comments inside it.
   *
   * <p>
   *   The type of the created replacement will mimic the type of supplied element.
   *   Supported element types are: {@link PsiExpression}, {@link PsiStatement},
   *   {@link PsiTypeElement}, {@link PsiIdentifier}, {@link PsiComment}.
   * </p>
   *
   * @param element element to replace
   * @param text replacement text
   * @return the element which was actually inserted in the tree
   */
  public @NotNull PsiElement replace(@NotNull PsiElement element, @NotNull String text) {
    PsiElement replacement = createElement(element, text);
    return replace(element, replacement);
  }

  /**
   * Replaces given PsiElement collecting all the comments inside it and restores comments putting them
   * to the appropriate place before replaced element.
   *
   * <p>After calling this method the tracker cannot be used anymore.</p>
   *
   * @param element element to replace
   * @param replacement replacement element
   * @return the element which was actually inserted in the tree (either {@code replacement} or its copy)
   */
  public @NotNull PsiElement replaceAndRestoreComments(@NotNull PsiElement element, @NotNull PsiElement replacement) {
    PsiElement result = replace(element, replacement);
    PsiElement anchor = PsiTreeUtil.getNonStrictParentOfType(result, PsiStatement.class, PsiLambdaExpression.class, PsiVariable.class);
    if(anchor instanceof PsiLambdaExpression && anchor != result) {
      anchor = ((PsiLambdaExpression)anchor).getBody();
    }
    if(anchor instanceof PsiVariable && anchor.getParent() instanceof PsiDeclarationStatement) {
      anchor = anchor.getParent();
    }
    if(anchor == null) anchor = result;
    insertCommentsBefore(anchor);
    return result;
  }

  /**
   * Creates a replacement element from the text and replaces given element,
   * collecting all the comments inside it and restores comments putting them
   * to the appropriate place before replaced element.
   *
   * <p>After calling this method the tracker cannot be used anymore.</p>
   *
   * <p>
   *   The type of the created replacement will mimic the type of supplied element.
   *   Supported element types are: {@link PsiExpression}, {@link PsiStatement},
   *   {@link PsiTypeElement}, {@link PsiIdentifier}, {@link PsiComment}.
   * </p>
   *
   * @param element element to replace
   * @param text replacement text
   * @return the element which was actually inserted in the tree
   */
  public @NotNull PsiElement replaceAndRestoreComments(@NotNull PsiElement element, @NotNull String text) {
    PsiElement replacement = createElement(element, text);
    return replaceAndRestoreComments(element, replacement);
  }

  @NotNull
  private static PsiElement createElement(@NotNull PsiElement element, @NotNull String text) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    PsiElement replacement;
    if(element instanceof PsiExpression) {
      replacement = factory.createExpressionFromText(text, element);
    } else if(element instanceof PsiStatement) {
      replacement = factory.createStatementFromText(text, element);
    } else if(element instanceof PsiTypeElement) {
      replacement = factory.createTypeElementFromText(text, element);
    } else if(element instanceof PsiIdentifier) {
      replacement = factory.createIdentifier(text);
    } else if(element instanceof PsiComment) {
      replacement = factory.createCommentFromText(text, element);
    } else {
      throw new IllegalArgumentException("Unsupported element type: "+element);
    }
    return replacement;
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
        if (shouldIgnore(comment)) continue;
        PsiElement added = parent.addBefore(factory.createCommentFromText(comment.getText(), anchor), anchor);
        PsiElement prevSibling = added.getPrevSibling();
        if (prevSibling instanceof PsiWhiteSpace) {
          ASTNode whiteSpaceBefore = normalizeWhiteSpace((PsiWhiteSpace)prevSibling);
          PsiElement prev = anchor.getPrevSibling();
          parent.getNode().addChild(whiteSpaceBefore, anchor.getNode());
          if (prev instanceof PsiWhiteSpace) {
            prev.delete();
          }
        }
      }
    }
    comments = null;
  }

  @NotNull
  private static ASTNode normalizeWhiteSpace(PsiWhiteSpace whiteSpace) {
    String text = whiteSpace.getText();
    int endLPos = text.lastIndexOf('\n');
    if(text.lastIndexOf('\n', endLPos-1) >= 0) {
      // has at least two line breaks
      return ASTFactory.whitespace(text.substring(endLPos));
    }
    return ASTFactory.whitespace(text);
  }

  private boolean shouldIgnore(PsiComment comment) {
    return ignoredParents.stream().anyMatch(p -> PsiTreeUtil.isAncestor(p, comment, false));
  }

  private void grabComments(PsiElement element) {
    checkState();
    for(PsiComment comment : PsiTreeUtil.collectElementsOfType(element, PsiComment.class)) {
      if (!shouldIgnore(comment)) {
        comments.add(comment);
      }
    }
  }

  private void checkState() {
    if(comments == null) {
      throw new IllegalStateException(getClass().getSimpleName()+" has been already used");
    }
  }

  private void addIgnored(PsiElement element) {
    if(element instanceof LeafPsiElement && !(element instanceof PsiComment)) return;
    ignoredParents.add(element);
  }

  public static String textWithSurroundingComments(PsiElement element) {
    Predicate<PsiElement> commentOrWhiteSpace = e -> e instanceof PsiComment || e instanceof PsiWhiteSpace;
    List<PsiElement> prev = StreamEx.iterate(element.getPrevSibling(), commentOrWhiteSpace, PsiElement::getPrevSibling).toList();
    List<PsiElement> next = StreamEx.iterate(element.getNextSibling(), commentOrWhiteSpace, PsiElement::getNextSibling).toList();
    if(StreamEx.of(prev, next).flatCollection(Function.identity()).anyMatch(PsiComment.class::isInstance)) {
      return StreamEx.ofReversed(prev).append(element).append(next).map(PsiElement::getText).joining();
    }
    return element.getText();
  }

  /**
   * Returns a string containing all the comments (possibly with some white-spaces) between given elements
   * (not including given elements themselves). This method also deletes all the comments actually used
   * in the returned string.
   *
   * @param start start element
   * @param end   end element, must strictly follow the start element and be located in the same file
   *              (though possibly on another hierarchy level)
   * @return a string containing all the comments between start and end.
   */
  @NotNull
  public static String commentsBetween(@NotNull PsiElement start, @NotNull PsiElement end) {
    PsiElement parent = PsiTreeUtil.findCommonParent(start, end);
    if (parent == null) {
      throw new IllegalStateException("Common parent is not found: [" + start + ".." + end + "]");
    }
    PsiElement cur = next(start, parent);
    List<PsiComment> comments = new ArrayList<>();
    while (cur != null && !PsiTreeUtil.isAncestor(cur, end, false)) {
      comments.addAll(PsiTreeUtil.findChildrenOfType(cur, PsiComment.class));
      if (cur instanceof PsiComment) {
        comments.add((PsiComment)cur);
      }
      cur = next(cur, parent);
    }
    if (cur == null) {
      throw new IllegalStateException("End is not reached: [" + start + ".." + end + "]");
    }
    PsiElement tail = prev(end, cur);
    Deque<PsiComment> tailComments = new ArrayDeque<>();
    while (tail != null) {
      PsiTreeUtil.findChildrenOfType(tail, PsiComment.class).forEach(tailComments::addFirst);
      if (cur instanceof PsiComment) {
        comments.add((PsiComment)cur);
      }
      tail = prev(tail, cur);
    }
    comments.addAll(tailComments);
    StringBuilder sb = new StringBuilder();
    for (PsiComment comment : comments) {
      PsiElement prev = prev(comment, parent);
      if (prev instanceof PsiWhiteSpace) {
        sb.append(prev.getText());
      }
      sb.append(comment.getText());
      PsiElement next = next(comment, parent);
      if (next instanceof PsiWhiteSpace) {
        sb.append(next.getText());
      }
      comment.delete();
    }
    return sb.toString();
  }

  private static PsiElement next(PsiElement cur, PsiElement stopAtParent) {
    if (cur == stopAtParent) return null;
    PsiElement next = cur.getNextSibling();
    if (next != null) return next;
    PsiElement parent = cur.getParent();
    if (parent == stopAtParent) return null;
    return next(parent, stopAtParent);
  }

  private static PsiElement prev(PsiElement cur, PsiElement stopAtParent) {
    if (cur == stopAtParent) return null;
    PsiElement prev = cur.getPrevSibling();
    if (prev != null) return prev;
    PsiElement parent = cur.getParent();
    if (parent == stopAtParent || parent == null) return null;
    return prev(parent, stopAtParent);
  }
}
