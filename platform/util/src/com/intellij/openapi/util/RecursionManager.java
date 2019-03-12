// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * A utility to prevent endless recursion and ensure the caching returns stable results if such endless recursion is prevented.<p></p>
 *
 * Imagine a method {@code A()} calls method {@code B()}, which in turn calls {@code C()},
 * which (unexpectedly) calls {@code A()} again (it's just an example; the loop could be shorter or longer).
 * This would normally result in endless recursion and stack overflow. One should avoid situations like these at all cost,
 * but if that's impossible (e.g. due to different plugins unaware of each other yet calling each other),
 * {@code RecursionManager} is to the rescue.<p></p>
 *
 * It helps to track all the computations in the thread stack and return some default value when
 * asked to compute {@code A()} for the second time. {@link #doPreventingRecursion} does precisely this, returning {@code null} when
 * endless recursion would otherwise happen.<p></p>
 *
 * Additionally, imagine all these methods {@code A()}, {@code B()} and {@code C()} cache their results.
 * Note that if not {@code A()} is called first, but {@code B()} or {@code C()}, the endless recursion would stay just the same,
 * but it would be prevented in different places ({@code B()} or {@code C()}, respectively). That'd mean there's 3 situations possible:
 * <ol>
 *   <li>{@code C()} calls {@code A()} and gets {@code null} as the result (if {@code A()} is first in the stack)</li>
 *   <li>{@code C()} calls {@code A()} which calls {@code B()} and gets {@code null} as the result (if {@code B()} is first in the stack)</li>
 *   <li>{@code C()} calls {@code A()} which calls {@code B()} which calls {@code C()} and gets {@code null} as the result (if {@code C()} is first in the stack)</li>
 * </ol>
 * Most likely, the results of {@code C()} would be different in those 3 cases, and it'd be unwise to cache just any of them randomly,
 * whatever is calculated first. In a multithreaded environment, that'd lead to unpredictability.<p></p>
 * 
 * Of the 3 possible scenarios above, caching for {@code C()} makes sense only for the last one, because that's the result we'd get if there were no caching at all.
 * Therefore, if you use any kind of caching in an endless-recursion-prone environment, please ensure you don't cache incomplete results
 * that happen when you're inside the evil recursion loop.
 * {@code RecursionManager} assists in distinguishing this situation and allowing caching outside that loop, but disallowing it inside.<p></p>
 *
 * To prevent caching incorrect values, please create a {@code private static final} field of {@link #createGuard} call, and then use
 * {@link RecursionGuard#markStack()} and {@link RecursionGuard.StackStamp#mayCacheNow()}
 * on it.<p></p>
 *
 * Note that the above only helps with idempotent recursion loops, that is, the ones that stabilize after one iteration, so that
 * {@code A()->B()->C()->null} returns the same value as {@code A()->B()->C()->A()->B()->C()->null} etc. If your functions lack that quality
 * (e.g. if they add items to some list), you won't get stable caching results ever, and your code will produce unpredictable results
 * with hard-to-catch bugs. Therefore, please strive for idempotence.
 *
 * @author peter
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public class RecursionManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.RecursionManager");
  private static final Object NULL = ObjectUtils.sentinel("RecursionManager.NULL");
  private static final ThreadLocal<CalculationStack> ourStack = ThreadLocal.withInitial(CalculationStack::new);
  private static boolean ourAssertOnPrevention;

  /**
   * Run the given computation, unless it's already running in this thread.
   * This is same as {@link RecursionGuard#doPreventingRecursion(Object, boolean, Computable)},
   * without a need to bother to create {@link RecursionGuard}.
   */
  @Nullable
  public static <T> T doPreventingRecursion(@NotNull Object key, boolean memoize, Computable<T> computation) {
    return createGuard(computation.getClass().getName()).doPreventingRecursion(key, memoize, computation);
  }

  /**
   * @param id just some string to separate different recursion prevention policies from each other
   * @return a helper object which allow you to perform reentrancy-safe computations and check whether caching will be safe.
   */
  @NotNull
  public static RecursionGuard createGuard(@NonNls final String id) {
    return new RecursionGuard() {
      @Override
      public <T> T doPreventingRecursion(@NotNull Object key, boolean memoize, @NotNull Computable<T> computation) {
        MyKey realKey = new MyKey(id, key, true);
        final CalculationStack stack = ourStack.get();

        if (stack.checkReentrancy(realKey)) {
          if (ourAssertOnPrevention) {
            throw new StackOverflowPreventedException("Endless recursion prevention occurred");
          }
          return null;
        }

        if (memoize) {
          Object o = stack.getMemoizedValue(realKey);
          if (o != null) {
            Map<MyKey, Object> map = stack.intermediateCache.get(realKey);
            if (map != null) {
              for (MyKey noCacheUntil : map.keySet()) {
                stack.prohibitResultCaching(noCacheUntil);
              }
            }

            //noinspection unchecked
            return o == NULL ? null : (T)o;
          }
        }

        realKey = new MyKey(id, key, false);

        final int sizeBefore = stack.progressMap.size();
        stack.beforeComputation(realKey);
        final int sizeAfter = stack.progressMap.size();
        int startStamp = stack.memoizationStamp;

        try {
          T result = computation.compute();

          if (memoize) {
            stack.maybeMemoize(realKey, result == null ? NULL : result, startStamp);
          }

          return result;
        }
        finally {
          try {
            stack.afterComputation(realKey, sizeBefore, sizeAfter);
          }
          catch (Throwable e) {
            //noinspection ThrowFromFinallyBlock
            throw new RuntimeException("Throwable in afterComputation", e);
          }

          stack.checkDepth("4");
        }
      }

      @NotNull
      @Override
      public StackStamp markStack() {
        final int stamp = ourStack.get().reentrancyCount;
        return new StackStamp() {
          @Override
          public boolean mayCacheNow() {
            return stamp == ourStack.get().reentrancyCount;
          }
        };
      }

      @NotNull
      @Override
      public List<Object> currentStack() {
        ArrayList<Object> result = new ArrayList<>();
        LinkedHashMap<MyKey, Integer> map = ourStack.get().progressMap;
        for (MyKey pair : map.keySet()) {
          if (pair.guardId.equals(id)) {
            result.add(pair.userObject);
          }
        }
        return result;
      }

      @Override
      public void prohibitResultCaching(@NotNull Object since) {
        MyKey realKey = new MyKey(id, since, false);
        final CalculationStack stack = ourStack.get();
        stack.enableMemoization(realKey, stack.prohibitResultCaching(realKey));
        stack.memoizationStamp++;
      }

    };
  }

  private static class MyKey {
    final String guardId;
    final Object userObject;
    private final int myHashCode;
    private final boolean myCallEquals;

    MyKey(String guardId, @NotNull Object userObject, boolean mayCallEquals) {
      this.guardId = guardId;
      this.userObject = userObject;
      // remember user object hashCode to ensure our internal maps consistency
      myHashCode = guardId.hashCode() * 31 + userObject.hashCode();
      myCallEquals = mayCallEquals;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MyKey && guardId.equals(((MyKey)obj).guardId))) return false;
      if (userObject == ((MyKey)obj).userObject) {
        return true;
      }
      if (myCallEquals || ((MyKey)obj).myCallEquals) {
        return userObject.equals(((MyKey)obj).userObject);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return myHashCode;
    }
  }

  private static class CalculationStack {
    private int reentrancyCount;
    private int memoizationStamp;
    private int depth;
    private final LinkedHashMap<MyKey, Integer> progressMap = new LinkedHashMap<>();
    private final Set<MyKey> toMemoize = new THashSet<>();
    private final THashMap<MyKey, MyKey> key2ReentrancyDuringItsCalculation = new THashMap<>();
    private final Map<MyKey, Map<MyKey, Object>> intermediateCache = ContainerUtil.createSoftMap();
    private int enters;
    private int exits;

    boolean checkReentrancy(MyKey realKey) {
      if (progressMap.containsKey(realKey)) {
        enableMemoization(realKey, prohibitResultCaching(realKey));

        return true;
      }
      return false;
    }

    @Nullable
    Object getMemoizedValue(MyKey realKey) {
      Map<MyKey, Object> map = intermediateCache.get(realKey);
      if (map == null) return null;

      if (depth == 0) {
        throw new AssertionError("Memoized values with empty stack");
      }

      for (MyKey key : map.keySet()) {
        final Object result = map.get(key);
        if (result != null) {
          return result;
        }
      }

      return null;
    }

    final void beforeComputation(MyKey realKey) {
      enters++;
      
      if (progressMap.isEmpty()) {
        assert reentrancyCount == 0 : "Non-zero stamp with empty stack: " + reentrancyCount;
      }

      checkDepth("1");

      int sizeBefore = progressMap.size();
      progressMap.put(realKey, reentrancyCount);
      depth++;

      checkDepth("2");

      int sizeAfter = progressMap.size();
      if (sizeAfter != sizeBefore + 1) {
        LOG.error("Key doesn't lead to the map size increase: " + sizeBefore + " " + sizeAfter + " " + realKey.userObject);
      }
    }

    final void maybeMemoize(MyKey realKey, @NotNull Object result, int startStamp) {
      if (memoizationStamp == startStamp && toMemoize.contains(realKey)) {
        Map<MyKey, Object> map = intermediateCache.get(realKey);
        if (map == null) {
          intermediateCache.put(realKey, map = ContainerUtil.createSoftKeySoftValueMap());
        }
        final MyKey reentered = key2ReentrancyDuringItsCalculation.get(realKey);
        assert reentered != null;
        map.put(reentered, result);
      }
    }

    final void afterComputation(MyKey realKey, int sizeBefore, int sizeAfter) {
      exits++;
      if (sizeAfter != progressMap.size()) {
        LOG.error("Map size changed: " + progressMap.size() + " " + sizeAfter + " " + realKey.userObject);
      }

      if (depth != progressMap.size()) {
        LOG.error("Inconsistent depth after computation; depth=" + depth + "; map=" + progressMap);
      }

      Integer value = progressMap.remove(realKey);
      depth--;
      toMemoize.remove(realKey);
      key2ReentrancyDuringItsCalculation.remove(realKey);

      if (depth == 0) {
        intermediateCache.clear();
        if (!key2ReentrancyDuringItsCalculation.isEmpty()) {
          LOG.error("non-empty key2ReentrancyDuringItsCalculation: " + new HashMap<>(key2ReentrancyDuringItsCalculation));
        }
        if (!toMemoize.isEmpty()) {
          LOG.error("non-empty toMemoize: " + new HashSet<>(toMemoize));
        }
      }

      if (sizeBefore != progressMap.size()) {
        LOG.error("Map size doesn't decrease: " + progressMap.size() + " " + sizeBefore + " " + realKey.userObject);
      }

      reentrancyCount = value;
      checkZero();

    }

    private void enableMemoization(MyKey realKey, Set<MyKey> loop) {
      toMemoize.addAll(loop);
      List<MyKey> stack = new ArrayList<>(progressMap.keySet());

      for (MyKey key : loop) {
        final MyKey existing = key2ReentrancyDuringItsCalculation.get(key);
        if (existing == null || stack.indexOf(realKey) >= stack.indexOf(key)) {
          key2ReentrancyDuringItsCalculation.put(key, realKey);
        }
      }
    }

    private Set<MyKey> prohibitResultCaching(MyKey realKey) {
      reentrancyCount++;

      if (!checkZero()) {
        throw new AssertionError("zero1");
      }

      Set<MyKey> loop = new THashSet<>();
      boolean inLoop = false;
      for (Map.Entry<MyKey, Integer> entry: new ArrayList<>(progressMap.entrySet())) {
        if (inLoop) {
          entry.setValue(reentrancyCount);
          loop.add(entry.getKey());
        }
        else if (entry.getKey().equals(realKey)) {
          inLoop = true;
        }
      }

      if (!checkZero()) {
        throw new AssertionError("zero2");
      }
      return loop;
    }

    private void checkDepth(String s) {
      int oldDepth = depth;
      if (oldDepth != progressMap.size()) {
        depth = progressMap.size();
        throw new AssertionError("_Inconsistent depth " + s + "; depth=" + oldDepth + "; enters=" + enters + "; exits=" + exits + "; map=" + progressMap);
      }
    }

    private boolean checkZero() {
      if (!progressMap.isEmpty() && !new Integer(0).equals(progressMap.get(progressMap.keySet().iterator().next()))) {
        LOG.error("Prisoner Zero has escaped: " + progressMap + "; value=" + progressMap.get(progressMap.keySet().iterator().next()));
        return false;
      }
      return true;
    }

  }

  @TestOnly
  public static void assertOnRecursionPrevention(@NotNull Disposable parentDisposable) {
    ourAssertOnPrevention = true;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourAssertOnPrevention = false;
      }
    });
  }

  @TestOnly
  public static void disableAssertOnRecursionPrevention() {
    ourAssertOnPrevention = false;
  }
}
