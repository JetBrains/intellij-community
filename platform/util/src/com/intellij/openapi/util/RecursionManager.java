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

import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * There are moments when a computation A requires the result of computation B, which in turn requires C, which (unexpectedly) requires A.
 * If there are no other ways to solve it, it helps to track all the computations in the thread stack and return some default value when
 * asked to compute A for the second time. {@link RecursionGuard#doPreventingRecursion(Object, Computable)} does precisely this.
 *
 * It's quite useful to cache some computation results to avoid performance problems. But not everyone realises that in the above situation it's
 * incorrect to cache the results of B and C, because they all are based on the default incomplete result of the A calculation. If the actual
 * computation sequence were C->A->B->C, the result of the outer C most probably wouldn't be the same as in A->B->C->A, where it depends on
 * the null A result directly. The natural wish is that the program with cache enabled has the same results as the one without cache. In the above
 * situation the result of C would depend on the order of invocations of C and A, which can be hardly predictable in multithreaded environments.
 *
 * Therefore if you use any kind of cache, it probably would make your program safer to cache only when it's safe to do this. See
 * {@link com.intellij.openapi.util.RecursionGuard#markStack()} and {@link com.intellij.openapi.util.RecursionGuard.StackStamp#mayCacheNow()}
 * for the advice.
 *
 * @see RecursionGuard
 * @see RecursionGuard.StackStamp
 * @author peter
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class RecursionManager {
  private static final ThreadLocal<Integer> ourStamp = new ThreadLocal<Integer>() {
    @Override
    protected Integer initialValue() {
      return 0;
    }
  };
  private static final ThreadLocal<LinkedHashMap<Pair<String, Object>, Integer>> ourProgress = new ThreadLocal<LinkedHashMap<Pair<String, Object>, Integer>>() {
    @Override
    protected LinkedHashMap<Pair<String, Object>, Integer> initialValue() {
      return new LinkedHashMap<Pair<String, Object>, Integer>();
    }
  };

  /**
   * @param id just some string to separate different recursion prevention policies from each other
   * @return a helper object which allow you to perform reentrancy-safe computations and check whether caching will be safe.
   */
  public static RecursionGuard createGuard(@NonNls final String id) {
    return new RecursionGuard() {
      @Override
      public <T> T doPreventingRecursion(Object key, Computable<T> computation) {
        Pair<String, Object> realKey = Pair.create(id, key);
        LinkedHashMap<Pair<String, Object>, Integer> progressMap = ourProgress.get();
        if (progressMap.containsKey(realKey)) {
          prohibitResultCaching(key);

          return null;
        }

        progressMap.put(realKey, ourStamp.get());

        try {
          return computation.compute();
        }
        finally {
          ourStamp.set(progressMap.remove(realKey));
        }
      }

      @Override
      public StackStamp markStack() {
        final Integer stamp = ourStamp.get();
        return new StackStamp() {
          @Override
          public boolean mayCacheNow() {
            return Comparing.equal(stamp, ourStamp.get());
          }
        };
      }

      @Override
      public List<Object> currentStack() {
        ArrayList<Object> result = new ArrayList<Object>();
        LinkedHashMap<Pair<String, Object>, Integer> map = ourProgress.get();
        for (Pair<String, Object> pair : map.keySet()) {
          if (pair.first == id) {
            result.add(pair.second);
          }
        }
        return result;
      }

      @Override
      public void prohibitResultCaching(Object since) {
        int stamp = ourStamp.get() + 1;
        ourStamp.set(stamp);

        boolean inLoop = false;
        for (Map.Entry<Pair<String, Object>, Integer> entry: ourProgress.get().entrySet()) {
          if (inLoop) {
            entry.setValue(stamp);
          }
          else if (entry.getKey().first.equals(id) && entry.getKey().second.equals(since)) {
            inLoop = true;
          }
        }
      }
    };
  }

}
