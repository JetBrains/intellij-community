// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * Internal utility class enabling the usage of VarHandle-like API in pre/after-jdk9 environments,
 * with the ability to switch the implementations dynamically.
 * It's used for porting classes containing VarHandles to jdk8-only modules, like util
 */
@ApiStatus.Internal
public abstract class VarHandleWrapper {
  protected static VarHandleWrapperFactory FACTORY;

  public static @NotNull VarHandleWrapperFactory getFactory() {
    VarHandleWrapperFactory factory = FACTORY;
    if (factory == null) {
      // try VarHandles first; if not available, use Unsafe
      try {
        Objects.requireNonNull(ReflectionUtil.getMethod(Class.forName("com.intellij.concurrency.VarHandleWrapperImpl"), "useVarHandlesInConcurrentCollections"))
          .invoke(null);
      }
      catch (InvocationTargetException | ClassNotFoundException e) {
        VarHandleWrapperUnsafe.useUnsafeInConcurrentCollections();
      }
      catch (IllegalAccessException e) {
        // signature was broken
        throw new RuntimeException(e);
      }
      factory = FACTORY;
    }
    return factory;
  }

  public interface VarHandleWrapperFactory {
    @NotNull VarHandleWrapper create(@NotNull Class<?> containingClass, @NotNull String name, @NotNull Class<?> type);
    @NotNull VarHandleWrapper createForArrayElement(@NotNull Class<?> arrayClass);
  }

  public abstract boolean compareAndSet(Object thisObject, Object expected, Object actual);
  public abstract boolean compareAndSetInt(Object thisObject, int expected, int actual);
  public abstract boolean compareAndSetLong(Object thisObject, long expected, long actual);

  public abstract Object getVolatileArrayElement(Object thisObject, int index);
  public abstract void setVolatileArrayElement(Object thisObject, int index, Object value);
  public abstract boolean compareAndSetArrayElement(Object thisObject, int index, Object expected, Object value);
}
