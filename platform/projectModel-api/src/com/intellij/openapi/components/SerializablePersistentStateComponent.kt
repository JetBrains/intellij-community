// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.UnaryOperator;

@SuppressWarnings({"unchecked", "FieldMayBeFinal"})
@ApiStatus.Experimental
public abstract class SerializablePersistentStateComponent<T> implements PersistentStateComponentWithModificationTracker<T> {

  private static final @NotNull VarHandle STATE_HANDLE;
  private static final @NotNull VarHandle TIMESTAMP_HANDLE;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(SerializablePersistentStateComponent.class,
                                                                  MethodHandles.lookup());

      STATE_HANDLE = lookup.findVarHandle(SerializablePersistentStateComponent.class,
                                          "myState",
                                          Object.class);

      TIMESTAMP_HANDLE = lookup.findVarHandle(SerializablePersistentStateComponent.class,
                                              "myTimestamp",
                                              long.class);
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      Logger.getInstance(SerializablePersistentStateComponent.class).error(e);
      throw ExtensionNotApplicableException.create();
    }
  }

  private @NotNull T myState;
  private long myTimestamp = 0L;

  protected SerializablePersistentStateComponent(@NotNull T state) {
    myState = state;
  }

  @Override
  public final @NotNull T getState() {
    return (T)STATE_HANDLE.getVolatile(this);
  }

  public final void setState(@NotNull T state) {
    STATE_HANDLE.setVolatile(this, state);
  }

  @Override
  public final void loadState(@NotNull T state) {
    setState(state);
  }

  @Override
  public final long getStateModificationCount() {
    return (long)TIMESTAMP_HANDLE.get(this);
  }

  /**
   * See {@link java.util.concurrent.atomic.AtomicReference#updateAndGet(UnaryOperator)}.
   *
   * @param updateFunction a function to merge states
   */
  protected final void updateState(@NotNull UnaryOperator<T> updateFunction) {
    T prev = getState(), next = null;

    for (boolean haveNext = false; ; ) {
      if (!haveNext) {
        next = updateFunction.apply(prev);
      }

      if (STATE_HANDLE.weakCompareAndSet(this, prev, next)) {
        TIMESTAMP_HANDLE.getAndAdd(this, 1L);
        break;
      }

      haveNext = (prev == (prev = getState()));
    }
  }
}
