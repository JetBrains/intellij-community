// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  private final GlobalMatchingVisitor matcher;
  private boolean shouldRecursivelyMatch = true;

  private final Stack<List<PsiElement>> mySavedMatchedNodes = new Stack<>();
  private List<PsiElement> myMatchedNodes = new SmartList<>();

  public MatchContext(@NotNull GlobalMatchingVisitor visitor) {
    matcher = visitor;
  }

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
    void matchedElements(@NotNull Collection<? extends PsiElement> matchedElements);
  }

  public @NotNull GlobalMatchingVisitor getMatcher() {
    return matcher;
  }

  public MatchOptions getOptions() {
    return options;
  }

  public void setOptions(@NotNull MatchOptions options) {
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

  @NotNull
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

  public void setPattern(@NotNull CompiledPattern pattern) {
    this.pattern = pattern;
  }

  public MatchResultSink getSink() {
    return sink;
  }

  public void setSink(@NotNull MatchResultSink sink) {
    this.sink = sink;
  }

  public void clear() {
    result = null;
  }

  public boolean shouldRecursivelyMatch() {
    return shouldRecursivelyMatch;
  }

  public void setShouldRecursivelyMatch(boolean shouldRecursivelyMatch) {
    this.shouldRecursivelyMatch = shouldRecursivelyMatch;
  }

  public void pushMatchedElementsListener(@NotNull MatchedElementsListener matchedElementsListener) {
    myMatchedElementsListenerStack.push(matchedElementsListener);
  }

  public void popMatchedElementsListener() {
    myMatchedElementsListenerStack.pop();
  }

  public void notifyMatchedElements(@NotNull Collection<? extends PsiElement> matchedElements) {
    if (!myMatchedElementsListenerStack.isEmpty()) {
      myMatchedElementsListenerStack.peek().matchedElements(matchedElements);
    }
  }

  public void dispatchMatched() {
    if (!myMatchedNodes.isEmpty() && !dispatchTargetMatch(getResult())) {
      dispatchCompleteMatch();
    }
  }

  private boolean dispatchTargetMatch(@NotNull MatchResult result) {
    boolean dispatched = false;

    for (MatchResult r : result.getChildren()) {
      if ((r.isScopeMatch() && !r.isTarget()) || r.isMultipleMatch()) {
        dispatched |= dispatchTargetMatch(r);
      }
      else if (r.isTarget()) {
        getSink().newMatch(r);
        dispatched = true;
      }
    }
    return dispatched;
  }

  private void dispatchCompleteMatch() {
    final MatchResultImpl result = getResult();
    final boolean complexMatch = myMatchedNodes.size() > 1;
    final PsiElement match = myMatchedNodes.get(0);

    if (!complexMatch) {
      result.setMatchRef(new SmartPsiPointer(match));
      result.setMatchImage(match.getText());
    }
    else {
      for (final PsiElement matchStatement : myMatchedNodes) {
        result.addChild(new MatchResultImpl(MatchResult.LINE_MATCH, matchStatement.getText(), new SmartPsiPointer(matchStatement), false));
      }

      result.setMatchRef(new SmartPsiPointer(match));
      result.setMatchImage(match.getText());
      result.setName(MatchResult.MULTI_LINE_MATCH);
    }
    getSink().newMatch(result);
  }
}
