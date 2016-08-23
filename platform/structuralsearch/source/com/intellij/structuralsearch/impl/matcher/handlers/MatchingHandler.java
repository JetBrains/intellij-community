package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.filters.DefaultFilter;
import com.intellij.structuralsearch.impl.matcher.predicates.BinaryPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;

import java.util.HashSet;
import java.util.Set;

/**
 * Root of handlers for pattern node matching. Handles simplest type of the match.
 */
public abstract class MatchingHandler extends MatchPredicate {
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
  public boolean match(PsiElement patternNode,PsiElement matchedNode, int start, int end, MatchContext context) {
    return match(patternNode,matchedNode,context);
  }

  /**
   * Matches given handler node against given value.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successful and false otherwise
   */
  public boolean match(PsiElement patternNode,PsiElement matchedNode, MatchContext context) {
    if (patternNode == null) {
      return matchedNode == null;
    }

    return canMatch(patternNode, matchedNode);
  }

  public boolean canMatch(final PsiElement patternNode, final PsiElement matchedNode) {
    if (filter!=null) {
      return filter.accepts(matchedNode);
    } else {
      return DefaultFilter.accepts(patternNode, matchedNode);
    }
  }

  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    final MatchingStrategy strategy = context.getPattern().getStrategy();

    skipIfNecessary(nodes, nodes2, strategy);
    skipIfNecessary(nodes2, nodes, strategy);

    final PsiElement patternElement = nodes.current();
    final MatchingHandler handler = context.getPattern().getHandler(patternElement);
    if (nodes2.hasNext() && handler.match(patternElement, nodes2.current(), context)) {

      nodes.advance();

      final boolean shouldRewindOnMatchFailure;
      if (shouldAdvanceTheMatchFor(patternElement, nodes2.current())) {
        nodes2.advance();
        skipIfNecessary(nodes, nodes2, strategy);
        shouldRewindOnMatchFailure = true;
      }
      else {
        shouldRewindOnMatchFailure = false;
      }
      skipIfNecessary(nodes2, nodes, strategy);

      if (nodes.hasNext()) {
        final MatchingHandler nextHandler = context.getPattern().getHandler(nodes.current());

        if (nextHandler.matchSequentially(nodes,nodes2,context)) {
          // match was found!
          return true;
        } else {
          // rewind, we was not able to match descendants
          nodes.rewind();
          if (shouldRewindOnMatchFailure) nodes2.rewind();
        }
      } else {
        // match was found
        return handler.isMatchSequentiallySucceeded(nodes2);
      }
    }
    return false;
  }

  private static void skipIfNecessary(NodeIterator nodes, NodeIterator nodes2, MatchingStrategy strategy) {
    while (strategy.shouldSkip(nodes2.current(), nodes.current())) {
      nodes2.advance();
    }
  }

  protected boolean isMatchSequentiallySucceeded(final NodeIterator nodes2) {
    return !nodes2.hasNext();
  }

  private static MatchPredicate findRegExpPredicate(MatchPredicate start) {
    if (start==null) return null;
    if (start instanceof RegExpPredicate) return start;

    if(start instanceof BinaryPredicate) {
      BinaryPredicate binary = (BinaryPredicate)start;
      final MatchPredicate result = findRegExpPredicate(binary.getFirst());
      if (result!=null) return result;

      return findRegExpPredicate(binary.getSecond());
    } else if (start instanceof NotPredicate) {
      return null;
    }
    return null;
  }

  public static RegExpPredicate getSimpleRegExpPredicate(SubstitutionHandler handler) {
    if (handler == null) return null;
    return (RegExpPredicate)findRegExpPredicate(handler.getPredicate());
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
        MatchingHandler handler = pattern.getHandlerSimple(element);
        if (handler instanceof SubstitutionHandler) {
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

      for(; patternNodes.hasNext(); patternNodes.advance()) {
        final PsiElement patternNode = patternNodes.current();
        final CompiledPattern pattern = context.getPattern();
        final MatchingHandler handler = pattern.getHandler(patternNode);

        final PsiElement startMatching = matchedNodes.current();
        do {
          final PsiElement element = handler.getPinnedNode(null);
          final PsiElement matchedNode = element != null ? element : matchedNodes.current();

          if (element == null) matchedNodes.advance();
          if (!matchedNodes.hasNext()) matchedNodes.reset();

          if (matchedElements == null || !matchedElements.contains(matchedNode)) {

            if (handler.match(patternNode, matchedNode, context)) {
              if (matchedElements == null) matchedElements = new HashSet<>();
              matchedElements.add(matchedNode);
              if (handler.shouldAdvanceThePatternFor(patternNode, matchedNode)) {
                break;
              }
            } else if (element != null) {
              return false;
            }

            // clear state of dependent objects
            clearingVisitor.clearState(pattern, patternNode);
          }

          // passed of elements and does not found the match
          if (startMatching == matchedNodes.current()) {
            final boolean result = validateSatisfactionOfHandlers(patternNodes,context);
            if (result && context.getMatchedElementsListener() != null) {
              context.getMatchedElementsListener().matchedElements(matchedElements);
            }
            return result;
          }
        } while(true);

        if (!handler.shouldAdvanceThePatternFor(patternNode, null)) {
          patternNodes.rewind();
        }
      }

      final boolean result = validateSatisfactionOfHandlers(patternNodes, context);
      if (result && context.getMatchedElementsListener() != null) {
        context.getMatchedElementsListener().matchedElements(matchedElements);
      }
      return result;
    } finally {
      if (saveResult!=null) {
        if (context.hasResult()) {
          saveResult.getMatches().addAll(context.getResult().getMatches());
        }
        context.setResult(saveResult);
      }
    }
  }

  protected static boolean validateSatisfactionOfHandlers(NodeIterator nodes, MatchContext context) {
    while(nodes.hasNext()) {
      final PsiElement element = nodes.current();
      final MatchingHandler handler = context.getPattern().getHandler( element );

      if (handler instanceof SubstitutionHandler) {
        if (!((SubstitutionHandler)handler).validate(
          context, StructuralSearchUtil.getProfileByPsiElement(element).getElementContextByPsi(element))) {
          return false;
        }
      } else {
        return false;
      }
      nodes.advance();
    }
    return true;
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

  public PsiElement getPinnedNode(PsiElement context) {
    return pinnedElement;
  }

  public void setPinnedElement(final PsiElement pinnedElement) {
    this.pinnedElement = pinnedElement;
  }
}