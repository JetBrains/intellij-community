// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.containers.WeakInterner;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;

/**
 * Please don't look, there's nothing interesting here.
 *
 *
 *
 *
 * If you insist, JVM stores stacktrace information in compact form in Throwable.backtrace field, but blocks reflective access to this field.
 * This class uses this field for comparing Throwables.
 * The available method Throwable.getStackTrace() unfortunately can't be used for that because it's
 * 1) too slow and 2) explodes Throwable retained size by polluting Throwable.stackTrace fields.
 */
@ApiStatus.Internal
public final class ThrowableInterner {
  private static final Interner<Throwable> myTraceInterner = new WeakInterner<>(new TObjectHashingStrategy<Throwable>() {
    @Override
    public int computeHashCode(Throwable throwable) {
      return ThrowableInterner.computeHashCode(throwable);
    }

    @Override
    public boolean equals(Throwable o1, Throwable o2) {
      if (o1 == o2) return true;
      if (o1 == null || o2 == null) return false;

      if (!Comparing.equal(o1.getClass(), o2.getClass())) return false;
      if (!Objects.equals(o1.getMessage(), o2.getMessage())) return false;
      if (!equals(o1.getCause(), o2.getCause())) return false;
      Object[] backtrace1 = getBacktrace(o1);
      Object[] backtrace2 = getBacktrace(o2);
      if (backtrace1 != null && backtrace2 != null) {
        return Arrays.deepEquals(backtrace1, backtrace2);
      }
      return Arrays.equals(o1.getStackTrace(), o2.getStackTrace());
    }
  });

  public static int computeHashCode(@NotNull Throwable throwable) {
    String message = throwable.getMessage();
    if (message != null) {
      return message.hashCode();
    }
    return computeTraceHashCode(throwable);
  }

  public static int computeTraceHashCode(@NotNull Throwable throwable) {
    Object[] backtrace = getBacktrace(throwable);
    if (backtrace != null) {
      Object[] stack = ContainerUtil.findInstance(backtrace, Object[].class);
      return Arrays.hashCode(stack);
    }
    return Arrays.hashCode(throwable.getStackTrace());
  }

  private static final Field BACKTRACE_FIELD;

  static {
    BACKTRACE_FIELD = ReflectionUtil.getDeclaredField(Throwable.class, "backtrace");
  }

  private static Object[] getBacktrace(@NotNull Throwable throwable) {
    // the JVM blocks access to Throwable.backtrace via reflection sometimes
    Object backtrace;
    try {
      backtrace = BACKTRACE_FIELD != null ? BACKTRACE_FIELD.get(throwable) : null;
    }
    catch (Throwable e) {
      return null;
    }
    // obsolete jdk
    return backtrace instanceof Object[] ? (Object[])backtrace : null;
  }

  public static void clearBacktrace(@NotNull Throwable throwable) {
    try {
      throwable.setStackTrace(new StackTraceElement[0]);
      if (BACKTRACE_FIELD != null) {
        BACKTRACE_FIELD.set(throwable, null);
      }
    }
    catch (Throwable e) {
      //noinspection ConstantConditions
      ExceptionUtil.rethrowAllAsUnchecked(e);
    }
  }

  @NotNull
  public static Throwable intern(@NotNull Throwable throwable) {
    return getBacktrace(throwable) == null ? throwable : myTraceInterner.intern(throwable);
  }

  public static void clearInternedBacktraces() {
    for (Throwable t : myTraceInterner.getValues()) {
      clearBacktrace(t);
    }
    myTraceInterner.clear();
  }
}
