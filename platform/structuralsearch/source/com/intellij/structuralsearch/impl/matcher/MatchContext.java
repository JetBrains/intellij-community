// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Global context of matching process
 */
public class MatchContext {
  private final Stack<MatchedElementsListener> myMatchedElementsListenerStack = new Stack<>(2);

  private MatchResultSink sink;
  private final Stack<MatchResultImpl> previousResults = new Stack<>();
  private MatchResultImpl result;
  private CompiledPattern pattern;
  private MatchOptions options;
  private GlobalMatchingVisitor matcher;
  private boolean shouldRecursivelyMatch = true;

  private final Stack<List<PsiElement>> mySavedMatchedNodes = new Stack<>();
  private List<PsiElement> myMatchedNodes = new SmartList<>();

  public void addMatchedNode(PsiElement node) {
    myMatchedNodes.add(node);
  }

  public void removeMatchedNode(PsiElement node) {
    myMatchedNodes.remove(node);
  }

  public void saveMatchedNodes() {
    mySavedMatchedNodes.push(myMatchedNodes);
    myMatchedNodes = new SmartList<>();
  }

  public void restoreMatchedNodes() {
    myMatchedNodes = mySavedMatchedNodes.tryPop();
  }

  public void clearMatchedNodes() {
    myMatchedNodes.clear();
  }

  @FunctionalInterface
  public interface MatchedElementsListener {
    void matchedElements(@NotNull Collection<PsiElement> matchedElements);
  }

  public void setMatcher(GlobalMatchingVisitor matcher) {
    this.matcher = matcher;
  }

  public GlobalMatchingVisitor getMatcher() {
    return matcher;
  }

  public MatchOptions getOptions() {
    return options;
  }

  public void setOptions(MatchOptions options) {
    this.options = options;
  }

  public MatchResultImpl getPreviousResult() {
    if (previousResults.isEmpty()) {
      return null;
    }
    else {
      int index = previousResults.size() - 1;
      MatchResultImpl result = previousResults.get(index); // may contain nulls
      while (result == null && index > 0) {
        index--;
        result = previousResults.get(index);
      }
      return result;
    }
  }

  public MatchResultImpl getResult() {
    if (result==null) result = new MatchResultImpl();
    return result;
  }

  public void pushResult() {
    previousResults.push(result);
    result = null;
  }
  
  public void popResult() {
    result = previousResults.pop();
  }
  
  public void setResult(MatchResultImpl result) {
    this.result = result;
    if (result == null) {
      pattern.clearHandlersState();
    }
  }

  public boolean hasResult() {
    return result!=null;
  }

  public CompiledPattern getPattern() {
    return pattern;
  }

  public void setPattern(CompiledPattern pattern) {
    this.pattern = pattern;
  }

  public MatchResultSink getSink() {
    return sink;
  }

  public void setSink(MatchResultSink sink) {
    this.sink = sink;
  }

  public void clear() {
    result = null;
    pattern = null;
  }

  public boolean shouldRecursivelyMatch() {
    return shouldRecursivelyMatch;
  }

  public void setShouldRecursivelyMatch(boolean shouldRecursivelyMatch) {
    this.shouldRecursivelyMatch = shouldRecursivelyMatch;
  }

  public void pushMatchedElementsListener(MatchedElementsListener matchedElementsListener) {
    myMatchedElementsListenerStack.push(matchedElementsListener);
  }

  public void popMatchedElementsListener() {
    myMatchedElementsListenerStack.pop();
  }

  public void notifyMatchedElements(Collection<PsiElement> matchedElements) {
    if (!myMatchedElementsListenerStack.isEmpty()) {
      myMatchedElementsListenerStack.peek().matchedElements(matchedElements);
    }
  }

  public void dispatchMatched() {
    if (myMatchedNodes.isEmpty()) {
      return;
    }
    final MatchResultImpl result = getResult();
    if (doDispatch(result)) return;

    // There is no substitutions so show the context

    processNoSubstitutionMatch(myMatchedNodes, result);
    getSink().newMatch(result);
  }

  private boolean doDispatch(final MatchResult result) {
    boolean ret = false;

    for (MatchResult r : result.getChildren()) {
      if ((r.isScopeMatch() && !r.isTarget()) || r.isMultipleMatch()) {
        ret |= doDispatch(r);
      }
      else if (r.isTarget()) {
        getSink().newMatch(r);
        ret = true;
      }
    }
    return ret;
  }

  private static void processNoSubstitutionMatch(List<PsiElement> matchedNodes, MatchResultImpl result) {
    boolean complexMatch = matchedNodes.size() > 1;
    final PsiElement match = matchedNodes.get(0);

    if (!complexMatch) {
      result.setMatchRef(new SmartPsiPointer(match));
      result.setMatchImage(match.getText());
    }
    else {
      for (final PsiElement matchStatement : matchedNodes) {
        result.addChild(new MatchResultImpl(MatchResult.LINE_MATCH, matchStatement.getText(), new SmartPsiPointer(matchStatement), false));
      }

      result.setMatchRef(new SmartPsiPointer(match));
      result.setMatchImage(match.getText());
      result.setName(MatchResult.MULTI_LINE_MATCH);
    }
  }
}
