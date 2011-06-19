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

import com.intellij.reference.SoftReference;
import com.intellij.util.containers.SoftHashMap;
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
 * situation the result of C would depend on the order of invocations of C and A, which can be hardly predictable in multi-threaded environments.
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
  private static final Object NULL = new Object();
  private static final ThreadLocal<Integer> ourStamp = new ThreadLocal<Integer>() {
    @Override
    protected Integer initialValue() {
      return 0;
    }
  };
  private static final ThreadLocal<Integer> ourMemoizationStamp = new ThreadLocal<Integer>() {
    @Override
    protected Integer initialValue() {
      return 0;
    }
  };
  private static final ThreadLocal<LinkedHashMap<MyKey, Integer>> ourProgress = new ThreadLocal<LinkedHashMap<MyKey, Integer>>() {
    @Override
    protected LinkedHashMap<MyKey, Integer> initialValue() {
      return new LinkedHashMap<MyKey, Integer>();
    }
  };
  private static final ThreadLocal<Map<MyKey, SoftReference>> ourIntermediateCache = new ThreadLocal<Map<MyKey, SoftReference>>() {
    @Override
    protected Map<MyKey, SoftReference> initialValue() {
      return new SoftHashMap<MyKey, SoftReference>();
    }
  };

  /**
   * @param id just some string to separate different recursion prevention policies from each other
   * @return a helper object which allow you to perform reentrancy-safe computations and check whether caching will be safe.
   */
  public static RecursionGuard createGuard(@NonNls final String id) {
    return new RecursionGuard() {
      @Override
      public <T> T doPreventingRecursion(Object key, boolean memoize, Computable<T> computation) {
        MyKey realKey = new MyKey(id, key);
        LinkedHashMap<MyKey, Integer> progressMap = ourProgress.get();
        if (progressMap.containsKey(realKey)) {
          _prohibitResultCaching(key);

          return null;
        }

        if (memoize) {
          SoftReference reference = ourIntermediateCache.get().get(realKey);
          if (reference != null) {
            Object o = reference.get();
            if (o != null) {
              //noinspection unchecked
              return o == NULL ? null : (T)o;
            }
          }
        }

        progressMap.put(realKey, ourStamp.get());
        int startStamp = ourMemoizationStamp.get();

        try {
          T result = computation.compute();

          if (memoize && ourMemoizationStamp.get() == startStamp) {
            ourIntermediateCache.get().put(realKey, new SoftReference<Object>(result == null ? NULL : result));
          }

          return result;
        }
        finally {
          Integer value = progressMap.remove(realKey);
          ourStamp.set(value);
          if (value == 0) {
            ourIntermediateCache.get().clear();
          }
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
        LinkedHashMap<MyKey, Integer> map = ourProgress.get();
        for (MyKey pair : map.keySet()) {
          if (pair.first == id) {
            result.add(pair.second);
          }
        }
        return result;
      }

      @Override
      public void prohibitResultCaching(Object since) {
        ourMemoizationStamp.set(_prohibitResultCaching(since));
      }

      private int _prohibitResultCaching(Object since) {
        int stamp = ourStamp.get() + 1;
        ourStamp.set(stamp);

        boolean inLoop = false;
        for (Map.Entry<MyKey, Integer> entry: ourProgress.get().entrySet()) {
          if (inLoop) {
            entry.setValue(stamp);
          }
          else if (entry.getKey().first.equals(id) && entry.getKey().second.equals(since)) {
            inLoop = true;
          }
        }
        return stamp;
      }
    };
  }
  
  private static class MyKey extends Pair<String, Object> {
    public MyKey(String first, Object second) {
      super(first, second);
    }
  }

}
