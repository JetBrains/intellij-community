// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.predicates.AndPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Matching handler that manages substitutions matching
 */
public class SubstitutionHandler extends MatchingHandler {
  private final String name;
  private final int maxOccurs;
  private final int minOccurs;
  private final boolean greedy;
  private boolean target;
  private MatchPredicate predicate;
  private MatchingHandler matchHandler;
  private boolean subtype;
  private boolean strictSubtype;
  // matchedOccurs + 1 = number of item being matched
  private int matchedOccurs;
  private int totalMatchedOccurs = -1;
  private MatchResultImpl myNestedResult;

  private static final NodeFilter VARS_DELIM_FILTER = new NodeFilter() {
    @Override
    public boolean accepts(PsiElement element) {
      if (element == null) {
        return false;
      }

      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
      if (profile == null) {
        return false;
      }

      return profile.canBeVarDelimeter(element);
    }
  };

  public SubstitutionHandler(String name, boolean target, int minOccurs, int maxOccurs, boolean greedy) {
    this.name = name;
    this.maxOccurs = maxOccurs;
    this.minOccurs = minOccurs;
    this.target = target;
    this.greedy = greedy;
  }

  public boolean isSubtype() {
    return subtype;
  }

  public boolean isStrictSubtype() {
    return strictSubtype;
  }

  public void setStrictSubtype(boolean strictSubtype) {
    this.strictSubtype = strictSubtype;
  }

  public void setSubtype(boolean subtype) {
    this.subtype = subtype;
  }

  public void setPredicate(MatchPredicate handler) {
    predicate = handler;
  }

  public MatchPredicate getPredicate() {
    return predicate;
  }

  @Nullable
  public RegExpPredicate findRegExpPredicate() {
    return findRegExpPredicate(getPredicate());
  }

  private static RegExpPredicate findRegExpPredicate(MatchPredicate start) {
    if (start==null) return null;
    if (start instanceof RegExpPredicate) return (RegExpPredicate)start;

    if(start instanceof AndPredicate) {
      AndPredicate binary = (AndPredicate)start;
      final RegExpPredicate result = findRegExpPredicate(binary.getFirst());
      if (result!=null) return result;

      return findRegExpPredicate(binary.getSecond());
    } else if (start instanceof NotPredicate) {
      return null;
    }
    return null;
  }

  private static boolean validateOneMatch(final PsiElement match, int start, int end, final MatchResult result, final MatchContext matchContext) {
    if (match != null) {
      if (start == 0 && end == -1 && result.getStart() == 0 && result.getEnd() == -1) {
        return matchContext.getMatcher().match(match, result.getMatch());
      }
      else {
        return StructuralSearchUtil.getProfileByPsiElement(match).getText(match, start, end).equals(result.getMatchImage());
      }
    }
    else {
      return result.getMatchImage() == null;
    }
  }

  public boolean validate(final PsiElement match, int start, int end, MatchContext context) {
    if (predicate != null && !predicate.match(match, start, end, context)) {
      return false;
    }

    if (maxOccurs==0) {
      totalMatchedOccurs++;
      return false;
    }

    MatchResult result = context.hasResult() ? context.getResult().findSon(name) : null;

    if (result == null && context.getPreviousResult() != null) {
      result = context.getPreviousResult().findSon(name);
    }

    if (result != null) {
      if (minOccurs == 1 && maxOccurs == 1) {
        // check if they are the same
        return validateOneMatch(match, start, end, result,context);
      } else if (maxOccurs > 1 && totalMatchedOccurs != -1) {
        if (result.isMultipleMatch()) {
          final int size = result.getAllSons().size();
          if (matchedOccurs >= size) {
            return false;
          }
          if (size != 0) {
            result = result.getAllSons().get(matchedOccurs);
          }
        }
        // check if they are the same
        return validateOneMatch(match, start, end, result, context);
      }
    }

    return true;
  }

  @Override
  public boolean match(final PsiElement node, final PsiElement match, MatchContext context) {
    if (!super.match(node,match,context)) return false;

    return matchHandler == null ?
           context.getMatcher().match(node, match):
           matchHandler.match(node,match,context);
  }

  public boolean handle(final PsiElement match, MatchContext context) {
    return handle(match,0,-1,context);
  }

  public void addResult(PsiElement match, int start, int end, MatchContext context) {
    if (totalMatchedOccurs == -1) {
      final MatchResultImpl matchResult = context.getResult();
      final MatchResultImpl substitution = matchResult.findSon(name);

      if (substitution == null) {
        matchResult.addSon( createMatch(match,start,end) );
      } else if (maxOccurs > 1) {
        final MatchResultImpl result = createMatch(match,start,end);
  
        if (!substitution.isMultipleMatch()) {
          // adding intermediate node to contain all multiple matches
          final MatchResultImpl sonresult = new MatchResultImpl(
            substitution.getName(),
            substitution.getMatchImage(),
            substitution.getMatchRef(),
            substitution.getStart(),
            substitution.getEnd(),
            target
          );

          substitution.setMatchRef(new SmartPsiPointer(match));

          substitution.setMultipleMatch(true);

          if (substitution.isScopeMatch()) {
            substitution.setScopeMatch(false);
            sonresult.setScopeMatch(true);
            for(MatchResult r:substitution.getAllSons()) sonresult.addSon((MatchResultImpl)r);
            substitution.clearMatches();
          }

          substitution.addSon( sonresult);
        } 
  
        substitution.addSon( result );
      }
    }
  }

  public boolean handle(final PsiElement match, int start, int end, MatchContext context) {
    if (!validate(match,start,end,context)) {
      myNestedResult = null;
      
      //if (maxOccurs==1 && minOccurs==1) {
      //  if (context.hasResult()) context.getResult().removeSon(name);
      //}
      // @todo we may fail fast the match by throwing an exception

      return false;
    }

    if (!Configuration.CONTEXT_VAR_NAME.equals(name)) addResult(match, start, end, context);

    return true;
  }

  private MatchResultImpl createMatch(final PsiElement match, int start, int end) {
    final String image = match == null ? null : StructuralSearchUtil.getProfileByPsiElement(match).getText(match, start, end);
    final SmartPsiPointer ref = new SmartPsiPointer(match);

    final MatchResultImpl result = myNestedResult == null ? new MatchResultImpl(
      name,
      image,
      ref,
      start,
      end,
      target
    ) : myNestedResult;

    if (myNestedResult != null) {
      myNestedResult.setName( name );
      myNestedResult.setMatchImage( image );
      myNestedResult.setMatchRef( ref );
      myNestedResult.setStart( start );
      myNestedResult.setEnd( end );
      myNestedResult.setTarget( target );
      myNestedResult = null;
    }

    return result;
  }

  boolean validate(MatchContext context, Class elementContext) {
    MatchResult substitution = context.hasResult() ? context.getResult().findSon(name) : null;

    if (minOccurs >= 1 &&
        (substitution == null || StructuralSearchUtil.getElementContextByPsi(substitution.getMatch()) != elementContext)) {
      return false;
    } else if (maxOccurs <= 1 && substitution!=null && substitution.hasSons()) {
      return false;
    } else if (maxOccurs==0 && totalMatchedOccurs!=-1) {
      return false;
    }
    return true;
  }

  public int getMinOccurs() {
    return minOccurs;
  }

  public int getMaxOccurs() {
    return maxOccurs;
  }

  private void removeLastResults(int numberOfResults, MatchContext context) {
    if (numberOfResults == 0) return;
    final MatchResultImpl substitution = context.getResult().findSon(name);

    if (substitution!=null) {
      final List<PsiElement> matchedNodes = context.getMatchedNodes();

      if (substitution.hasSons()) {
        final List<MatchResult> sons = substitution.getMatches();

        while(numberOfResults > 0) {
          --numberOfResults;
          final MatchResult matchResult = sons.remove(sons.size() - 1);
          if (matchedNodes != null) matchedNodes.remove(matchResult.getMatch());
        }

        if (sons.isEmpty()) {
          context.getResult().removeSon(name);
        }
      } else {
        final MatchResultImpl matchResult = context.getResult().removeSon(name);
        if (matchedNodes != null) matchedNodes.remove(matchResult.getMatch());
      }
    }
  }

  @Override
  public boolean matchInAnyOrder(NodeIterator patternNodes, NodeIterator matchedNodes, final MatchContext context) {
    final MatchResultImpl saveResult = context.hasResult() ? context.getResult() : null;
    context.setResult(null);

    try {

      if (patternNodes.hasNext() && !matchedNodes.hasNext()) {
        return validateSatisfactionOfHandlers(patternNodes, context);
      }

      Set<PsiElement> matchedElements = null;

      for(; patternNodes.hasNext(); patternNodes.advance()) {
        int matchedOccurs = 0;
        final PsiElement patternNode = patternNodes.current();
        final CompiledPattern pattern = context.getPattern();
        final MatchingHandler handler = pattern.getHandler(patternNode);

        final PsiElement startMatching = matchedNodes.current();
        do {
          final PsiElement element = handler.getPinnedNode();
          final PsiElement matchedNode = (element != null) ? element : matchedNodes.current();

          if (element == null) matchedNodes.advance();
          if (!matchedNodes.hasNext()) matchedNodes.reset();

          if (matchedOccurs <= maxOccurs &&
              (matchedElements == null || !matchedElements.contains(matchedNode))) {

            if (handler.match(patternNode, matchedNode, context)) {
              ++matchedOccurs;
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
            final boolean result = validateSatisfactionOfHandlers(patternNodes, context) &&
                                   matchedOccurs >= minOccurs && matchedOccurs <= maxOccurs;
            if (result && matchedElements != null && context.getMatchedElementsListener() != null) {
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
      if (result && matchedElements != null && context.getMatchedElementsListener() != null) {
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

  @Override
  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    return doMatchSequentially(nodes, nodes2, context);
  }

  protected boolean doMatchSequentiallyBySimpleHandler(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    final boolean oldValue = context.shouldRecursivelyMatch();
    context.setShouldRecursivelyMatch(false);
    final boolean result = super.matchSequentially(nodes, nodes2, context);
    context.setShouldRecursivelyMatch(oldValue);
    return result;
  }

  protected boolean doMatchSequentially(NodeIterator nodes, NodeIterator nodes2, MatchContext context) {
    final int previousMatchedOccurs = matchedOccurs;

    FilteringNodeIterator fNodes2 = new FilteringNodeIterator(nodes2, VARS_DELIM_FILTER);

    try {
      MatchingHandler handler = context.getPattern().getHandler(nodes.current());
      matchedOccurs = 0;

      boolean flag = false;

      while(fNodes2.hasNext() && matchedOccurs < minOccurs) {
        if (handler.match(nodes.current(), nodes2.current(), context)) {
          ++matchedOccurs;
        } else {
          break;
        }
        fNodes2.advance();
        flag = true;
      }

      if (matchedOccurs!=minOccurs) {
        // failed even for min occurs
        removeLastResults(matchedOccurs, context);
        fNodes2.rewind(matchedOccurs);
        return false;
      }

      if (greedy)  {
        // go greedily to maxOccurs

        while(fNodes2.hasNext() && matchedOccurs < maxOccurs) {
          if (handler.match(nodes.current(), nodes2.current(), context)) {
            ++matchedOccurs;
          } else {
            // no more matches could take!
            break;
          }
          fNodes2.advance();
          flag = true;
        }

        if (flag) {
          fNodes2.rewind();
          nodes2.advance();
        }

        nodes.advance();

        if (nodes.hasNext()) {
          final MatchingHandler nextHandler = context.getPattern().getHandler(nodes.current());

          while(matchedOccurs >= minOccurs) {
            if (nextHandler.matchSequentially(nodes, nodes2, context)) {
              totalMatchedOccurs = matchedOccurs;
              // match found
              return true;
            }

            if (matchedOccurs > 0) {
              nodes2.rewind();
              removeLastResults(1,context);
            }
            --matchedOccurs;
          }

          if (matchedOccurs > 0) {
            removeLastResults(matchedOccurs, context);
          }
          nodes.rewind();
          return false;
        } else {
          // match found
          if (handler.isMatchSequentiallySucceeded(nodes2)) {
            return checkSameOccurrencesConstraint();
          }
          removeLastResults(matchedOccurs, context);
          return false;
        }
      } else {
        nodes.advance();

        if (flag) {
          fNodes2.rewind();
          nodes2.advance();
        }

        if (nodes.hasNext()) {
          final MatchingHandler nextHandler = context.getPattern().getHandler(nodes.current());

          flag = false;

          while(nodes2.hasNext() && matchedOccurs <= maxOccurs) {
            if (nextHandler.matchSequentially(nodes, nodes2, context)) {
              return checkSameOccurrencesConstraint();
            }

            if (flag) {
              nodes2.rewind();
              fNodes2.advance();
            }

            if (handler.match(nodes.current(), nodes2.current(), context)) {
              matchedOccurs++;
            } else {
              nodes.rewind();
              removeLastResults(matchedOccurs,context);
              return false;
            }
            nodes2.advance();
            flag = true;
          }

          nodes.rewind();
          removeLastResults(matchedOccurs,context);
          return false;
        } else {
          return checkSameOccurrencesConstraint();
        }
      }
    } finally {
      matchedOccurs = previousMatchedOccurs;
    }
  }

  private boolean checkSameOccurrencesConstraint() {
    if (totalMatchedOccurs == -1) {
      totalMatchedOccurs = matchedOccurs;
      return true;
    }
    else {
      return totalMatchedOccurs == matchedOccurs;
    }
  }

  public void setTarget(boolean target) {
    this.target = target;
  }

  public void setMatchHandler(MatchingHandler matchHandler) {
    this.matchHandler = matchHandler;
  }

  public boolean isTarget() {
    return target;
  }

  public String getName() {
    return name;
  }

  @Override
  public void reset() {
    super.reset();
    totalMatchedOccurs = -1;
  }

  @Override
  public boolean shouldAdvanceThePatternFor(PsiElement patternElement, PsiElement matchedElement) {
    if(maxOccurs > 1) return false;
    return super.shouldAdvanceThePatternFor(patternElement,matchedElement);
  }

  public void setNestedResult(final MatchResultImpl nestedResult) {
    myNestedResult = nestedResult;
  }

  public MatchResultImpl getNestedResult() {
    return myNestedResult;
  }
}
