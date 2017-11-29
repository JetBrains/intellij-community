/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.containers.WeakInterner;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;

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
public class ThrowableInterner {
  private static final WeakInterner<Throwable> myTraceInterner = new WeakInterner<Throwable>(new TObjectHashingStrategy<Throwable>() {
    @Override
    public int computeHashCode(Throwable throwable) {
      String message = throwable.getMessage();
      if (message != null) {
        return message.hashCode();
      }
      Object[] backtrace = getBacktrace(throwable);
      if (backtrace != null) {
        Object[] stack = (Object[])ContainerUtil.find(backtrace, FilteringIterator.instanceOf(Object[].class));
        return Arrays.hashCode(stack);
      }
      return Arrays.hashCode(throwable.getStackTrace());
    }

    @Override
    public boolean equals(Throwable o1, Throwable o2) {
      if (o1 == o2) return true;
      if (o1 == null || o2 == null) return false;

      if (!Comparing.equal(o1.getClass(), o2.getClass())) return false;
      if (!Comparing.equal(o1.getMessage(), o2.getMessage())) return false;
      if (!equals(o1.getCause(), o2.getCause())) return false;
      Object[] backtrace1 = getBacktrace(o1);
      Object[] backtrace2 = getBacktrace(o2);
      if (backtrace1 != null && backtrace2 != null) {
        return Arrays.deepEquals(backtrace1, backtrace2);
      }
      return Arrays.equals(o1.getStackTrace(), o2.getStackTrace());
    }
  });

  private static final Field BACKTRACE_FIELD;
  // can be UNKNOWN if the memory layout or JDK is unknown or ancient so we skip interning altogether, (e.g. jdk <=6)
  // or LUCKILY_NOT_NEEDED when the JDK supports reflection to the "backtrace" field (e.g. jdk 9)
  // or the real offset of the "backtrace" field into the Throwable class (for jdk 7 and 8)
  private static final int BACKTRACE_FIELD_OFFSET;
  private static final int UNKNOWN = -1;
  private static final int LUCKILY_NOT_NEEDED = -2;

  static {
    BACKTRACE_FIELD = ReflectionUtil.getDeclaredField(Throwable.class, "backtrace");
    if (BACKTRACE_FIELD != null) {
      BACKTRACE_FIELD_OFFSET = LUCKILY_NOT_NEEDED;
    }
    else if ((SystemInfo.isOracleJvm || SystemInfo.isJetBrainsJvm) && SystemInfo.isJavaVersionAtLeast("1.7")) {
      Field firstField = Throwable.class.getDeclaredFields()[1];
      long firstFieldOffset = AtomicFieldUpdater.getUnsafe().objectFieldOffset(firstField);
      BACKTRACE_FIELD_OFFSET = firstFieldOffset == 12 ? 8 : firstFieldOffset == 16 ? 12 : firstFieldOffset == 24 ? 16 : UNKNOWN;
      if (BACKTRACE_FIELD_OFFSET == UNKNOWN
          || !firstField.getName().equals("detailMessage")
          || !(AtomicFieldUpdater.getUnsafe().getObject(new Throwable(), (long)BACKTRACE_FIELD_OFFSET) instanceof Object[])) {
        throw new RuntimeException("Unknown layout: "+firstField+";"+firstFieldOffset+". Please specify -Didea.disposer.debug=off in idea.properties to suppress");
      }
    }
    else {
      BACKTRACE_FIELD_OFFSET = UNKNOWN;
    }
  }

  private static Object[] getBacktrace(@NotNull Throwable throwable) {
    // the JVM blocks access to Throwable.backtrace via reflection
    Object backtrace;
    try {
      backtrace = BACKTRACE_FIELD != null ? BACKTRACE_FIELD.get(throwable) :
                  BACKTRACE_FIELD_OFFSET == UNKNOWN ? null :
                  AtomicFieldUpdater.getUnsafe().getObject(throwable, (long)BACKTRACE_FIELD_OFFSET);
    }
    catch (IllegalAccessException e) {
      return null;
    }
    // obsolete jdk
    return backtrace instanceof Object[] && ((Object[])backtrace).length == 5 ? (Object[])backtrace : null;
  }

  @NotNull
  public static Throwable intern(@NotNull Throwable throwable) {
    return getBacktrace(throwable) == null ? throwable : myTraceInterner.intern(throwable);
  }
}
