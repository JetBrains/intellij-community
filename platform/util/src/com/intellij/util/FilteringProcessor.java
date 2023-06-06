// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class FilteringProcessor<T> implements Processor<T> {
  private final Predicate<? super T> myFilter;
  private final Processor<? super T> myProcessor;

  public FilteringProcessor(@NotNull Predicate<? super T> filter, @NotNull Processor<? super T> processor) {
    myFilter = filter;
    myProcessor = processor;
  }

  /**
   * @deprecated use {@link FilteringProcessor#FilteringProcessor(Predicate, Processor)} instead
   */
  @Deprecated
  public FilteringProcessor(@NotNull Condition<? super T> filter, @NotNull Processor<? super T> processor) {
    this((Predicate<? super T>) filter, processor);
  }

  @Override
  public boolean process(final T t) {
    if (!myFilter.test(t)) return true;
    return myProcessor.process(t);
  }
}
