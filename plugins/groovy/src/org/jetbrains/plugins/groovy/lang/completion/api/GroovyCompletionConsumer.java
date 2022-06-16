// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion.api;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

@ApiStatus.Experimental
public interface GroovyCompletionConsumer extends AutoCloseable {

  void consume(@NotNull LookupElement element);

  void interrupt();

  @NotNull GroovyCompletionConsumer transform(@NotNull Function<? super CompletionResultSet, ? extends CompletionResultSet> transformer);

  @NotNull CompletionResultSet getCompletionResultSet();
}
