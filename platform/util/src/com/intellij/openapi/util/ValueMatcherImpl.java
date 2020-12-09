// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

final class ValueMatcherImpl<T, T1> implements ValueKey.BeforeIf<T>, ValueKey.BeforeThen<T, T1> {
  private enum State {
    NOT_MATCHED, IGNORING, SKIPPING, MATCHING, MATCHED, FINISHED
  }

  private @NotNull final String myKey;
  private @NotNull State myState = State.NOT_MATCHED;
  private T myValue;

  ValueMatcherImpl(@NotNull String key) {
    myKey = key;
  }

  @Override
  public @NotNull String getKeyName() {
    return myKey;
  }

  @NotNull
  @Override
  public <TT> ValueKey.BeforeThen<T, TT> ifEq(@NotNull ValueKey<TT> key) {
    switch (myState) {
      case FINISHED:
        throw new IllegalStateException("Matching is already finished");
      case IGNORING:
      case MATCHING:
      case SKIPPING:
        throw new IllegalStateException("'then'/'thenGet'/'or' call is expected");
      case MATCHED:
        if (key.getName().equals(myKey)) {
          throw new IllegalStateException("Key '" + key.getName() + "' already matched");
        }
        myState = State.SKIPPING;
        break;
      case NOT_MATCHED:
        myState = key.getName().equals(myKey) ? State.MATCHING : State.IGNORING;
    }
    //noinspection unchecked
    return (ValueKey.BeforeThen<T, TT>)this;
  }

  @Override
  public T get() {
    switch (myState) {
      case FINISHED:
        throw new IllegalStateException("Matching is already finished");
      case NOT_MATCHED:
        myState = State.FINISHED;
        throw new NoSuchElementException("Requested key '" + myKey + "' is not matched");
      case MATCHED:
        myState = State.FINISHED;
        return myValue;
      default:
        throw new IllegalStateException("'then'/'thenGet'/'or' call is expected");
    }
  }

  @Nullable
  @Override
  public T orNull() {
    switch (myState) {
      case FINISHED:
        throw new IllegalStateException("Matching is already finished");
      case NOT_MATCHED:
      case MATCHED:
        myState = State.FINISHED;
        return myValue;
      default:
        throw new IllegalStateException("'then'/'thenGet'/'or' call is expected");
    }
  }

  @NotNull
  @Override
  public ValueKey.BeforeThen<T, T1> or(@NotNull ValueKey<T1> key) {
    switch (myState) {
      case FINISHED:
        throw new IllegalStateException("Matching is already finished");
      case MATCHED:
      case NOT_MATCHED:
        throw new IllegalStateException("'ifEq'/'get'/'orNull' call is expected");
      case SKIPPING:
      case MATCHING:
        if (key.getName().equals(myKey)) {
          throw new IllegalStateException("Key '" + key.getName() + "' already matched");
        }
        break;
      case IGNORING:
        if (key.getName().equals(myKey)) {
          myState = State.MATCHING;
        }
        break;
    }
    return this;
  }

  @NotNull
  @Override
  public ValueKey.BeforeIf<T> then(T1 value) {
    switch (myState) {
      case FINISHED:
        throw new IllegalStateException("Matching is already finished");
      case MATCHED:
      case NOT_MATCHED:
        throw new IllegalStateException("'ifEq'/'get'/'orNull' call is expected");
      case SKIPPING:
        myState = State.MATCHED;
        break;
      case IGNORING:
        myState = State.NOT_MATCHED;
        break;
      case MATCHING:
        myState = State.MATCHED;
        //noinspection unchecked
        myValue = (T)value;
    }
    return this;
  }

  @NotNull
  @Override
  public ValueKey.BeforeIf<T> thenGet(@NotNull Supplier<? extends T1> fn) {
    switch (myState) {
      case FINISHED:
        throw new IllegalStateException("Matching is already finished");
      case MATCHED:
      case NOT_MATCHED:
        throw new IllegalStateException("'ifEq'/'get'/'orNull' call is expected");
      case SKIPPING:
        myState = State.MATCHED;
        break;
      case IGNORING:
        myState = State.NOT_MATCHED;
        break;
      case MATCHING:
        myState = State.MATCHED;
        //noinspection unchecked
        myValue = (T)fn.get();
    }
    return this;
  }
}
