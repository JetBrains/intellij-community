// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion.impl;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionConsumer;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Intended for sorting completion elements, avoids the troubles with "frozen" completion results
 */
public class AccumulatingGroovyCompletionConsumer implements GroovyCompletionConsumer {

  private final CompletionResultSet myResultSet;
  private final Set<LookupElement> myAccumulator;

  public AccumulatingGroovyCompletionConsumer(CompletionResultSet set) {
    this(set, ConcurrentCollectionFactory.createConcurrentIdentitySet());
  }

  private AccumulatingGroovyCompletionConsumer(CompletionResultSet set, Set<LookupElement> accumulator) {
    myResultSet = set;
    myAccumulator = accumulator;
  }

  @Override
  public void consume(@NotNull LookupElement element) {
    myAccumulator.add(element);
  }

  @Override
  public void fastElementsProcessed(CompletionParameters parameters) {
    Set<LookupElement> newSet = new HashSet<>(myAccumulator);
    myAccumulator.clear();
    myResultSet.addAllElements(newSet);
  }

  @Override
  public void interrupt() {
    close();
    myResultSet.stopHere();
  }

  @Override
  public void close() {
    myResultSet.addAllElements(myAccumulator);
  }

  @Override
  public @NotNull GroovyCompletionConsumer transform(@NotNull Function<? super CompletionResultSet, ? extends CompletionResultSet> transformer) {
    return new AccumulatingGroovyCompletionConsumer(transformer.apply(getCompletionResultSet()), myAccumulator);
  }

  @Override
  public @NotNull CompletionResultSet getCompletionResultSet() {
    return myResultSet;
  }
}
