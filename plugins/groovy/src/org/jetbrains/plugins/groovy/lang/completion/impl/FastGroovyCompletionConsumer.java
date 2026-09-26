// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion.impl;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionConsumer;

import java.util.function.Function;

public class FastGroovyCompletionConsumer implements GroovyCompletionConsumer {
  private final CompletionResultSet myResultSet;

  public FastGroovyCompletionConsumer(CompletionResultSet set) { myResultSet = set; }

  @Override
  public void consume(@NotNull LookupElement element) {
    myResultSet.consume(element);
  }

  @Override
  public void interrupt() {
    myResultSet.stopHere();
  }

  @Override
  public void close() {
  }

  @Override
  public @NotNull GroovyCompletionConsumer transform(@NotNull Function<? super CompletionResultSet, ? extends CompletionResultSet> transformer) {
    return new FastGroovyCompletionConsumer(transformer.apply(getCompletionResultSet()));
  }

  @Override
  public @NotNull CompletionResultSet getCompletionResultSet() {
    return myResultSet;
  }
}
