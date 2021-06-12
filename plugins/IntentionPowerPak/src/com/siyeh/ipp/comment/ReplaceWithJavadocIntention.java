// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.comment;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts C-style block comments or end-of-line comments that belong to an element of the {@link PsiMember} type to javadoc.
 */
public class ReplaceWithJavadocIntention extends Intention {
  private static final Logger LOGGER = Logger.getInstance(ReplaceWithJavadocIntention.class.getName());

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    if (!(element instanceof PsiComment)) return;

    final PsiElement method = element.getParent();
    if (method == null) return;

    final PsiElement child = method.getFirstChild();
    if (!(child instanceof PsiComment)) return;
    final PsiComment firstCommentOfMethod = (PsiComment)child;

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(element.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();

    // the set will contain all the comment nodes that are directly before the method's modifier list
    final Set<PsiComment> commentNodes = new HashSet<>();

    final String javadocText = prepareJavadocComment(firstCommentOfMethod, commentNodes);
    final PsiDocComment javadoc = factory.createDocCommentFromText(javadocText);

    if (commentNodes.isEmpty()) {
      LOGGER.error("The set of visited node comments is empty");
      return;
    }

    if (commentNodes.size() > 1) {
      // remove all the comment nodes except the first one
      commentNodes.stream().skip(1).forEach(PsiElement::delete);
    }

    final PsiComment item = ContainerUtil.getFirstItem(commentNodes);
    item.replace(javadoc);
  }

  /**
   * Extracts the content of the comment nodes that are right siblings to the comment node
   *
   * @param comment a comment node to get siblings for
   * @param visited a set of visited comment nodes
   * @return the list of strings which consists of the content of the comment nodes that are either left or right siblings.
   */
  @Contract(mutates = "param2")
  private static @NotNull List<@NotNull String> siblingsComments(@NotNull PsiComment comment,
                                                                 @NotNull Set<? super @NotNull PsiComment> visited) {
    final List<String> result = new ArrayList<>();
    PsiElement node = comment;
    do {
      if (node instanceof PsiComment) {
        final PsiComment commentNode = (PsiComment)node;

        visited.add(commentNode);
        result.addAll(getCommentTextLines(commentNode));
      }
      node = node.getNextSibling();
    }
    while (node != null && !(node instanceof PsiModifierList));

    return result;
  }

  /**
   * Combines the collection of strings into a string in the javadoc format:
   * <ul>
   *   <li>The resulting string starts with <code>/**</code></li>
   *   <li>Each line from the collection is precluded with '<code>*</code>'</li>
   *   <li>The resulting string ends with <code>*&#47;</code></li>
   * </ul>
   * @return the string in the javadoc format.
   */
  @Contract(mutates = "param2")
  private static @NotNull String prepareJavadocComment(PsiComment comment, @NotNull Set<@NotNull PsiComment> visited) {
    final @NotNull Collection<String> commentContent = siblingsComments(comment, visited);
    final StringBuilder sb = new StringBuilder("/**\n");

    for (String string : commentContent) {
      final String line = string.trim();
      if (line.isEmpty()) continue;
      sb.append("* ");
      sb.append(line);
      sb.append("\n");
    }

    if (comment instanceof PsiDocComment) {
      PsiDocComment javadoc = (PsiDocComment)comment;
      final PsiDocTag[] tags = javadoc.getTags();
      if (tags.length > 0) {
        final int start = tags[0].getStartOffsetInParent();
        final int end = tags[tags.length - 1].getTextRangeInParent().getEndOffset();
        sb.append("* ");
        sb.append(comment.getText(), start, end);
        sb.append("\n");
      }
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
      .collect(Collectors.toList());
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
      if (!(element instanceof PsiComment)) return false;
      if (element instanceof PsiDocComment) return false;

      final PsiComment comment = (PsiComment)element;

      final PsiElement parent = comment.getParent();
      if (!(parent instanceof PsiMember) || parent instanceof PsiAnonymousClass) return false;

      // the comment node might have a method as its parent,
      // but the comment itself can be defined before/after the modifier list/type/name/parameters of the method
      // Such comments are not candidates to be converted to javadoc
      final PsiElement type = PsiTreeUtil.getPrevSiblingOfType(comment, PsiModifierList.class);

      return type == null;
    };
  }
}