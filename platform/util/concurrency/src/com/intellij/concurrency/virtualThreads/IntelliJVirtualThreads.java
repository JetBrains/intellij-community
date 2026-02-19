// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency.virtualThreads;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jetbrains.annotations.ApiStatus;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.concurrent.Executor;

/**
 * IntelliJ Platform adapters for virtual threads.
 */
@ApiStatus.Experimental
public final class IntelliJVirtualThreads {

  private IntelliJVirtualThreads() { }

  /**
   * By default, virtual threads run on top of Fork-Join Pool.
   * We use coroutine scheduler as the main scheduler of IntelliJ Platform, so we need to replace FJP in default constructors of virtual thread factories.
   * <p>
   * Until JDK gets an API for setting custom executors, we use reflection.
   */
  private static final MethodHandle virtualThreadBuilderConstructor;

  static {
    MethodHandle handle;
    try {
      Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
      Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
      ctor.setAccessible(true);
      handle = MethodHandles.lookup().unreflectConstructor(ctor);
    }
    catch (Throwable e) {
      handle = null;
    }
    virtualThreadBuilderConstructor = handle;
  }

  private static Thread.Builder getVirtualBuilder() {
    if (virtualThreadBuilderConstructor == null) {
      return Thread.ofVirtual();
    }
    try {
      Executor executor = ExecutorsKt.asExecutor(Dispatchers.getDefault());
      return (Thread.Builder)virtualThreadBuilderConstructor.invoke(executor);
    }
    catch (Throwable e) {
      return Thread.ofVirtual();
    }
  }

  /**
   * Returns a virtual thread builder.
   * <p>
   * This method is preferable to {@link Thread#ofVirtual()}, as it allows IntelliJ Platform to perform modifications to virtual threads.
   */
  public static Thread.Builder ofVirtual() {
    return getVirtualBuilder();
  }
}
