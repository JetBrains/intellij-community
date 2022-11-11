// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion.api;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

@ApiStatus.Experimental
public interface GroovyCompletionConsumer extends AutoCloseable {

  /**
   * Accepts a lookup element
   */
  void consume(@NotNull LookupElement element);

  /**
   * This method is called right before the start of absorbing slow variants.
   */
  default void fastElementsProcessed(CompletionParameters parameters) {}

  /**
   * After this method is invoked, {@link GroovyCompletionConsumer#consume} shouldn't accept results anymore
   */
  void interrupt();

  /**
   * Allows to apply modifications to a result set
   */
  @NotNull GroovyCompletionConsumer transform(@NotNull Function<? super CompletionResultSet, ? extends CompletionResultSet> transformer);

  @NotNull CompletionResultSet getCompletionResultSet();
}
