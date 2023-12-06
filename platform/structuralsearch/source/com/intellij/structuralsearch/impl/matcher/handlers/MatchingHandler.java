// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.filters.DefaultFilter;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Root of handlers for pattern node matching. Matching handlers know how to match a specific pattern node
 * to a node in the source code.
 */
public abstract class MatchingHandler {
  protected NodeFilter filter;
  private PsiElement pinnedElement;

  /**
   * Node filters determine which kind of PsiElements can match the pattern element.
   * Filters are applied to MatchingHandlers in the CompilingVisitor.
   */
  public void setFilter(@Nullable NodeFilter filter) {
    this.filter = filter;
  }

  /**
   * Matches given handler node against given value.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successful and false otherwise
   */
  public boolean match(PsiElement patternNode, PsiElement matchedNode, @NotNull MatchContext context) {
    return (patternNode == null) ? matchedNode == null : canMatch(patternNode, matchedNode, context);
  }

  public boolean canMatch(@NotNull PsiElement patternNode, final PsiElement matchedNode, @NotNull MatchContext context) {
    return (filter != null) ? filter.accepts(matchedNode) : DefaultFilter.accepts(patternNode, matchedNode);
  }

  public boolean matchSequentially(@NotNull NodeIterator patternNodes, @NotNull NodeIterator matchNodes, @NotNull MatchContext context) {
    final MatchingStrategy strategy = context.getPattern().getStrategy();
    final PsiElement currentPatternNode = patternNodes.current();
    final PsiElement currentMatchNode = matchNodes.current();

    skipIfNecessary(matchNodes, currentPatternNode, strategy);
    skipIfNecessary(patternNodes, matchNodes.current(), strategy);

    if (!patternNodes.hasNext()) {
      return !matchNodes.hasNext();
    }

    final PsiElement patternElement = patternNodes.current();
    final MatchingHandler handler = context.getPattern().getHandler(patternElement);
    if (!(handler instanceof TopLevelMatchingHandler)) skipComments(matchNodes, currentPatternNode);
    if (matchNodes.hasNext() && handler.match(patternElement, matchNodes.current(), context)) {

      patternNodes.advance();
      skipIfNecessary(patternNodes, matchNodes.current(), strategy);
      if (shouldAdvanceTheMatchFor(patternElement, matchNodes.current())) {
        matchNodes.advance();
        skipIfNecessary(matchNodes, patternNodes.current(), strategy);
        if (patternNodes.hasNext()) skipComments(matchNodes, patternNodes.current());
      }

      if (patternNodes.hasNext()) {
        final MatchingHandler nextHandler = context.getPattern().getHandler(patternNodes.current());
        if (nextHandler.matchSequentially(patternNodes, matchNodes, context)) {
          return true;
        } else {
          patternNodes.rewindTo(currentPatternNode);
          matchNodes.rewindTo(currentMatchNode);
        }
      } else {
        // match was found
        return handler.isMatchSequentiallySucceeded(matchNodes);
      }
    }
    return false;
  }

  private static void skipComments(@NotNull NodeIterator matchNodes, PsiElement patternNode) {
    if (patternNode instanceof PsiComment) return;
    while (matchNodes.current() instanceof PsiComment) matchNodes.advance();
  }

  private static void skipIfNecessary(@NotNull NodeIterator nodes, PsiElement elementToMatchWith, @NotNull MatchingStrategy strategy) {
    while (nodes.hasNext() && strategy.shouldSkip(nodes.current(), elementToMatchWith)) {
      nodes.advance();
    }
  }

  protected boolean isMatchSequentiallySucceeded(@NotNull NodeIterator matchNodes) {
    skipComments(matchNodes, null);
    return !matchNodes.hasNext();
  }

  static class ClearStateVisitor extends PsiRecursiveElementWalkingVisitor {
    private CompiledPattern pattern;

    ClearStateVisitor() {
      super(true);
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      // We do not reset certain handlers because they are also bound to higher level nodes
      // e.g. Identifier handler in name is also bound to PsiMethod
      if (pattern.isToResetHandler(element)) {
        final MatchingHandler handler = pattern.getHandlerSimple(element);
        if (handler != null) {
          handler.reset();
        }
      }
      super.visitElement(element);
    }

    synchronized void clearState(@NotNull CompiledPattern _pattern, @NotNull PsiElement el) {
      pattern = _pattern;
      el.acceptChildren(this);
      pattern = null;
    }
  }

  protected static ClearStateVisitor clearingVisitor = new ClearStateVisitor();

  public static boolean matchInAnyOrder(@NotNull NodeIterator patternNodes, @NotNull NodeIterator matchedNodes, @NotNull MatchContext context) {
    if (patternNodes.hasNext() && !matchedNodes.hasNext()) {
      return validateSatisfactionOfHandlers(patternNodes, context);
    }

    Set<PsiElement> matchedElements = null;
    while (patternNodes.hasNext()) {
      final PsiElement patternNode = patternNodes.current();
      patternNodes.advance();
      final CompiledPattern pattern = context.getPattern();
      final MatchingHandler handler = pattern.getHandler(patternNode);

      matchedNodes.reset();
      boolean allElementsMatched = true;
      int matchedOccurs = 0;
      do {
        final PsiElement pinnedNode = handler.getPinnedNode();
        final PsiElement matchedNode = (pinnedNode != null) ? pinnedNode : matchedNodes.current();
        if (pinnedNode == null) matchedNodes.advance();

        if (matchedElements == null || !matchedElements.contains(matchedNode)) {
          allElementsMatched = false;
          if (handler.match(patternNode, matchedNode, context)) {
            matchedOccurs++;
            if (matchedElements == null) matchedElements = new HashSet<>();
            matchedElements.add(matchedNode);
            if (handler.shouldAdvanceThePatternFor(patternNode, matchedNode)) {
              break;
            }
          } else if (pinnedNode != null) {
            return false;
          }

          // clear state of dependent objects
          clearingVisitor.clearState(pattern, patternNode);
        }

        if (!matchedNodes.hasNext() || pinnedNode != null) {
          if (!handler.validate(context, matchedOccurs)) return false;
          if (allElementsMatched || !patternNodes.hasNext()) {
            final boolean result = validateSatisfactionOfHandlers(patternNodes, context);
            if (result && matchedElements != null) {
              context.notifyMatchedElements(matchedElements);
            }
            return result;
          }
          break;
        }
      } while(true);

      if (!handler.validate(context, matchedOccurs)) return false;
    }

    final boolean result = validateSatisfactionOfHandlers(patternNodes, context);
    if (result && matchedElements != null) {
      context.notifyMatchedElements(matchedElements);
    }
    return result;
  }

  protected static boolean validateSatisfactionOfHandlers(@NotNull NodeIterator patternNodes, @NotNull MatchContext context) {
    for (; patternNodes.hasNext(); patternNodes.advance()) {
      if (!context.getPattern().getHandler(patternNodes.current()).validate(context, 0)) {
        return false;
      }
    }
    return true;
  }

  public boolean validate(@NotNull MatchContext context, int matchedOccurs) {
    return matchedOccurs == 1;
  }

  public NodeFilter getFilter() {
    return filter;
  }

  public boolean shouldAdvanceThePatternFor(@NotNull PsiElement patternElement, @NotNull PsiElement matchedElement) {
    return true;
  }

  public boolean shouldAdvanceTheMatchFor(PsiElement patternElement, PsiElement matchedElement) {
    return true;
  }

  public void reset() {
    //pinnedElement = null;
  }

  public PsiElement getPinnedNode() {
    return pinnedElement;
  }

  public void setPinnedElement(@NotNull PsiElement pinnedElement) {
    this.pinnedElement = pinnedElement;
  }
}