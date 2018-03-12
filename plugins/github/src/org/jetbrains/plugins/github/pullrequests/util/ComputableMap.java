/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullrequests.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ExecutionException;

public abstract class ComputableMap<K, R> {
  private final Object LOCK = new Object();
  private final Map<K, FutureResult<R>> myMap = createMap();

  @NotNull
  protected abstract R compute(@NotNull K key) throws Exception;

  protected abstract void handleError(@NotNull Exception e);

  @NotNull
  protected Map<K, FutureResult<R>> createMap() {
    return ContainerUtil.newHashMap();
  }


  @NotNull
  public FutureResult<R> getFuture(@NotNull K key) {
    synchronized (LOCK) {
      FutureResult<R> future = myMap.get(key);
      if (future != null) return future;

      future = computeInBackground(key);
      myMap.put(key, future);
      return future;
    }
  }

  @Nullable
  public R get(@NotNull K key) {
    try {
      return getFuture(key).get();
    }
    catch (InterruptedException | ExecutionException e) {
      return null;
    }
  }


  @NotNull
  private FutureResult<R> computeInBackground(@NotNull K key) {
    FutureResult<R> future = new FutureResult<R>();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        future.set(compute(key));
      }
      catch (Exception e) {
        handleError(e);
      }
    });

    return future;
  }
}
