/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Futures {
  /** A representation of a {@link Future} computation result. */
  public static class Result<V> {
    private final V myResult;
    private final Throwable myError;

    private Result(V result, @Nullable Throwable error) {
      myResult = result;
      myError = error;
    }

    public boolean isOK() {
      return myError == null;
    }

    public V get() throws IllegalStateException {
      if (myError != null) {
        throw new IllegalStateException(myError);
      }
      else {
        return myResult;
      }
    }

    @Nullable
    public Throwable getError() {
      return myError;
    }
  }

  @NotNull
  public static <V> Collection<Result<V>> invokeAll(@NotNull Collection<? extends Future<? extends V>> futures) {
    List<Result<V>> results = ContainerUtil.newArrayListWithCapacity(futures.size());

    try {
      for (Future<? extends V> future : futures) {
        try {
          results.add(new Result<V>(future.get(), null));
        }
        catch (ExecutionException e) {
          results.add(new Result<V>(null, e));
        }
      }
    }
    catch (InterruptedException e) {
      Logger.getInstance(Futures.class).error(e);
      Thread.currentThread().interrupt();
    }

    return results;
  }

  private Futures() { }
}