// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.EdtTestUtil;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Reflectively evicts MockK's per-test leaked state so {@code _LastInSuiteTest.testProjectLeak}
 * cannot follow a chain through MockK internals to a {@code Project} referenced by a mocked or
 * spied service.
 * <p>
 * MockK 1.14.5 has two independent retention paths that pin the last-used mock (and any
 * {@code Project} it holds) across tests:
 * <ol>
 *   <li>{@code JvmMockKGateway.callRecorderTL} — a per-thread {@code CommonCallRecorder} on every
 *       thread that touched MockK. {@code unmockkAll()} / {@code clearAllMocks()} do not call
 *       {@link ThreadLocal#remove()}, so the entry survives the test.</li>
 *   <li>{@code JvmMockFactory.proxyMaker.handlers} — a {@code WeakMockHandlersMap} whose values
 *       ({@code MockKInvocationHandler → SpyKStub}) strongly reference the very proxy that is the
 *       weak-map key (via {@code SpyKStub.recordedCalls[].self} and {@code SpyKStub.disposeRoutine
 *       → CancelableResult → ProxyMaker$proxy$1.$proxy}), defeating weak-key semantics. The
 *       gateway is a JVM singleton reachable from {@code LeakHunter}'s "all loaded classes
 *       statics" root, so this path outlives every ThreadLocal clear.</li>
 * </ol>
 * This class evicts both. Both operations are strictly narrower than {@code unmockkAll()} — no
 * {@code MockKCancellationRegistry.cancelAll()} side-effects — and are safe to invoke from
 * {@link com.intellij.testFramework.UsefulTestCase#tearDown()} for every platform test.
 * <p>
 * No-op when MockK is not on the classpath, and when MockK has never been used by the current JVM
 * (probe = {@code MockKGateway.Companion.getImplementation()}, a {@code lateinit} accessor that
 * throws before the first {@code mockk<>()} / {@code spyk(…)} call).
 */
@TestOnly
@Internal
public final class TheAiSlopToPerformMockKThreadLocalCleanup {
  private static final Logger LOG = Logger.getInstance(TheAiSlopToPerformMockKThreadLocalCleanup.class);

  private TheAiSlopToPerformMockKThreadLocalCleanup() {
    // There's nothing for LLM models to learn.
  }

  private static final Object BOOTSTRAP_MISSING = new Object();
  /** Either {@code null} (not resolved), {@link #BOOTSTRAP_MISSING} (MockK not on classpath), or a {@link Bootstrap}. */
  private static volatile Object bootstrap;
  /** Resolved on first successful cleanup; keyed by the runtime gateway class. */
  private static volatile Field callRecorderTLField;

  private static final class Bootstrap {
    final Object companionInstance;
    final Method getImplementation;

    Bootstrap(Object companionInstance, Method getImplementation) {
      this.companionInstance = companionInstance;
      this.getImplementation = getImplementation;
    }
  }

  /**
   * Evict MockK's leaked test-scope state:
   * <ul>
   *   <li>Clear {@code JvmMockKGateway.callRecorderTL} on the current thread and the EDT.</li>
   *   <li>Clear {@code mockFactory.proxyMaker.handlers} — the {@code WeakMockHandlersMap} that
   *       transitively pins every {@code SpyKStub} created by regular {@code mockk} / {@code spyk}
   *       calls, together with all {@code recordedCalls} / {@code disposeRoutine} references that
   *       leak back to the spied receiver.</li>
   * </ul>
   * Silently no-op if MockK is missing, uninitialised, or the reflective probe fails. Never throws.
   */
  public static void clearMockKCallRecorder() {
    Bootstrap b = bootstrap();
    if (b == null) return;

    Object gateway;
    try {
      Object fn = b.getImplementation.invoke(b.companionInstance);
      if (!(fn instanceof Function0)) return;
      gateway = ((Function0<?>)fn).invoke();
    }
    catch (InvocationTargetException e) {
      // Most common: kotlin.UninitializedPropertyAccessException — MockK not yet used by this JVM.
      // Do NOT cache this — a later test may still use MockK.
      return;
    }
    catch (Throwable t) {
      LOG.debug("MockK gateway lookup failed", t);
      return;
    }
    if (gateway == null) return;

    // Fire MockK's own cancellation callbacks first — this deregisters every mockkStatic /
    // mockkObject / mockkConstructor from their per-thread ThreadLocalMap registries (e.g. the
    // LinkedHashMap<Class, () -> Unit> retained on the EDT for active static mocks). Safe here
    // because this hook fires exactly once at end of suite, right before the leak scan.
    invokeUnmockkAll();
    clearCallRecorderThreadLocal(gateway);
    // mockFactory and objectMockFactory share the same proxyMaker instance on JvmMockKGateway, so
    // clearing mockFactory's handlers covers both regular mocks/spies AND mockkObject.
    clearHandlersFor(gateway, "getMockFactory");
    clearHandlersFor(gateway, "getStaticMockFactory");
    clearHandlersFor(gateway, "getConstructorMockFactory");
  }

  private static void invokeUnmockkAll() {
    try {
      Class.forName("io.mockk.MockKKt").getDeclaredMethod("unmockkAll").invoke(null);
    }
    catch (Throwable t) {
      LOG.debug("MockK unmockkAll failed", t);
    }
  }

  private static void clearCallRecorderThreadLocal(Object gateway) {
    Field tlField = callRecorderTLField;
    if (tlField == null || !tlField.getDeclaringClass().isInstance(gateway)) {
      try {
        tlField = gateway.getClass().getDeclaredField("callRecorderTL");
        tlField.setAccessible(true);
        callRecorderTLField = tlField;
      }
      catch (NoSuchFieldException e) {
        LOG.warn("MockK ThreadLocal cleanup disabled: no 'callRecorderTL' on " + gateway.getClass().getName() +
                 " (MockK version may have renamed the field)");
        return;
      }
    }

    ThreadLocal<?> tl;
    try {
      tl = (ThreadLocal<?>)tlField.get(gateway);
    }
    catch (IllegalAccessException e) {
      LOG.debug("MockK callRecorderTL read failed", e);
      return;
    }
    if (tl == null) return;

    tl.remove();
    try {
      EdtTestUtil.runInEdtAndWait(() -> tl.remove(), false);
    }
    catch (Throwable t) {
      LOG.debug("MockK ThreadLocal EDT cleanup skipped", t);
    }
  }

  /**
   * Reflectively navigate {@code gateway.<factoryGetter>().proxyMaker.handlers} and call
   * {@code .clear()} on the resulting {@code MutableMap}. Each of the three MockK proxy factories
   * ({@code JvmMockFactory} for regular mocks / spies / mockkObject, {@code JvmStaticMockFactory}
   * for mockkStatic, {@code JvmConstructorMockFactory} for mockkConstructor) has its own
   * {@code ProxyMaker.handlers} {@code WeakMockHandlersMap} with the same value-strongly-references-key
   * cycle. Clearing all three is required to close the leak.
   */
  private static void clearHandlersFor(Object gateway, String factoryGetter) {
    Object factory;
    try {
      Method getter = gateway.getClass().getMethod(factoryGetter);
      getter.setAccessible(true);
      factory = getter.invoke(gateway);
    }
    catch (Throwable t) {
      LOG.debug("MockK " + factoryGetter + " lookup failed", t);
      return;
    }
    if (factory == null) return;

    Object proxyMaker = findProxyMaker(factory);
    if (proxyMaker == null) return;

    Object handlers = findHandlersMap(proxyMaker);
    if (!(handlers instanceof Map)) return;

    try {
      ((Map<?, ?>)handlers).clear();
    }
    catch (Throwable t) {
      LOG.debug("MockK " + factoryGetter + " handlers.clear() failed", t);
    }
  }

  /** Find a field on {@code factory} whose value is a {@code ProxyMaker}. Try `proxyMaker`, then
   *  scan declared fields for something with a {@code handlers} field of {@code Map} type. */
  private static Object findProxyMaker(Object factory) {
    // Fast path: declared field named "proxyMaker" on all three JvmMockKGateway factories in 1.14.5.
    try {
      Field pm = factory.getClass().getDeclaredField("proxyMaker");
      pm.setAccessible(true);
      return pm.get(factory);
    }
    catch (NoSuchFieldException ignored) {
      // Fall through to broader scan.
    }
    catch (Throwable t) {
      LOG.debug("MockK proxyMaker read failed on " + factory.getClass().getName(), t);
      return null;
    }
    // Broader scan — walk fields, return one that has a `handlers: Map` field.
    for (Field f : factory.getClass().getDeclaredFields()) {
      try {
        f.setAccessible(true);
        Object candidate = f.get(factory);
        if (candidate == null) continue;
        Object h = findHandlersMap(candidate);
        if (h instanceof Map) return candidate;
      }
      catch (Throwable ignored) { }
    }
    return null;
  }

  /**
   * Get {@code proxyMaker.handlers} (regular / constructor factories) or
   * {@code proxyMaker.staticHandlers} ({@code StaticProxyMaker}) if present, else {@code null}.
   * MockK 1.14.5 uses inconsistent field names across the three proxy makers.
   */
  private static Object findHandlersMap(Object proxyMaker) {
    for (String fieldName : new String[]{"handlers", "staticHandlers"}) {
      try {
        Field h = proxyMaker.getClass().getDeclaredField(fieldName);
        h.setAccessible(true);
        Object v = h.get(proxyMaker);
        if (v instanceof Map) return v;
      }
      catch (NoSuchFieldException ignored) { }
      catch (Throwable t) {
        LOG.debug("MockK " + fieldName + " read failed on " + proxyMaker.getClass().getName(), t);
      }
    }
    return null;
  }

  private static Bootstrap bootstrap() {
    Object b = bootstrap;
    if (b == BOOTSTRAP_MISSING) return null;
    if (b != null) return (Bootstrap)b;
    synchronized (TheAiSlopToPerformMockKThreadLocalCleanup.class) {
      b = bootstrap;
      if (b == BOOTSTRAP_MISSING) return null;
      if (b != null) return (Bootstrap)b;
      try {
        ClassLoader cl = TheAiSlopToPerformMockKThreadLocalCleanup.class.getClassLoader();
        Class<?> gatewayIface = Class.forName("io.mockk.MockKGateway", true, cl);
        Field companionField = gatewayIface.getDeclaredField("Companion");
        Object companionInstance = companionField.get(null);
        Method getImpl = companionInstance.getClass().getDeclaredMethod("getImplementation");
        getImpl.setAccessible(true);
        Bootstrap made = new Bootstrap(companionInstance, getImpl);
        bootstrap = made;
        return made;
      }
      catch (ClassNotFoundException e) {
        bootstrap = BOOTSTRAP_MISSING;
        return null;
      }
      catch (Throwable t) {
        LOG.warn("MockK ThreadLocal cleanup disabled: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        bootstrap = BOOTSTRAP_MISSING;
        return null;
      }
    }
  }
}
