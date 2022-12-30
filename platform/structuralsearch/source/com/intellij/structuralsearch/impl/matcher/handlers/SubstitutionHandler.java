// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiComment;
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
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handles matching for template variables in the pattern. All filtering (= search constraints) happens here.
 */
public class SubstitutionHandler extends MatchingHandler {
  @NotNull
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
  private boolean myRepeatedVar;

  private static final NodeFilter VARS_DELIM_FILTER = element -> {
    if (element == null) {
      return false;
    }

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
    if (profile == null) {
      return false;
    }

    return profile.canBeVarDelimiter(element);
  };

  public SubstitutionHandler(@NotNull String name, boolean target, int minOccurs, int maxOccurs, boolean greedy) {
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

  public void setRepeatedVar(boolean repeatedVar) {
    myRepeatedVar = repeatedVar;
  }

  public void setPredicate(@NotNull MatchPredicate handler) {
    predicate = handler;
  }

  public MatchPredicate getPredicate() {
    return predicate;
  }

  /**
   * @deprecated Use {@link SubstitutionHandler#findPredicate instead}
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public RegExpPredicate findRegExpPredicate() {
    return findPredicate(getPredicate(), RegExpPredicate.class);
  }

  public @Nullable <T extends MatchPredicate> T findPredicate(@NotNull Class<T> aClass) {
    return findPredicate(getPredicate(), aClass);
  }

  @Contract("null, _ -> null")
  private static @Nullable <T extends MatchPredicate> T findPredicate(@Nullable MatchPredicate start, @NotNull Class<T> aClass) {
    if (start == null) return null;
    if (aClass.isInstance(start)) return aClass.cast(start);
    if (start instanceof AndPredicate) {
      final AndPredicate binaryPredicate = (AndPredicate)start;
      final T firstBranchCheck = findPredicate(binaryPredicate.getFirst(), aClass);
      if (firstBranchCheck != null) return firstBranchCheck;
      return findPredicate(binaryPredicate.getSecond(), aClass);
    } else if (start instanceof NotPredicate) {
      return null;
    }
    return null;
  }

  private boolean validateOneMatch(@NotNull PsiElement match, int start, int end, @NotNull MatchResult result, @NotNull MatchContext matchContext) {
    if (!myRepeatedVar) {
      return true;
    }
    if (start == 0 && end == -1 && result.getStart() == 0 && result.getEnd() == -1) {
      return matchContext.getMatcher().match(match, result.getMatch());
    }
    else {
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(match);
      assert profile != null;
      return profile.getText(match, start, end).equals(result.getMatchImage());
    }
  }

  public boolean validate(PsiElement match, @NotNull MatchContext context) {
    return validate(match, 0, -1, context);
  }

  public boolean validate(PsiElement match, int start, int end, @NotNull MatchContext context) {
    if (match == null || predicate != null && !predicate.match(match, start, end, context)) {
      return false;
    }

    MatchResult result = context.hasResult() ? context.getResult().findChild(name) : null;
    if (result == null && myNestedResult != null) {
      result = myNestedResult.findChild(name);
    }
    if (result == null) {
      final MatchResultImpl previous = context.getPreviousResult();
      if (previous != null) {
        result = previous.findChild(name);
      }
    }

    if (result != null) {
      if (minOccurs == 1 && maxOccurs == 1) {
        // check if they are the same
        return validateOneMatch(match, start, end, result, context);
      }
      if (maxOccurs > 1 && totalMatchedOccurs != -1) {
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
  public boolean match(PsiElement node, PsiElement match, @NotNull MatchContext context) {
    if (!super.match(node, match, context)) return false;

    return matchHandler == null ?
           context.getMatcher().match(node, match):
           matchHandler.match(node, match, context);
  }

  public void addResult(@NotNull PsiElement match, @NotNull MatchContext context) {
    addResult(match, 0, -1, context);
  }

  public void addResult(@NotNull PsiElement match, int start, int end, @NotNull MatchContext context) {
    if (totalMatchedOccurs == -1) {
      final MatchResultImpl matchResult = context.getResult();
      final MatchResultImpl substitution = matchResult.getChild(name);

      if (substitution == null) {
        matchResult.addChild(createMatch(match, start, end) );
      } else if (maxOccurs > 1 || target && !myRepeatedVar) {
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

          substitution.setMatch(match);
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

  public boolean handle(PsiElement match, @NotNull MatchContext context) {
    return handle(match, 0, -1, context);
  }

  public boolean handle(PsiElement match, int start, int end, @NotNull MatchContext context) {
    if (!validate(match, start, end, context)) {
      myNestedResult = null;
      return false;
    }

    if (!Configuration.CONTEXT_VAR_NAME.equals(name)) {
      addResult(match, start, end, context);
    }

    return true;
  }

  @NotNull
  private MatchResultImpl createMatch(@NotNull PsiElement match, int start, int end) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(match);
    assert profile != null;
    final String image = profile.getText(match, start, end);

    if (myNestedResult == null) {
      return new MatchResultImpl(name, image, match, start, end, target);
    }
    final MatchResultImpl result = myNestedResult;
    result.setName(name);
    result.setMatchImage(image);
    result.setMatch(match);
    result.setStart(start);
    result.setEnd(end);
    result.setTarget(target);
    myNestedResult = null;

    return result;
  }

  @Override
  public boolean validate(@NotNull MatchContext context, int matchedOccurs) {
    if (target) return matchedOccurs > 0;
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

  private void removeLastResults(int numberOfResults, @NotNull MatchContext context) {
    if (numberOfResults == 0) return;
    final MatchResultImpl substitution = context.getResult().getChild(name);

    if (substitution != null) {
      if (substitution.hasChildren()) {
        while (numberOfResults > 0) {
          --numberOfResults;
          final MatchResult matchResult = substitution.removeLastChild();
          context.removeMatchedNode(matchResult.getMatch());
        }
        if (!substitution.hasChildren()) {
          context.getResult().removeChild(name);
        }
      } else {
        final MatchResult matchResult = context.getResult().removeChild(name);
        assert matchResult != null;
        context.removeMatchedNode(matchResult.getMatch());
      }
    }
  }

  @Override
  public boolean matchSequentially(@NotNull NodeIterator patternNodes, @NotNull NodeIterator matchNodes, @NotNull MatchContext context) {
    return doMatchSequentially(patternNodes, matchNodes, context);
  }

  protected boolean doMatchSequentiallyBySimpleHandler(NodeIterator patternNodes, NodeIterator matchNodes, @NotNull MatchContext context) {
    final boolean oldValue = context.shouldRecursivelyMatch();
    context.setShouldRecursivelyMatch(false);
    final boolean result = super.matchSequentially(patternNodes, matchNodes, context);
    context.setShouldRecursivelyMatch(oldValue);
    return result;
  }

  protected boolean doMatchSequentially(@NotNull NodeIterator patternNodes,
                                        @NotNull NodeIterator matchNodes,
                                        @NotNull MatchContext context) {
    final int previousMatchedOccurs = matchedOccurs;
    final FilteringNodeIterator fNodes = new FilteringNodeIterator(matchNodes, VARS_DELIM_FILTER);

    try {
      final CompiledPattern pattern = context.getPattern();
      final PsiElement currentPatternNode = patternNodes.current();
      final MatchingHandler handler = pattern.getHandler(currentPatternNode);
      matchedOccurs = 0;

      boolean flag = false;
      final List<PsiElement> matchedNodes = new SmartList<>();

      while (fNodes.hasNext() && (matchedOccurs < minOccurs || target && !myRepeatedVar && !(handler instanceof TopLevelMatchingHandler))) {
        final PsiElement current = matchNodes.current();
        if (handler.match(currentPatternNode, current, context)) {
          matchedNodes.add(current);
          ++matchedOccurs;
        }
        else if (handler instanceof TopLevelMatchingHandler && matchedOccurs == 0 ||
                 currentPatternNode instanceof PsiComment ||
                 !(matchNodes.current() instanceof PsiComment)) {
          break;
        }
        fNodes.advance();
        flag = true;
      }

      if (matchedOccurs != minOccurs && (!target || myRepeatedVar || matchedOccurs == 0)) {
        // failed even for min occurs
        removeLastResults(matchedOccurs, context);
        fNodes.rewind(matchedOccurs);
        return false;
      }

      if (greedy) {
        // go greedily to maxOccurs

        while (fNodes.hasNext() && matchedOccurs < maxOccurs) {
          final PsiElement current = matchNodes.current();
          if (handler.match(currentPatternNode, current, context)) {
            matchedNodes.add(current);
            ++matchedOccurs;
          }
          else if (handler instanceof TopLevelMatchingHandler && matchedOccurs == 0 ||
                   currentPatternNode instanceof PsiComment ||
                   !(matchNodes.current() instanceof PsiComment)) {
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
          final MatchingHandler nextHandler = pattern.getHandler(patternNodes.current());

          while (matchedOccurs >= minOccurs && patternNodes.hasNext()) {
            if (nextHandler.matchSequentially(patternNodes, matchNodes, context)) {
              totalMatchedOccurs = matchedOccurs;
              // match found
              return true;
            }

            final int size = matchedNodes.size();
            if (size > 0) {
              matchNodes.rewindTo(matchedNodes.remove(size - 1));
            }
            removeLastResults(1, context);
            --matchedOccurs;
          }

          if (matchedOccurs > 0) {
            removeLastResults(matchedOccurs, context);
          }
          patternNodes.rewind();
        }
        else {
          // match found
          if (handler.isMatchSequentiallySucceeded(matchNodes)) {
            return checkSameOccurrencesConstraint(context);
          }
          removeLastResults(matchedOccurs, context);
        }
        return false;
      }
      else {
        patternNodes.advance();

        if (flag) {
          fNodes.rewind();
          matchNodes.advance();
        }

        if (patternNodes.hasNext()) {
          final MatchingHandler nextHandler = pattern.getHandler(patternNodes.current());

          flag = false;

          while (matchNodes.hasNext() && matchedOccurs <= maxOccurs) {
            if (nextHandler.matchSequentially(patternNodes, matchNodes, context)) {
              return checkSameOccurrencesConstraint(context);
            }

            if (flag) {
              matchNodes.rewind();
              fNodes.advance();
            }

            if (handler.match(patternNodes.current(), matchNodes.current(), context)) {
              matchedOccurs++;
            }
            else {
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
        }
        else {
          return checkSameOccurrencesConstraint(context);
        }
      }
    }
    finally {
      matchedOccurs = previousMatchedOccurs;
    }
  }

  private boolean checkSameOccurrencesConstraint(@NotNull MatchContext context) {
    if (totalMatchedOccurs == -1) {
      totalMatchedOccurs = matchedOccurs;
      return true;
    }
    MatchResult result = context.hasResult() ? context.getResult().getChild(name) : null;
    if (result == null && context.getPreviousResult() != null) {
      result = context.getPreviousResult().getChild(name);
    }
    return result == null || result.size() == matchedOccurs || target;
  }

  public void setTarget(boolean target) {
    this.target = target;
  }

  public void setMatchHandler(@NotNull MatchingHandler matchHandler) {
    this.matchHandler = matchHandler;
  }

  public boolean isTarget() {
    return target;
  }

  public @NotNull String getName() {
    return name;
  }

  @Override
  public void reset() {
    super.reset();
    totalMatchedOccurs = -1;
  }

  @Override
  public boolean shouldAdvanceThePatternFor(@NotNull PsiElement patternElement, @NotNull PsiElement matchedElement) {
    return maxOccurs <= 1 && !target;
  }

  public void setNestedResult(MatchResultImpl nestedResult) {
    myNestedResult = nestedResult;
  }

  public MatchResultImpl getNestedResult() {
    return myNestedResult;
  }
}
