// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.filters.DefaultFilter;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;

import java.util.HashSet;
import java.util.Set;

/**
 * Root of handlers for pattern node matching. Handles simplest type of the match.
 */
public abstract class MatchingHandler {
  protected NodeFilter filter;
  private PsiElement pinnedElement;

  public void setFilter(NodeFilter filter) {
    this.filter = filter;
  }

  /**
   * Matches given handler node against given value.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successful and false otherwise
   */
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return (patternNode == null) ? matchedNode == null : canMatch(patternNode, matchedNode, context);
  }

  public boolean canMatch(final PsiElement patternNode, final PsiElement matchedNode, MatchContext context) {
    return (filter != null) ? filter.accepts(matchedNode) : DefaultFilter.accepts(patternNode, matchedNode);
  }

  public boolean matchSequentially(NodeIterator patternNodes, NodeIterator matchNodes, MatchContext context) {
    final MatchingStrategy strategy = context.getPattern().getStrategy();
    final PsiElement currentPatternNode = patternNodes.current();
    final PsiElement currentMatchNode = matchNodes.current();

    skipIfNecessary(matchNodes, currentPatternNode, strategy);
    skipComments(matchNodes, currentPatternNode);
    skipIfNecessary(patternNodes, matchNodes.current(), strategy);

    if (!patternNodes.hasNext()) {
      return !matchNodes.hasNext();
    }

    final PsiElement patternElement = patternNodes.current();
    final MatchingHandler handler = context.getPattern().getHandler(patternElement);
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

  private static void skipComments(NodeIterator matchNodes, PsiElement patternNode) {
    final boolean skipComment = !(patternNode instanceof PsiComment);
    while (skipComment && matchNodes.current() instanceof PsiComment) matchNodes.advance();
  }

  private static void skipIfNecessary(NodeIterator nodes, PsiElement elementToMatchWith, MatchingStrategy strategy) {
    while (strategy.shouldSkip(nodes.current(), elementToMatchWith)) {
      nodes.advance();
    }
  }

  protected boolean isMatchSequentiallySucceeded(final NodeIterator matchNodes) {
    skipComments(matchNodes, null);
    return !matchNodes.hasNext();
  }

  static class ClearStateVisitor extends PsiRecursiveElementWalkingVisitor {
    private CompiledPattern pattern;

    ClearStateVisitor() {
      super(true);
    }

    @Override public void visitElement(PsiElement element) {
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

    synchronized void clearState(CompiledPattern _pattern, PsiElement el) {
      pattern = _pattern;
      el.acceptChildren(this);
      pattern = null;
    }
  }

  protected static ClearStateVisitor clearingVisitor = new ClearStateVisitor();

  public boolean matchInAnyOrder(NodeIterator patternNodes, NodeIterator matchedNodes, final MatchContext context) {
    final MatchResultImpl saveResult = context.hasResult() ? context.getResult() : null;
    context.setResult(null);

    try {
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
    } finally {
      if (saveResult != null) {
        if (context.hasResult()) {
          for (MatchResult child : context.getResult().getChildren()) {
            saveResult.addChild(child);
          }
        }
        context.setResult(saveResult);
      }
    }
  }

  protected static boolean validateSatisfactionOfHandlers(NodeIterator patternNodes, MatchContext context) {
    for (;patternNodes.hasNext(); patternNodes.advance()) {
      if (!context.getPattern().getHandler(patternNodes.current()).validate(context, 0)) {
        return false;
      }
    }
    return true;
  }

  boolean validate(MatchContext context, int matchedOccurs) {
    return matchedOccurs == 1;
  }

  public NodeFilter getFilter() {
    return filter;
  }

  public boolean shouldAdvanceThePatternFor(PsiElement patternElement, PsiElement matchedElement) {
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

  public void setPinnedElement(final PsiElement pinnedElement) {
    this.pinnedElement = pinnedElement;
  }
}