/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author peter
 */
public abstract class AbstractExecutorsQuery<Result, Parameter> implements Query<Result> {
  protected final Parameter myParameters;
  private boolean myIsProcessing = false;

  public AbstractExecutorsQuery(@NotNull final Parameter params) {
    myParameters = params;
  }

  @NotNull
  public Parameter getParameters() {
    return myParameters;
  }

  @NotNull
  public Collection<Result> findAll() {
    assertNotProcessing();
    final CommonProcessors.CollectProcessor<Result> processor = new CommonProcessors.CollectProcessor<Result>();
    forEach(processor);
    return processor.getResults();
  }

  public Iterator<Result> iterator() {
    assertNotProcessing();
    return new UnmodifiableIterator<Result>(findAll().iterator());
  }

  @Nullable
  public Result findFirst() {
    assertNotProcessing();
    final CommonProcessors.FindFirstProcessor<Result> processor = new CommonProcessors.FindFirstProcessor<Result>();
    forEach(processor);
    return processor.getFoundValue();
  }

  private void assertNotProcessing() {
    assert !myIsProcessing : "Operation is not allowed while query is being processed";
  }

  public Result[] toArray(Result[] a) {
    assertNotProcessing();

    final Collection<Result> all = findAll();
    return all.toArray(a);
  }

  public boolean forEach(@NotNull Processor<Result> consumer) {
    assertNotProcessing();

    myIsProcessing = true;
    try {
      return processResults(consumer);
    }
    finally {
      myIsProcessing = false;
    }
  }

  protected abstract boolean processResults(Processor<Result> consumer);
}
