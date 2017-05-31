/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.util.containers.Stack;

import java.util.Collection;
import java.util.List;

/**
 * Global context of matching process
 */
public class MatchContext {
  private MatchResultSink sink;
  private final Stack<MatchResultImpl> previousResults = new Stack<>();
  private MatchResultImpl result;
  private CompiledPattern pattern;
  private MatchOptions options;
  private GlobalMatchingVisitor matcher;
  private boolean shouldRecursivelyMatch = true;

  private List<PsiElement> myMatchedNodes;

  public List<PsiElement> getMatchedNodes() {
    return myMatchedNodes;
  }

  public void setMatchedNodes(final List<PsiElement> matchedNodes) {
    myMatchedNodes = matchedNodes;
  }

  public interface MatchedElementsListener {
    void matchedElements(Collection<PsiElement> matchedElements);
    void commitUnmatched();
  }

  private MatchedElementsListener myMatchedElementsListener;

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
    return previousResults.isEmpty() ? null : previousResults.peek();
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

  public void setMatchedElementsListener(MatchedElementsListener _matchedElementsListener) {
    myMatchedElementsListener = _matchedElementsListener;
  }

  public MatchedElementsListener getMatchedElementsListener() {
    return myMatchedElementsListener;
  }
}
