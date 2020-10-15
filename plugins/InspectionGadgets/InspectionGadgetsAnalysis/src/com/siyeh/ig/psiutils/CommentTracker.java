// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A helper class for implementing quick-fixes. CommentTracker collects removed comments from the PSI and can restore them at once.
 *
 * After this object restores comments, it becomes unusable.
 *
 * @author Tagir Valeev
 */
public final class CommentTracker {
  private final Set<PsiElement> ignoredParents = new HashSet<>();
  private List<PsiComment> comments = new ArrayList<>();
  private PsiElement lastTextWithCommentsElement = null;

  /**
   * Marks the element as unchanged and returns its text. Unchanged elements are assumed to be preserved
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
   * Marks the expression as unchanged and returns its text, adding parentheses if necessary.
   * Unchanged elements are assumed to be preserved in the resulting code as is,
   * so the comments from them will not be extracted.
   *
   * @param element    expression to return the text
   * @param precedence precedence of surrounding operation
   * @return a text to be inserted into refactored code
   * @see ParenthesesUtils#getText(PsiExpression, int)
   */
  public @NotNull String text(@NotNull PsiExpression element, int precedence) {
    checkState();
    addIgnored(element);
    return ParenthesesUtils.getText(element, precedence + 1);
  }

  /**
   * Marks the expression as unchanged and returns a single-parameter lambda text which parameter
   * is the name of supplied variable and body is the supplied expression
   *
   * @param variable   a variable to use as lambda parameter
   * @param expression an expression to use as lambda body
   * @return a string representation of the created lambda
   */
  public @NotNull String lambdaText(@NotNull PsiVariable variable, @NotNull PsiExpression expression) {
    return variable.getName() + " -> " + text(expression);
  }

  /**
   * Marks the element as unchanged and returns it. Unchanged elements are assumed to be preserved
   * in the resulting code as is, so the comments from them will not be extracted.
   *
   * @param element element to mark
   * @param <T>     the type of the element
   * @return the passed argument
   */
  @Contract("_ -> param1")
  public <T extends PsiElement> T markUnchanged(@Nullable T element) {
    checkState();
    if (element != null) addIgnored(element);
    return element;
  }

  /**
   * Marks the range of elements as unchanged and returns their text. Unchanged elements are assumed to be preserved
   * in the resulting code as is, so the comments from them will not be extracted.
   *
   * @param firstElement first element to mark
   * @param lastElement last element to mark (must be equal to firstElement or its sibling)
   * @return a text to be inserted into refactored code
   * @throws IllegalArgumentException if firstElement and lastElements are not siblings or firstElement goes after last element
   */
  public String rangeText(@NotNull PsiElement firstElement, @NotNull PsiElement lastElement) {
    checkState();
    PsiElement e;
    StringBuilder result = new StringBuilder();
    for (e = firstElement; e != null && e != lastElement; e = e.getNextSibling()) {
      addIgnored(e);
      result.append(e.getText());
    }
    if (e == null) {
      throw new IllegalArgumentException("Elements must be siblings: " + firstElement + " and " + lastElement);
    }
    addIgnored(lastElement);
    result.append(lastElement.getText());
    return result.toString();
  }

  /**
   * Marks the range of elements as unchanged. Unchanged elements are assumed to be preserved
   * in the resulting code as is, so the comments from them will not be extracted.
   *
   * @param firstElement first element to mark
   * @param lastElement last element to mark (must be equal to firstElement or its sibling)
   * @throws IllegalArgumentException if firstElement and lastElements are not siblings or firstElement goes after last element
   */
  public void markRangeUnchanged(@NotNull PsiElement firstElement, @NotNull PsiElement lastElement) {
    checkState();
    PsiElement e;
    for (e = firstElement; e != null && e != lastElement; e = e.getNextSibling()) {
      addIgnored(e);
    }
    if (e == null) {
      throw new IllegalArgumentException("Elements must be siblings: " + firstElement + " and " + lastElement);
    }
    addIgnored(lastElement);
  }

  /**
   * Returns the comments which are located between the supplied element
   * and the previous element passed into {@link #textWithComments(PsiElement)} or {@link #commentsBefore(PsiElement)}.
   * The used comments are deleted from the original document.
   *
   * <p>This method can be used if several parts of original code are reused in the generated replacement.
   *
   * @param element an element grab the comments before it
   * @return the string containing the element text and possibly some comments.
   */
  public String commentsBefore(@NotNull PsiElement element) {
    List<PsiElement> comments = grabCommentsBefore(element);
    if (comments.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (PsiElement comment : comments) {
      PsiElement prev = comment.getPrevSibling();
      if (sb.length() == 0 && prev instanceof PsiWhiteSpace) {
        sb.append(prev.getText());
      }
      sb.append(comment.getText());
      PsiElement next = PsiTreeUtil.nextLeaf(comment);
      if (next instanceof PsiWhiteSpace) {
        sb.append(next.getText());
      }
    }
    comments.forEach(PsiElement::delete);
    return sb.toString();
  }

  private List<PsiElement> grabCommentsBefore(@NotNull PsiElement element) {
    if (lastTextWithCommentsElement == null) {
      lastTextWithCommentsElement = element;
      return Collections.emptyList();
    }
    List<PsiElement> result = new SmartList<>();
    int start = lastTextWithCommentsElement.getTextRange().getEndOffset();
    int end = element.getTextRange().getStartOffset();
    PsiElement parent = PsiTreeUtil.findCommonParent(lastTextWithCommentsElement, element);
    if (parent != null && start < end) {
      PsiTreeUtil.processElements(parent, e -> {
        if (e instanceof PsiComment) {
          TextRange range = e.getTextRange();
          if (range.getStartOffset() >= start && range.getEndOffset() <= end && !shouldIgnore((PsiComment)e)) {
            result.add(e);
          }
        }
        return true;
      });
    }

    lastTextWithCommentsElement = element;
    return result;
  }

  /**
   * Returns the text of the specified element, possibly prepended with comments which are located between the supplied element
   * and the preceding element passed into {@link #textWithComments(PsiElement)} or {@link #commentsBefore(PsiElement)}.
   * The used comments are deleted from the original document.
   *
   * <p>Note that if PsiExpression was passed, the resulting text may not parse as an PsiExpression,
   * because PsiExpression cannot start with comment.
   *
   * <p>This method can be used if several parts of original code are reused in the generated replacement.
   *
   * @param element the element to convert to text
   * @return a string containing the element text and possibly some comments.
   */
  public String textWithComments(@NotNull PsiElement element) {
    return commentsBefore(element)+text(element);
  }

  /**
   * Returns an element text, adding parentheses if necessary, possibly prepended with comments which are
   * located between the supplied element and the previous element passed into
   * {@link #textWithComments(PsiElement)} or {@link #commentsBefore(PsiElement)}.
   * The used comments are deleted from the original document.
   *
   * <p>Note that if PsiExpression was passed, the resulting text may not parse as an PsiExpression,
   * because PsiExpression cannot start with comment.
   *
   * <p>This method can be used if several parts of original code are reused in the generated replacement.
   *
   * @param expression an expression to convert to text
   * @param precedence precedence of surrounding operation
   * @return a string containing the element text and possibly some comments.
   */
  public String textWithComments(@NotNull PsiExpression expression, int precedence) {
    return commentsBefore(expression)+ParenthesesUtils.getText(expression, precedence + 1);
  }

  /**
   * Deletes the given PsiElement collecting all comments inside it.
   *
   * @param element an element to delete
   */
  public void delete(@NotNull PsiElement element) {
    grabCommentsOnDelete(element);
    element.delete();
  }

  /**
   * Deletes all given PsiElements collecting all comments inside them.
   *
   * @param elements elements to delete (all not null)
   */
  public void delete(PsiElement @NotNull ... elements) {
    for (PsiElement element : elements) {
      delete(element);
    }
  }

  /**
   * Deletes the given PsiElement replacing it with the comments, including comments inside the deleted element
   * and previously gathered comments.
   *
   * <p>After calling this method the tracker cannot be used anymore.</p>
   *
   * @param element element to delete
   */
  public void deleteAndRestoreComments(@NotNull PsiElement element) {
    grabCommentsOnDelete(element);
    PsiElement anchor = element;
    while (anchor.getParent() != null && !(anchor.getParent() instanceof PsiFile) && anchor.getParent().getFirstChild() == anchor) {
      anchor = anchor.getParent();
    }
    insertCommentsBefore(anchor);
    element.delete();
  }

  /**
   * Replaces the given PsiElement collecting all comments inside it. In the case that the replacement is a {@link PsiPolyadicExpression}
   * and the parent of the element to replace is also a PsiPolyadicExpression, with the same operator and type, a combined flattened
   * PsiPolyadicExpression is created from both expressions and inserted instead of the parent of the element to replace.
   *
   * @param element     element to replace
   * @param replacement replacement element. It's also marked as unchanged (see {@link #markUnchanged(PsiElement)})
   * @return the element which was actually inserted in the tree (either {@code replacement}, its copy or a newly created polyadic expression)
   */
  public @NotNull PsiElement replace(@NotNull PsiElement element, @NotNull PsiElement replacement) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiPolyadicExpression && replacement instanceof PsiPolyadicExpression) {
      // flatten nested polyadic expressions
      PsiPolyadicExpression parentPolyadic = (PsiPolyadicExpression)parent;
      PsiPolyadicExpression childPolyadic = (PsiPolyadicExpression)replacement;
      IElementType parentTokenType = parentPolyadic.getOperationTokenType();
      IElementType childTokenType = childPolyadic.getOperationTokenType();
      if (PsiPrecedenceUtil.getPrecedenceForOperator(parentTokenType) == PsiPrecedenceUtil.getPrecedenceForOperator(childTokenType) &&
          !PsiPrecedenceUtil.areParenthesesNeeded(childPolyadic, parentPolyadic, false)) {
        PsiElement[] children = parentPolyadic.getChildren();
        int idx = ArrayUtil.indexOf(children, element);
        if (idx > 0 || (idx == 0 && parentTokenType == childTokenType)) {
          StringBuilder text = new StringBuilder();
          for (int i = 0; i < children.length; i++) {
            PsiElement child = children[i];
            text.append(text((i == idx) ? replacement : child));
          }
          replacement = JavaPsiFacade.getElementFactory(parent.getProject()).createExpressionFromText(text.toString(), parent);
          element = parent;
        }
      }
    }
    markUnchanged(replacement);
    grabComments(element);
    return element.replace(replacement);
  }

  /**
   * Creates a replacement element from the text and replaces the given PsiElement collecting all comments inside it. When
   * the replacement parses to a {@link PsiPolyadicExpression} and the parent of the element to replace is also a
   * PsiPolyadicExpression, with the same operator and type, a combined flattened
   * PsiPolyadicExpression is created from both expressions and inserted instead of the parent of the element to replace.
   *
   * <p>
   * The type of the created replacement will mimic the type of supplied element.
   * Supported element types are: {@link PsiExpression}, {@link PsiStatement},
   * {@link PsiTypeElement}, {@link PsiIdentifier}, {@link PsiComment}.
   * </p>
   *
   * @param element element to replace
   * @param text    replacement text
   * @return the element which was actually inserted in the tree
   */
  public @NotNull PsiElement replace(@NotNull PsiElement element, @NotNull @NlsSafe String text) {
    PsiElement replacement = createElementFromText(text, element);
    return replace(element, replacement);
  }

  /**
   * Replaces given PsiElement collecting all the comments inside it and restores comments putting them
   * to the appropriate place before replaced element. See also the javadoc of {@link #replace(PsiElement, PsiElement)}
   * In the case that the replacement is a {@link PsiPolyadicExpression} and the parent of the element to replace is also
   * a PsiPolyadicExpression, with the same operator and type, a combined flattened PsiPolyadicExpression is created
   * from both expressions and inserted instead of the parent of the element to replace.
   *
   * <p>After calling this method the tracker cannot be used anymore.</p>
   *
   * @param element     element to replace
   * @param replacement replacement element. It's also marked as unchanged (see {@link #markUnchanged(PsiElement)})
   * @return the element which was actually inserted in the tree (either {@code replacement} or its copy)
   */
  public @NotNull PsiElement replaceAndRestoreComments(@NotNull PsiElement element, @NotNull PsiElement replacement) {
    List<PsiElement> suffix = grabSuffixComments(element);
    PsiElement result = replace(element, replacement);
    PsiElement anchor = findAnchor(result);
    restoreSuffixComments(result, suffix);
    insertCommentsBefore(anchor);
    return result;
  }

  private static @NotNull PsiElement findAnchor(@NotNull PsiElement result) {
    PsiElement anchor = PsiTreeUtil
      .getNonStrictParentOfType(result, PsiStatement.class, PsiLambdaExpression.class, PsiVariable.class, PsiNameValuePair.class);
    if (anchor instanceof PsiLambdaExpression && anchor != result) {
      anchor = ((PsiLambdaExpression)anchor).getBody();
    }
    if (anchor instanceof PsiVariable && anchor.getParent() instanceof PsiDeclarationStatement) {
      anchor = anchor.getParent();
    }
    if (anchor instanceof PsiStatement && (anchor.getParent() instanceof PsiIfStatement || anchor.getParent() instanceof PsiLoopStatement)) {
      anchor = anchor.getParent();
    }
    return anchor == null ? result : anchor;
  }

  /**
   * Replaces the specified expression and restores any comments to their appropriate place before and/or after the expression.
   * Meant to be used with {@link #commentsBefore(PsiElement)} and {@link #commentsBetween(PsiElement, PsiElement)}.
   * When the replacement parses to a {@link PsiPolyadicExpression} and the parent of the element to replace is also a
   * PsiPolyadicExpression, with the same operator and type, a combined flattened
   * PsiPolyadicExpression is created from both expressions and inserted instead of the parent of the element to replace
   *
   * @param expression  the expression to replace
   * @param replacementText  text of the replacement expression
   * @return the element which was inserted in the tree
   */
  public @NotNull PsiElement replaceExpressionAndRestoreComments(@NotNull PsiExpression expression, @NotNull String replacementText) {
    return replaceExpressionAndRestoreComments(expression, replacementText, Collections.emptyList());
  }

  /**
   * Replaces the specified expression and restores any comments to their appropriate place before and/or after the expression.
   * Meant to be used with {@link #commentsBefore(PsiElement)} and {@link #commentsBetween(PsiElement, PsiElement)}.
   * When the replacement parses to a {@link PsiPolyadicExpression} and the parent of the element to replace is also a
   * PsiPolyadicExpression, with the same operator and type, a combined flattened
   * PsiPolyadicExpression is created from both expressions and inserted instead of the parent of the element to replace
   *
   * @param expression  the expression to replace
   * @param replacementText  text of the replacement expression
   * @param toDelete  elements to delete, comments inside will be collected
   * @return the element which was inserted in the tree
   */
  public @NotNull PsiElement replaceExpressionAndRestoreComments(@NotNull PsiExpression expression, @NotNull String replacementText,
                                                                 List<? extends PsiElement> toDelete) {
    List<PsiElement> trailingComments = new SmartList<>();
    List<PsiElement> comments = grabCommentsBefore(PsiTreeUtil.lastChild(expression));
    if (!comments.isEmpty()) {
      PsiParserFacade parser = PsiParserFacade.SERVICE.getInstance(expression.getProject());
      for (PsiElement comment : comments) {
        PsiElement prev = comment.getPrevSibling();
        if (prev instanceof PsiWhiteSpace) {
          String text = prev.getText();
          if (!text.contains("\n")) trailingComments.add(parser.createWhiteSpaceFromText(" "));
          else if (text.endsWith("\n")) trailingComments.add(parser.createWhiteSpaceFromText("\n")); // comment at first column
          else trailingComments.add(parser.createWhiteSpaceFromText("\n ")); // newline followed by space will cause formatter to indent
        }
        ignoredParents.add(comment);
        trailingComments.add(comment.copy());
      }
      Collections.reverse(trailingComments);
    }
    PsiElement replacement = replace(expression, replacementText);
    for (PsiElement element : trailingComments) {
      replacement.getParent().addAfter(element, replacement);
    }
    toDelete.forEach(this::delete);
    PsiElement anchor = replacement;
    while (anchor.getParent() != null && anchor.getPrevSibling() == null) {
      anchor = anchor.getParent();
    }
    insertCommentsBefore(anchor);
    return replacement;
  }

  @NotNull
  private List<PsiElement> grabSuffixComments(@NotNull PsiElement element) {
    if (!(element instanceof PsiStatement)) {
      return Collections.emptyList();
    }
    List<PsiElement> suffix = new ArrayList<>();
    PsiElement lastChild = element.getLastChild();
    boolean hasComment = false;
    while (lastChild instanceof PsiComment || lastChild instanceof PsiWhiteSpace) {
      hasComment |= lastChild instanceof PsiComment;
      if (!(lastChild instanceof PsiComment) || !(shouldIgnore((PsiComment)lastChild))) {
        suffix.add(markUnchanged(lastChild).copy());
      }
      lastChild = lastChild.getPrevSibling();
    }
    return hasComment ? suffix : Collections.emptyList();
  }

  private static void restoreSuffixComments(PsiElement target, List<? extends PsiElement> suffix) {
    if (!suffix.isEmpty()) {
      PsiElement lastChild = target.getLastChild();
      if (lastChild instanceof PsiComment && JavaTokenType.END_OF_LINE_COMMENT.equals(((PsiComment)lastChild).getTokenType())) {
        PsiElement nextSibling = target.getNextSibling();
        if (nextSibling instanceof PsiWhiteSpace) {
          target.add(nextSibling);
        } else {
          target.add(PsiParserFacade.SERVICE.getInstance(target.getProject()).createWhiteSpaceFromText("\n"));
        }
      }
      StreamEx.ofReversed(suffix).forEach(target::add);
    }
  }

  /**
   * Creates a replacement element from the text and replaces given element,
   * collecting all the comments inside it and restores comments putting them
   * to the appropriate place before replaced element.
   * In the case that the replacement parses to a {@link PsiPolyadicExpression}
   * and the parent of the element to replace is also a PsiPolyadicExpression,
   * with the same operator and type, a combined flattened
   * PsiPolyadicExpression is created from both expressions and inserted
   * instead of the parent of the element to replace.
   *
   * <p>After calling this method the tracker cannot be used anymore.</p>
   *
   * <p>
   * The type of the created replacement will mimic the type of supplied element.
   * Supported element types are: {@link PsiExpression}, {@link PsiStatement},
   * {@link PsiTypeElement}, {@link PsiIdentifier}, {@link PsiComment}.
   * </p>
   *
   * @param element element to replace
   * @param text    replacement text
   * @return the element which was actually inserted in the tree
   */
  public @NotNull PsiElement replaceAndRestoreComments(@NotNull PsiElement element, @NotNull @NlsSafe String text) {
    PsiElement replacement = createElementFromText(text, element);
    return replaceAndRestoreComments(element, replacement);
  }

  private static @NotNull PsiElement createElementFromText(@NotNull String text, @NotNull PsiElement context) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    if (context instanceof PsiExpression) {
      return factory.createExpressionFromText(text, context);
    }
    else if (context instanceof PsiStatement) {
      return factory.createStatementFromText(text, context);
    }
    else if (context instanceof PsiTypeElement) {
      return factory.createTypeElementFromText(text, context);
    }
    else if (context instanceof PsiIdentifier) {
      return factory.createIdentifier(text);
    }
    else if (context instanceof PsiComment) {
      return factory.createCommentFromText(text, context);
    }
    else {
      throw new IllegalArgumentException("Unsupported element type: " + context);
    }
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
    if (!comments.isEmpty()) {
      PsiElement parent = anchor.getParent();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(anchor.getProject());
      for (PsiComment comment : comments) {
        if (shouldIgnore(comment)) continue;
        PsiElement added = parent.addBefore(factory.createCommentFromText(comment.getText(), anchor), anchor);
        PsiElement prevSibling = added.getPrevSibling();
        if (prevSibling instanceof PsiWhiteSpace) {
          PsiElement prev = anchor.getPrevSibling();
          ASTNode whiteSpaceBefore = normalizeWhiteSpace((PsiWhiteSpace)prevSibling, prev);
          parent.getNode().addChild(whiteSpaceBefore, anchor.getNode());
          if (prev instanceof PsiWhiteSpace) {
            prev.delete();
          }
        }
      }
    }
    comments = null;
  }

  private static @NotNull ASTNode normalizeWhiteSpace(PsiWhiteSpace whiteSpace, PsiElement nextElement) {
    String text = whiteSpace.getText();
    int endLPos = text.lastIndexOf('\n');
    if (text.lastIndexOf('\n', endLPos - 1) >= 0) {
      // has at least two line breaks
      return ASTFactory.whitespace(text.substring(endLPos));
    }
    if (nextElement instanceof PsiWhiteSpace && nextElement.getText().contains("\n") && !text.contains("\n")) {
      text = '\n' + text;
    }
    return ASTFactory.whitespace(text);
  }

  private boolean shouldIgnore(PsiComment comment) {
    return ignoredParents.stream().anyMatch(p -> PsiTreeUtil.isAncestor(p, comment, false));
  }

  private void grabCommentsOnDelete(PsiElement element) {
    if (element instanceof PsiExpression && element.getParent() instanceof PsiExpressionStatement ||
        (element.getParent() instanceof PsiDeclarationStatement &&
         ((PsiDeclarationStatement)element.getParent()).getDeclaredElements().length == 1)) {
      element = element.getParent();
    }
    else if (element.getParent() instanceof PsiJavaCodeReferenceElement) {
      PsiElement parent = element.getParent();
      if (element instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)parent).getQualifier() == element) {
        ASTNode dot = ((CompositeElement)parent).findChildByRole(ChildRole.DOT);
        if (dot != null) {
          PsiElement nextSibling = dot.getPsi().getNextSibling();
          if (nextSibling != null && nextSibling.getTextLength() == 0) {
            nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(nextSibling);
          }
          while (nextSibling != null) {
            nextSibling = markUnchanged(nextSibling).getNextSibling();
          }
        }
      }
      element = parent;
    }
    grabComments(element);
  }

  /**
   * Grab the comments from given element which should be restored. Normally you don't need to call this method.
   * It should be called only if element is about to be deleted by other code which is not CommentTracker-aware.
   *
   * <p>Calling this method repeatedly has no effect. It's also safe to call this method, then delete element using
   * other methods from this class like {@link #delete(PsiElement)}.
   *
   * @param element element to grab the comments from.
   */
  public void grabComments(PsiElement element) {
    checkState();
    for (PsiComment comment : PsiTreeUtil.collectElementsOfType(element, PsiComment.class)) {
      if (!shouldIgnore(comment)) {
        comments.add(comment);
      }
    }
  }

  private void checkState() {
    if (comments == null) {
      throw new IllegalStateException(getClass().getSimpleName() + " has been already used");
    }
  }

  private void addIgnored(PsiElement element) {
    if (!(element instanceof LeafPsiElement) || element instanceof PsiComment) {
      ignoredParents.add(element);
    }
  }

  public static String textWithSurroundingComments(PsiElement element) {
    Predicate<PsiElement> commentOrWhiteSpace = e -> e instanceof PsiComment || e instanceof PsiWhiteSpace;
    List<PsiElement> prev = StreamEx.iterate(element.getPrevSibling(), commentOrWhiteSpace, PsiElement::getPrevSibling).toList();
    List<PsiElement> next = StreamEx.iterate(element.getNextSibling(), commentOrWhiteSpace, PsiElement::getNextSibling).toList();
    if (StreamEx.of(prev, next).flatCollection(Function.identity()).anyMatch(PsiComment.class::isInstance)) {
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
  public static @NotNull String commentsBetween(@NotNull PsiElement start, @NotNull PsiElement end) {
    CommentTracker ct = new CommentTracker();
    ct.lastTextWithCommentsElement = start;
    return ct.commentsBefore(end);
  }
}