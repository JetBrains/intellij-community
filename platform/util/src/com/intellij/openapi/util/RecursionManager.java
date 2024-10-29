// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A utility to prevent endless recursion and ensure the caching returns stable results if such endless recursion is prevented.
 * Should be used only as a last resort, when it's impossible to detect endless recursion without using thread-local state.<p></p>
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
 * whatever is calculated first. In a multi-threaded environment, that'd lead to unpredictability.<p></p>
 *
 * Of the 3 possible scenarios above, caching for {@code C()} makes sense only for the last one, because that's the result we'd get if there were no caching at all.
 * Therefore, if you use any kind of caching in an endless-recursion-prone environment, please ensure you don't cache incomplete results
 * that happen when you're inside the evil recursion loop.
 * {@code RecursionManager} assists in distinguishing this situation and allowing caching outside that loop, but disallowing it inside.<p></p>
 *
 * To prevent caching incorrect values, please create a {@code private static final} field of {@link #createGuard} call, and then use
 * {@link RecursionManager#markStack()} and {@link RecursionGuard.StackStamp#mayCacheNow()}
 * on it.<p></p>
 *
 * Note that the above only helps with idempotent recursion loops, that is, the ones that stabilize after one iteration, so that
 * {@code A()->B()->C()->null} returns the same value as {@code A()->B()->C()->A()->B()->C()->null} etc. If your functions lack that quality
 * (e.g. if they add items to some list), you won't get stable caching results ever, and your code will produce unpredictable results
 * with hard-to-catch bugs. Therefore, please strive for idempotence.
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public final class RecursionManager {
  private static final Logger LOG = Logger.getInstance(RecursionManager.class);
  private static final ThreadLocal<CalculationStack> ourStack = ThreadLocal.withInitial(() -> new CalculationStack());
  private static final AtomicBoolean ourAssertOnPrevention = new AtomicBoolean();
  private static final AtomicBoolean ourAssertOnMissedCache = new AtomicBoolean();

  /**
   * Run the given computation, unless it's already running in this thread.
   * This is same as {@link RecursionGuard#doPreventingRecursion(Object, boolean, Computable)},
   * without a need to bother to create {@link RecursionGuard}.
   */
  public static @Nullable <T> T doPreventingRecursion(@NotNull Object key, boolean memoize, Computable<T> computation) {
    return createGuard(computation.getClass().getName()).doPreventingRecursion(key, memoize, computation);
  }

  /**
   * @param id just some string to separate different recursion prevention policies from each other
   * @return a helper object which allows you to perform reentrancy-safe computations and check whether caching will be safe.
   * Don't use it unless you need to call it from several places in the code, inspect the computation stack and/or prohibit result caching.
   */
  public static @NotNull <Key> RecursionGuard<Key> createGuard(final @NonNls String id) {
    return new RecursionGuard<Key>() {
      @Override
      public <T, E extends Throwable> @Nullable T computePreventingRecursion(@NotNull Key key,
                                                                             boolean memoize,
                                                                             @NotNull ThrowableComputable<T, E> computation) throws E{
        MyKey realKey = new MyKey(id, key, true);
        final CalculationStack stack = ourStack.get();

        if (stack.checkReentrancy(realKey)) {
          if (ourAssertOnPrevention.get()) {
            throw new StackOverflowPreventedException("Endless recursion prevention occurred on " + key);
          }
          else if (LOG.isDebugEnabled()) {
            LOG.debug(new StackOverflowPreventedException("Endless recursion prevention occurred on " + key));
          }
          return null;
        }

        if (memoize) {
          MemoizedValue memoized = stack.intermediateCache.get(realKey);
          if (memoized != null) {
            for (MyKey noCacheUntil : memoized.dependencies) {
              stack.prohibitResultCaching(noCacheUntil);
            }
            //noinspection unchecked
            return (T)memoized.value;
          }
        }

        realKey = new MyKey(id, key, false);

        final int sizeBefore = stack.progressMap.size();
        StackFrame frame = stack.beforeComputation(realKey);
        final int sizeAfter = stack.progressMap.size();

        try {
          T result = computation.compute();

          if (memoize && frame.preventionsInside != null) {
            stack.memoize(realKey, result, frame.preventionsInside);
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

      @Override
      public @NotNull List<Key> currentStack() {
        List<Key> result = new ArrayList<>();
        for (MyKey pair : ourStack.get().progressMap.keySet()) {
          if (pair.guardId.equals(id)) {
            //noinspection unchecked
            result.add((Key)pair.userObject);
          }
        }
        return Collections.unmodifiableList(result);
      }

      @Override
      public void prohibitResultCaching(@NotNull Object since) {
        MyKey realKey = new MyKey(id, since, false);
        final CalculationStack stack = ourStack.get();
        stack.prohibitResultCaching(realKey);
      }

    };
  }

  /**
   * Clears the memoization cache for the current thread. This can be invoked when a side effect happens
   * inside a {@link #doPreventingRecursion} call that may affect results of nested memoizing {@code doPreventingRecursion} calls,
   * whose memoized results should not be reused on that point.<p></p>
   *
   * Please avoid this method at all cost and try to restructure your code to avoid side effects inside {@code doPreventingRecursion}.
   */
  @ApiStatus.Internal
  public static void dropCurrentMemoizationCache() {
    ourStack.get().intermediateCache.clear();
  }

  /**
   * Used in pair with {@link RecursionGuard.StackStamp#mayCacheNow()} to ensure that cached are only the reliable values,
   * not depending on anything incomplete due to recursive prevention policies.
   * A typical usage is this:
   * <pre>{@code
   *   RecursionGuard.StackStamp stamp = RecursionManager.createGuard("id").markStack();
   *
   *   Result result = doComputation();
   *
   *   if (stamp.mayCacheNow()) {
   *     cache(result);
   *   }
   *   return result;
   * }</pre>
   * @return an object representing the current stack state, managed by {@link RecursionManager}
   */
  public static @NotNull RecursionGuard.StackStamp markStack() {
    int stamp = ourStack.get().reentrancyCount;
    return new RecursionGuard.StackStamp() {
      @Override
      public boolean mayCacheNow() {
        CalculationStack stack = ourStack.get();
        boolean result = stamp == stack.reentrancyCount;
        if (!result && ourAssertOnMissedCache.get() && !stack.isCurrentNonCachingStillTolerated()) {
          throw new CachingPreventedException(stack.preventions);
        }
        return result;
      }
    };
  }

  /**
   * Runs computation in a new context in the current thread, without any previously set guards.
   */
  @ApiStatus.Internal
  @IntellijInternalApi
  public static void runInNewContext(@NotNull Runnable runnable) {
    CalculationStack currentContext = ourStack.get();
    try {
      ourStack.set(new CalculationStack());
      runnable.run();
    }
    finally {
      ourStack.set(currentContext);
    }
  }


  private static final class MyKey {
    final String guardId;
    final Object userObject;
    private final int myHashCode;
    private final boolean myCallEquals;

    MyKey(String guardId, @NotNull Object userObject, boolean mayCallEquals) {
      this.guardId = guardId;
      this.userObject = userObject;
      LOG.assertTrue(!userObject.getClass().isArray(), "Arrays use the default hashCode/equals implementation");
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

    @Override
    public String toString() {
      return guardId + "->" + userObject;
    }
  }

  private static final class CalculationStack {
    private int reentrancyCount;
    private int depth;
    private int firstLoopStart = Integer.MAX_VALUE; // outermost recursion-prevented frame depth; memoized values are dropped on its change.
    private final LinkedHashMap<MyKey, StackFrame> progressMap = new LinkedHashMap<>();
    private final Map<MyKey, Throwable> preventions = new IdentityHashMap<>();
    private final Map<@NotNull MyKey, MemoizedValue> intermediateCache = CollectionFactory.createSoftKeySoftValueMap();
    private int enters;
    private int exits;

    boolean checkReentrancy(MyKey realKey) {
      if (progressMap.containsKey(realKey)) {
        prohibitResultCaching(realKey);
        return true;
      }
      return false;
    }

    StackFrame beforeComputation(MyKey realKey) {
      enters++;

      if (progressMap.isEmpty()) {
        assert reentrancyCount == 0 : "Non-zero stamp with empty stack: " + reentrancyCount;
      }

      checkDepth("1");

      int sizeBefore = progressMap.size();
      StackFrame frame = new StackFrame();
      frame.reentrancyStamp = reentrancyCount;
      progressMap.put(realKey, frame);
      depth++;

      checkDepth("2");

      int sizeAfter = progressMap.size();
      if (sizeAfter != sizeBefore + 1) {
        LOG.error("Key doesn't lead to the map size increase: " + sizeBefore + " " + sizeAfter + " " + realKey.userObject);
      }
      return frame;
    }

    void memoize(@NotNull MyKey key, @Nullable Object result, @NotNull Set<MyKey> preventionsInside) {
      intermediateCache.put(key, new MemoizedValue(result, preventionsInside.toArray(new MyKey[0])));
    }

    void afterComputation(MyKey realKey, int sizeBefore, int sizeAfter) {
      exits++;
      if (sizeAfter != progressMap.size()) {
        LOG.error("Map size changed: " + progressMap.size() + " " + sizeAfter + " " + realKey.userObject);
      }

      if (depth != progressMap.size()) {
        LOG.error("Inconsistent depth after computation; depth=" + depth + "; map=" + progressMap);
      }

      StackFrame value = progressMap.remove(realKey);
      depth--;
      if (!preventions.isEmpty()) {
        preventions.remove(realKey);
      }

      if (depth <= firstLoopStart) {
        firstLoopStart = Integer.MAX_VALUE;
        intermediateCache.clear();
      }

      if (sizeBefore != progressMap.size()) {
        LOG.error("Map size doesn't decrease: " + progressMap.size() + " " + sizeBefore + " " + realKey.userObject);
      }

      reentrancyCount = value.reentrancyStamp;
    }

    private void prohibitResultCaching(MyKey realKey) {
      reentrancyCount++;

      List<Map.Entry<MyKey, StackFrame>> stack = new ArrayList<>(progressMap.entrySet());
      int loopStart = ContainerUtil.indexOf(stack, entry -> entry.getKey().equals(realKey));
      if (loopStart >= 0) {
        MyKey loopStartKey = stack.get(loopStart).getKey();
        if (!preventions.containsKey(loopStartKey)) {
          preventions.put(loopStartKey, ourAssertOnMissedCache.get() ? new StackOverflowPreventedException(null) : null);
        }
        for (int i = loopStart + 1; i < stack.size(); i++) {
          stack.get(i).getValue().addPrevention(reentrancyCount, loopStartKey);
        }
        if (LOG.isDebugEnabled() && loopStart < stack.size() - 1) {
          LOG.debug("Recursion prevented for " + realKey +
                    ", caching disabled for " + ContainerUtil.map(stack.subList(loopStart, stack.size()), Map.Entry::getKey));
        }
        if (firstLoopStart > loopStart) {
          firstLoopStart = loopStart;
          intermediateCache.clear();
        }
      }
    }

    private void checkDepth(String s) {
      int oldDepth = depth;
      if (oldDepth != progressMap.size()) {
        depth = progressMap.size();
        throw new AssertionError("_Inconsistent depth " + s + "; depth=" + oldDepth + "; enters=" + enters + "; exits=" + exits + "; map=" + progressMap);
      }
    }

    /**
     * Rules in this method correspond to bugs that should be fixed but for some reasons that can't be done immediately.
     * The ultimate goal is to get rid of all of them.
     * So, each rule should be accompanied by a reference to a tracker issue.
     * Don't add rules here without discussing them with someone else.
     * Don't add rules for situations where caching prevention is expected, use {@link #disableMissedCacheAssertions} instead.
     */
    boolean isCurrentNonCachingStillTolerated() {
      return isCurrentNonCachingStillTolerated(new Throwable()) ||
             ContainerUtil.exists(preventions.values(), CalculationStack::isCurrentNonCachingStillTolerated);
    }

    private static boolean isCurrentNonCachingStillTolerated(Throwable t) {
      String trace = ExceptionUtil.getThrowableText(t);
      return ContainerUtil.exists(toleratedFrames, trace::contains);
    }
  }

  private static final @NonNls String[] toleratedFrames = {
    "com.intellij.psi.impl.source.xml.XmlAttributeImpl.getDescriptor(", // IDEA-228451
    "org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.util.SymbolHierarchy.getAncestorsCaching(", // RUBY-25487
    "com.intellij.lang.aspectj.psi.impl.PsiInterTypeReferenceImpl.", // IDEA-228779
    "com.intellij.psi.impl.search.JavaDirectInheritorsSearcher.processConcurrentlyIfTooMany(", // IDEA-229003

    // WEB-42912
    "com.intellij.lang.javascript.psi.resolve.JSEvaluatorComplexityTracker.doRunTask(",
    "com.intellij.lang.javascript.ecmascript6.types.JSTypeSignatureChooser.chooseOverload(",
    "com.intellij.lang.javascript.psi.resolve.JSEvaluationCache.getElementType(",
    "com.intellij.lang.ecmascript6.psi.impl.ES6ImportSpecifierImpl.multiResolve(",
    "com.intellij.lang.javascript.psi.types.JSTypeBaseImpl.substitute(",

    // IDEA-228814
    "com.intellij.psi.ThreadLocalTypes.performWithTypes(",

    // IDEA-212671
    "com.intellij.xml.impl.schema.XmlNSDescriptorImpl.getRedefinedElementDescriptor(",
    "com.intellij.psi.impl.source.xml.XmlTagImpl.getDescriptor(",
    "com.intellij.psi.impl.source.xml.XmlTagDelegate.getNSDescriptor(",
    "com.intellij.xml.impl.schema.XmlNSDescriptorImpl.findTypeDescriptor(",
    "com.intellij.psi.impl.source.xml.XmlEntityRefImpl.doResolveEntity(",
    "com.intellij.xml.impl.dtd.XmlNSDescriptorImpl.getElementDescriptor(",

    // PY-39529
    "com.jetbrains.python.psi.PyKnownDecoratorUtil.resolveDecorator(",
    "com.jetbrains.python.psi.impl.references.PyReferenceImpl.multiResolve(",
  };

  private static final class MemoizedValue {
    final Object value;
    final MyKey[] dependencies;

    MemoizedValue(Object value, MyKey[] dependencies) {
      this.value = value;
      this.dependencies = dependencies;
    }
  }

  private static final class StackFrame {
    int reentrancyStamp;
    @Nullable Set<MyKey> preventionsInside;

    void addPrevention(int stamp, MyKey prevented) {
      reentrancyStamp = stamp;
      if (preventionsInside == null) {
        preventionsInside = new HashSet<>();
      }
      preventionsInside.add(prevented);
    }
  }

  @TestOnly
  public static void assertOnRecursionPrevention(@NotNull Disposable parentDisposable) {
    setFlag(parentDisposable, true, ourAssertOnPrevention);
  }

  private static void setFlag(@NotNull Disposable parentDisposable, boolean toAssert, AtomicBoolean flag) {
    boolean prev = flag.get();
    if (toAssert == prev) return;

    flag.set(toAssert);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        if (flag.get() != toAssert) {
          throw new IllegalStateException("Non-nested assertion flag modifications");
        }
        flag.set(prev);
      }
    });
  }

  @TestOnly
  public static void disableAssertOnRecursionPrevention(@NotNull Disposable parentDisposable) {
    setFlag(parentDisposable, false, ourAssertOnPrevention);
  }

  /**
   * Disables the effect of {@link #assertOnMissedCache}. Should be used as rarely as possible, ideally only in tests that check
   * that the stack isn't overflown on invalid code.
   */
  @TestOnly
  public static void disableMissedCacheAssertions(@NotNull Disposable parentDisposable) {
    setFlag(parentDisposable, false, ourAssertOnMissedCache);
  }

  /**
   * Enable the mode when a {@link CachingPreventedException} is thrown whenever
   * {@link RecursionGuard.StackStamp#mayCacheNow()} returns false,
   * either due to recursion prevention or explicit {@link RecursionGuard#prohibitResultCaching} call.
   * Restore previous mode when parentDisposable is disposed.
   */
  @TestOnly
  public static void assertOnMissedCache(@NotNull Disposable parentDisposable) {
    setFlag(parentDisposable, true, ourAssertOnMissedCache);
  }

  /**
   * If this exception happened to you, this means that your caching might be suboptimal due to endless recursion, and you'd better fix that.
   * The exception's cause might help, as it contains a full stack trace of the endless recursion prevention that has led to this caching prohibition.
   * <p></p>
   * Try to get rid of the cyclic dependency. Often it's easy: look carefully at the stack trace, put breakpoints, look at the values passed,
   * and find something that shouldn't be called there. There's almost always such something. Remove it and voila, you've got rid of the cycle,
   * improved caching and also avoided calling unnecessary code which can speed up the execution even in unrelated circumstances.
   * <p></p>
   * What not to do: don't replace {@code RecursionManager} with your own thread-local,
   * don't just remove {@code mayCacheNow} checks and cache despite them.
   * Both approaches will likely result in test & production flakiness and {@code IdempotenceChecker} assertions.
   * <p></p>
   * There are rare cases when recursion prevention is acceptable. They might involve analyzing very incorrect code or
   * independent plugins calling into each other. This should be an exotic situation, not a normal workflow.
   * In this case, you may call {@link #disableMissedCacheAssertions} in the tests
   * which check such exotic situations.
   */
  static final class CachingPreventedException extends RuntimeException {
    CachingPreventedException(Map<MyKey, Throwable> preventions) {
      super("Caching disabled due to recursion prevention, please get rid of cyclic dependencies. Preventions: "
            + new ArrayList<>(preventions.keySet()) + getPreventionStackTrace(preventions),
            ContainerUtil.getFirstItem(preventions.values()));
    }
  }

  private static String getPreventionStackTrace(Map<MyKey, Throwable> preventions) {
    Throwable prevention = ContainerUtil.getFirstItem(preventions.values());
    if (prevention == null) return "";

    StringWriter writer = new StringWriter();
    prevention.printStackTrace(new PrintWriter(writer));
    return "\nCaused by: " + writer;
  }
}
