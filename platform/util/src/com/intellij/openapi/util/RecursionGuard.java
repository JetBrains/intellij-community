/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A helper object for {@link RecursionManager}. Is obtained from {@link RecursionManager#createGuard(String)}.
 *
 * @author peter
*/
public abstract class RecursionGuard {

  /**
   * @param key an id of the computation. Is stored internally to ensure that a recursive calls with the same key won't lead to endless recursion.
   * @param memoize whether the result of the computation may me cached thread-locally until the last currently active doPreventingRecursion call
   *                completes. May be used to speedup things when recursion re-entrance happens: otherwise nothing would be cached at all and
   *                in some cases exponential performance may be observed.
   * @param computation a piece of code to compute.
   * @return the result of the computation or null if we're entering a computation with this key on this thread recursively,
   */
  @Nullable
  public abstract <T> T doPreventingRecursion(@NotNull Object key, boolean memoize, @NotNull Computable<T> computation);

  /**
   * Used in pair with {@link RecursionGuard.StackStamp#mayCacheNow()} to ensure that cached are only the reliable values,
   * not depending on anything incomplete due to recursive prevention policies.
   * A typical usage is this:
   * {@code
   *  RecursionGuard.StackStamp stamp = RecursionManager.createGuard("id").markStack();
   *
   *   Result result = doComputation();
   *
   *   if (stamp.mayCacheNow()) {
   *     cache(result);
   *   }
   *   return result;
   * }
   * @return an object representing the current stack state, managed by {@link RecursionManager}
   */
  @NotNull
  public abstract StackStamp markStack();

  /**
   * @return the current thread-local stack of keys passed to {@link #doPreventingRecursion(Object, boolean, Computable)}
   */
  @NotNull
  public abstract List<Object> currentStack();

  /**
   * Makes {@link RecursionGuard.StackStamp#mayCacheNow()} return false for all stamps created since a computation with
   * key {@code since} began.
   *
   * Used to prevent caching of results that are non-reliable NOT due to recursion prevention: for example, too deep recursion
   * ({@link #currentStack()} may help in determining the recursion depth)
   *
   * Also disables thread-local memoization (see the second parameter of {@link #doPreventingRecursion(Object, boolean, Computable)}.
   *
   * @param since the id of a computation whose result is safe to cache whilst for more nested ones it's not.
   */
  public abstract void prohibitResultCaching(@NotNull Object since);

  public interface StackStamp {

    /**
     * @return whether a computation that started at the moment of this {@link StackStamp} instance creation does not depend on any
     * re-entrant recursive results. When such non-reliable results exist in the thread's call stack, returns false, otherwise true.
     *
     * If you use this with {@link RecursionGuard#doPreventingRecursion(Object, boolean, Computable)}, then the
     * {@link RecursionGuard#markStack()}+{@link #mayCacheNow()} should be outside of recursion prevention call. Otherwise
     * even the outer recursive computation result won't be cached.
     */
    @SuppressWarnings("JavaDoc")
    boolean mayCacheNow();
  }
}
