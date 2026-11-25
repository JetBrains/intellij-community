// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Method;

/**
 * Evicts Mockito's per-thread state so {@code _LastInSuiteTest.testProjectLeak} cannot follow a
 * chain through Mockito internals to a {@code Project} referenced by an argument of a stubbed call.
 * <p>
 * Mockito is invoked purely via reflection here so that
 * {@code intellij.platform.testFramework.common} does not need to depend on the mockito lib.
 * <p>
 * The cleanup does nothing in a JVM that did not use Mockito, and it loads no Mockito class there.
 * A call of {@code Mockito.framework()} starts the inline mock maker: about 1 100 classes and an agent attach.
 **/
@TestOnly
@Internal
public final class MockitoThreadLocalCleanup {
  private static final Logger LOG = Logger.getInstance(MockitoThreadLocalCleanup.class);

  private MockitoThreadLocalCleanup() { }

  /**
   * The holder of the per-thread state of Mockito. Each mock, stub and verification loads it.
   * If no class loader loaded it, this JVM has no Mockito state to clear.
   */
  private static final String MOCKING_PROGRESS_CLASS = "org.mockito.internal.progress.ThreadSafeMockingProgress";

  private static final Object BOOTSTRAP_MISSING = new Object();
  /** Either {@code null} (not resolved), {@link #BOOTSTRAP_MISSING} (Mockito not on classpath), or a {@link Bootstrap}. */
  private static volatile Object bootstrap;
  /** Resolved lazily on first successful {@code framework().clearInlineMocks()} call; keyed by the runtime framework class. */
  private static volatile Method clearInlineMocksMethod;

  private static final class Bootstrap {
    /** {@code Mockito.reset(T...)} — erased to {@code reset(Object[])}. */
    final Method resetMethod;
    /** {@code Mockito.framework()} returning {@code MockitoFramework}. */
    final Method frameworkMethod;

    Bootstrap(Method resetMethod, Method frameworkMethod) {
      this.resetMethod = resetMethod;
      this.frameworkMethod = frameworkMethod;
    }
  }

  /**
   * Evict Mockito's leaked test-scope state. Silently no-op on any failure. Never throws.
   */
  public static void clearMockitoState() {
    if (!isMockitoUsed()) return;
    Bootstrap b = bootstrap();
    if (b == null) return;

    resetMockingProgress(b);
    try {
      EdtTestUtil.runInEdtAndWait(() -> resetMockingProgress(b), false);
    }
    catch (Throwable t) {
      LOG.debug("Mockito reset on EDT skipped", t);
    }
    try {
      Object framework = b.frameworkMethod.invoke(null);
      if (framework != null) {
        Method clear = clearInlineMocksMethod;
        if (clear == null || !clear.getDeclaringClass().isInstance(framework)) {
          clear = framework.getClass().getMethod("clearInlineMocks");
          clearInlineMocksMethod = clear;
        }
        clear.invoke(framework);
      }
    }
    catch (Throwable t) {
      LOG.debug("Mockito.framework().clearInlineMocks() failed", t);
    }
  }

  /**
   * Checks the class loader of this class and its parents for {@link #MOCKING_PROGRESS_CLASS}, and loads no class for it.
   * A class loader that is not a {@link UrlClassLoader} cannot tell this, so the method returns {@code true} for it.
   */
  private static boolean isMockitoUsed() {
    ClassLoader platformClassLoader = ClassLoader.getPlatformClassLoader();
    for (var cl = MockitoThreadLocalCleanup.class.getClassLoader(); cl != null && cl != platformClassLoader; cl = cl.getParent()) {
      if (!(cl instanceof UrlClassLoader urlClassLoader) || urlClassLoader.hasLoadedClass(MOCKING_PROGRESS_CLASS)) {
        return true;
      }
    }
    return false;
  }

  private static void resetMockingProgress(Bootstrap b) {
    try {
      // Mockito.reset(T... mocks) — empty varargs; cast is required so the array is not unpacked.
      b.resetMethod.invoke(null, (Object)ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    }
    catch (Throwable t) {
      LOG.debug("Mockito.reset() failed on " + Thread.currentThread().getName(), t);
    }
  }

  private static Bootstrap bootstrap() {
    Object b = bootstrap;
    if (b == BOOTSTRAP_MISSING) return null;
    if (b != null) return (Bootstrap)b;
    synchronized (MockitoThreadLocalCleanup.class) {
      b = bootstrap;
      if (b == BOOTSTRAP_MISSING) return null;
      if (b != null) return (Bootstrap)b;
      try {
        ClassLoader cl = MockitoThreadLocalCleanup.class.getClassLoader();
        Class<?> mockitoClass = Class.forName("org.mockito.Mockito", true, cl);
        Method resetMethod = mockitoClass.getMethod("reset", Object[].class);
        Method frameworkMethod = mockitoClass.getMethod("framework");
        Bootstrap made = new Bootstrap(resetMethod, frameworkMethod);
        bootstrap = made;
        return made;
      }
      catch (ClassNotFoundException e) {
        bootstrap = BOOTSTRAP_MISSING;
        return null;
      }
      catch (Throwable t) {
        LOG.warn("Mockito ThreadLocal cleanup disabled: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        bootstrap = BOOTSTRAP_MISSING;
        return null;
      }
    }
  }
}
