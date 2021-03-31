// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.comment;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts C-style block comments or end-of-line comments that belong to an element of the {@link PsiMember} type to javadoc.
 */
public class ReplaceWithJavadocIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    ProgressManager.checkCanceled();

    if (!(element instanceof PsiComment)) return;
    final PsiComment comment = (PsiComment)element;

    // a set will contain nodes to remove: all the right and left sibling nodes of the PsiComment type
    final Set<PsiComment> toRemove = new HashSet<>();

    // a queue of lines obtained from the content of all the left, current and right comment nodes
    final Deque<String> commentContent = new ArrayDeque<>(getCommentTextLines(comment));

    final List<String> leftSiblingsComments = siblingsComments(comment,
                                                               toRemove,
                                                               e -> PsiTreeUtil.getPrevSiblingOfType(e, PsiComment.class));
    leftSiblingsComments.forEach(commentContent::addFirst);

    final List<String> rightSiblingsComments = siblingsComments(comment,
                                                               toRemove,
                                                               e -> PsiTreeUtil.getNextSiblingOfType(e, PsiComment.class));
    rightSiblingsComments.forEach(commentContent::addLast);


    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(element.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();

    final String javadoc = prepareJavadocComment(commentContent);
    final PsiDocComment docComment = factory.createDocCommentFromText(javadoc);

    ProgressManager.checkCanceled();

    // remove all the left and right comment nodes if any
    toRemove.forEach(PsiElement::delete);

    // replace the current node with the new one
    element.replace(docComment);
  }

  /**
   * Extracts the content of the comment nodes that are either left or right siblings of the comment.
   *
   * @param comment a comment to get siblings for
   * @param visited a set of visited comment nodes
   * @param siblingExtractor a function to extract siblings. Either left or right siblings.
   * @return the list of strings which consists of the content of the comment nodes that are either left or right siblings.
   */
  private static @NotNull List<@NotNull String> siblingsComments(@NotNull PsiComment comment,
                                                                 @NotNull Set<? super @NotNull PsiComment> visited,
                                                                 @NotNull Function<@NotNull PsiComment, @Nullable PsiComment> siblingExtractor) {
    final List<String> result = new ArrayList<>();

    PsiComment commentNode = siblingExtractor.apply(comment);
    while (commentNode != null) {
      ProgressManager.checkCanceled();

      visited.add(commentNode);
      result.addAll(getCommentTextLines(commentNode));

      commentNode = siblingExtractor.apply(commentNode);
    }
    return result;
  }

  /**
   * Combines the collection of strings into a string in the javadoc format:
   * <ul>
   *   <li>The resulting string starts with <code>/**</code></li>
   *   <li>Each line from the collection is precluded with '<code>*</code>'</li>
   *   <li>The resulting string ends with <code>*&#47;</code></li>
   * </ul>
   * @param commentContent the collection of strings to form the javadoc's content
   * @return the string in the javadoc format.
   */
  @Contract(pure = true)
  private static @NotNull String prepareJavadocComment(final @NotNull Collection<String> commentContent) {
    final StringBuilder sb = new StringBuilder("/**\n");

    for (String string : commentContent) {
      ProgressManager.checkCanceled();

      final String line = string.trim();
      if (line.isEmpty()) continue;
      sb.append("* ");
      sb.append(line);
      sb.append("\n");
    }
    sb.append("*/");

    return sb.toString();
  }

  /**
   * Extracts the content of a comment as a list of strings.
   *
   * @param comment {@link PsiComment} to examine
   * @return the content of a comment as a list of strings where each line is an element of the list.
   */
  @Contract(pure = true)
  private static List<String> getCommentTextLines(@NotNull PsiComment comment) {
    final Stream<String> lines;
    if (comment instanceof PsiDocComment) {
      final PsiDocComment docComment = (PsiDocComment)comment;

      lines = Arrays.stream(docComment.getDescriptionElements())
        .map(PsiElement::getText);
    }
    else {
      lines = Arrays.stream(extractLines(comment));
    }
    return lines
      .map(String::trim)
      .filter(Predicate.not(String::isEmpty))
      .map(line -> line.startsWith("*") ? line.substring(1) : line)
      .map(line -> StringUtil.replace(line, "*/", "*&#47;"))
      .collect(Collectors.toList())
    ;
  }

  /**
   * Extracts the content from either a C-style block comment or an end-of-line comment as an array of lines
   *
   * @param comment {@link PsiComment} to examine
   * @return the content of a comment as an array of lines
   */
  @Contract(pure = true)
  private static String @NotNull[] extractLines(@NotNull PsiComment comment) {
    assert !(comment instanceof PsiDocComment) : "The method can't be called for a javadoc comment.";

    final String commentText = comment.getText();
    if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
      return new String[] { commentText.substring("//".length()) };
    }

    final int start = "/*".length();
    final int end = commentText.length() - "*/".length();

    final String content = commentText.substring(start, end);
    return content.split("\n");
  }

  /**
   * Returns a predicate that returns true when a {@link PsiElement} is of the {@link PsiComment}(excluding {@link PsiDocComment}) type
   * which belong to a {@link PsiMember} type to convert it to javadoc.
   *
   * @return predicate to check if a comment can be converted to a javadoc.
   */
  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return element -> {
      ProgressManager.checkCanceled();

      if (!(element instanceof PsiComment)) return false;
      if (element instanceof PsiDocComment) return false;

      final PsiComment comment = (PsiComment)element;

      return comment.getParent() instanceof PsiMember;
    };
  }
}