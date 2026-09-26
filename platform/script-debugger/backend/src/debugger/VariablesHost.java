// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.PromiseManager;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.debugger.values.ValueManager;

import java.util.List;

@ApiStatus.Internal
public abstract class VariablesHost<VALUE_MANAGER extends ValueManager> {
  @SuppressWarnings("unchecked")
  private static final PromiseManager<VariablesHost, List<Variable>> VARIABLES_LOADER =
    new PromiseManager<>(VariablesHost.class) {
      @Override
      public boolean isUpToDate(@NotNull VariablesHost host, @NotNull List<Variable> data) {
        return host.valueManager.getCacheStamp() == host.cacheStamp;
      }

      @Override
      public @NotNull Promise load(@NotNull VariablesHost host) {
        return host.valueManager.isObsolete() ? Promises.cancelledPromise() : host.load();
      }
    };

  @SuppressWarnings("UnusedDeclaration")
  private volatile Promise<List<Variable>> result;

  private volatile int cacheStamp = -1;

  public final VALUE_MANAGER valueManager;

  public VariablesHost(@NotNull VALUE_MANAGER manager) {
    valueManager = manager;
  }

  /**
   * You must call {@link #updateCacheStamp()} when data loaded
   */
  public final @NotNull Promise<List<Variable>> get() {
    return VARIABLES_LOADER.get(this);
  }

  public final @Nullable Promise.State getState() {
    return VARIABLES_LOADER.getState(this);
  }

  public final void set(@NotNull List<Variable> result) {
    updateCacheStamp();
    VARIABLES_LOADER.set(this, result);
  }

  protected abstract @NotNull Promise<List<Variable>> load();

  public final void updateCacheStamp() {
    cacheStamp = valueManager.getCacheStamp();
  }

  /**
   * Some backends requires to reload the whole call stack on scope variable modification, but not all API is asynchronous (compromise, to not increase complexity),
   * for example, {@link CallFrame#getVariableScopes()} is not asynchronous method. So, you must use returned callback to postpone your code working with updated data.
   */
  public Promise<?> clearCaches() {
    cacheStamp = -1;
    VARIABLES_LOADER.reset(this);
    return Promises.resolvedPromise();
  }
}
