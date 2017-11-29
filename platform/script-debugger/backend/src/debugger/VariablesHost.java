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
package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.PromiseManager;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.debugger.values.ValueManager;

import java.util.List;

public abstract class VariablesHost<VALUE_MANAGER extends ValueManager> {
  @SuppressWarnings("unchecked")
  private static final PromiseManager<VariablesHost, List<Variable>> VARIABLES_LOADER =
    new PromiseManager<VariablesHost, List<Variable>>(VariablesHost.class) {
      @Override
      public boolean isUpToDate(@NotNull VariablesHost host, @NotNull List<Variable> data) {
        return host.valueManager.getCacheStamp() == host.cacheStamp;
      }

      @NotNull
      @Override
      public Promise load(@NotNull VariablesHost host) {
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
  @NotNull
  public final Promise<List<Variable>> get() {
    return VARIABLES_LOADER.get(this);
  }

  @Nullable
  public final Promise.State getState() {
    return VARIABLES_LOADER.getState(this);
  }

  public final void set(@NotNull List<Variable> result) {
    updateCacheStamp();
    VARIABLES_LOADER.set(this, result);
  }

  @NotNull
  protected abstract Promise<List<Variable>> load();

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
