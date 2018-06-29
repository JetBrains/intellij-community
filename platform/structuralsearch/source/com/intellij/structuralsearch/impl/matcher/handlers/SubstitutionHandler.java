// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.predicates.AndPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    if (minOccurs < 0) throw new IllegalArgumentException("minOccurs must be greater or equal to 0");
    if (minOccurs > maxOccurs) throw new IllegalArgumentException("maxOccurs must be greater equal to minOccurs");
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
        final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(match);
        assert profile != null;
        return profile.getText(match, start, end).equals(result.getMatchImage());
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

    MatchResult result = context.hasResult() ? context.getResult().findChild(name) : null;

    if (result == null && context.getPreviousResult() != null) {
      result = context.getPreviousResult().findChild(name);
    }

    if (result != null) {
      if (minOccurs == 1 && maxOccurs == 1) {
        // check if they are the same
        return validateOneMatch(match, start, end, result,context);
      } else if (maxOccurs > 1 && totalMatchedOccurs != -1) {
        if (result.isMultipleMatch()) {
          final List<MatchResult> children = result.getChildren();
          final int size = children.size();
          if (matchedOccurs >= size) {
            return false;
          }
          if (size != 0) {
            result = children.get(matchedOccurs);
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

  public void addResult(@NotNull PsiElement match, int start, int end, MatchContext context) {
    if (totalMatchedOccurs == -1) {
      final MatchResultImpl matchResult = context.getResult();
      final MatchResultImpl substitution = matchResult.findChild(name);

      if (substitution == null) {
        matchResult.addChild(createMatch(match, start, end) );
      } else if (maxOccurs > 1) {
        final MatchResultImpl result = createMatch(match, start, end);
  
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
            for (MatchResult r : substitution.getChildren()) {
              sonresult.addChild(r);
            }
            substitution.removeChildren();
          }

          substitution.addChild(sonresult);
        } 
  
        substitution.addChild(result);
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

  private MatchResultImpl createMatch(@NotNull final PsiElement match, int start, int end) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(match);
    assert profile != null;
    final String image = profile.getText(match, start, end);
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

  @Override
  boolean validate(MatchContext context, int matchedOccurs) {
    if (minOccurs > matchedOccurs) return false;
    if (maxOccurs < matchedOccurs) return false;
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
    final MatchResultImpl substitution = context.getResult().findChild(name);

    if (substitution != null) {
      final List<PsiElement> matchedNodes = context.getMatchedNodes();

      if (substitution.hasChildren()) {
        while (numberOfResults > 0) {
          --numberOfResults;
          final MatchResult matchResult = substitution.removeLastChild();
          if (matchedNodes != null) matchedNodes.remove(matchResult.getMatch());
        }
        if (!substitution.hasChildren()) {
          context.getResult().removeChild(name);
        }
      } else {
        final MatchResult matchResult = context.getResult().removeChild(name);
        assert matchResult != null;
        if (matchedNodes != null) matchedNodes.remove(matchResult.getMatch());
      }
    }
  }

  @Override
  public boolean matchSequentially(NodeIterator patternNodes, NodeIterator matchNodes, MatchContext context) {
    return doMatchSequentially(patternNodes, matchNodes, context);
  }

  protected boolean doMatchSequentiallyBySimpleHandler(NodeIterator patternNodes, NodeIterator matchNodes, MatchContext context) {
    final boolean oldValue = context.shouldRecursivelyMatch();
    context.setShouldRecursivelyMatch(false);
    final boolean result = super.matchSequentially(patternNodes, matchNodes, context);
    context.setShouldRecursivelyMatch(oldValue);
    return result;
  }

  protected boolean doMatchSequentially(NodeIterator patternNodes, NodeIterator matchNodes, MatchContext context) {
    final int previousMatchedOccurs = matchedOccurs;
    FilteringNodeIterator fNodes = new FilteringNodeIterator(matchNodes, VARS_DELIM_FILTER);

    try {
      MatchingHandler handler = context.getPattern().getHandler(patternNodes.current());
      matchedOccurs = 0;

      boolean flag = false;

      while(fNodes.hasNext() && matchedOccurs < minOccurs) {
        if (handler.match(patternNodes.current(), matchNodes.current(), context)) {
          ++matchedOccurs;
        } else if (patternNodes.current() instanceof PsiComment || !(matchNodes.current() instanceof PsiComment)) {
          break;
        }
        fNodes.advance();
        flag = true;
      }

      if (matchedOccurs != minOccurs) {
        // failed even for min occurs
        removeLastResults(matchedOccurs, context);
        fNodes.rewind(matchedOccurs);
        return false;
      }

      if (greedy)  {
        // go greedily to maxOccurs

        while(fNodes.hasNext() && matchedOccurs < maxOccurs) {
          if (handler.match(patternNodes.current(), matchNodes.current(), context)) {
            ++matchedOccurs;
          } else if (patternNodes.current() instanceof PsiComment || !(matchNodes.current() instanceof PsiComment)) {
            break;
          }
          fNodes.advance();
          flag = true;
        }

        if (flag) {
          fNodes.rewind();
          matchNodes.advance();
        }

        patternNodes.advance();

        if (patternNodes.hasNext()) {
          final MatchingHandler nextHandler = context.getPattern().getHandler(patternNodes.current());

          while(matchedOccurs >= minOccurs) {
            if (nextHandler.matchSequentially(patternNodes, matchNodes, context)) {
              totalMatchedOccurs = matchedOccurs;
              // match found
              return true;
            }

            if (matchedOccurs > 0) {
              matchNodes.rewind();
              removeLastResults(1, context);
            }
            --matchedOccurs;
          }

          if (matchedOccurs > 0) {
            removeLastResults(matchedOccurs, context);
          }
          patternNodes.rewind();
          return false;
        } else {
          // match found
          if (handler.isMatchSequentiallySucceeded(matchNodes)) {
            return checkSameOccurrencesConstraint(context);
          }
          removeLastResults(matchedOccurs, context);
          return false;
        }
      } else {
        patternNodes.advance();

        if (flag) {
          fNodes.rewind();
          matchNodes.advance();
        }

        if (patternNodes.hasNext()) {
          final MatchingHandler nextHandler = context.getPattern().getHandler(patternNodes.current());

          flag = false;

          while(matchNodes.hasNext() && matchedOccurs <= maxOccurs) {
            if (nextHandler.matchSequentially(patternNodes, matchNodes, context)) {
              return checkSameOccurrencesConstraint(context);
            }

            if (flag) {
              matchNodes.rewind();
              fNodes.advance();
            }

            if (handler.match(patternNodes.current(), matchNodes.current(), context)) {
              matchedOccurs++;
            } else {
              patternNodes.rewind();
              removeLastResults(matchedOccurs, context);
              return false;
            }
            matchNodes.advance();
            flag = true;
          }

          patternNodes.rewind();
          removeLastResults(matchedOccurs, context);
          return false;
        } else {
          return checkSameOccurrencesConstraint(context);
        }
      }
    } finally {
      matchedOccurs = previousMatchedOccurs;
    }
  }

  private boolean checkSameOccurrencesConstraint(MatchContext context) {
    if (totalMatchedOccurs == -1) {
      totalMatchedOccurs = matchedOccurs;
      return true;
    }
    MatchResult result = context.hasResult() ? context.getResult().findChild(name) : null;
    if (result == null && context.getPreviousResult() != null) {
      result = context.getPreviousResult().findChild(name);
    }
    return result == null || result.size() == matchedOccurs;
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
