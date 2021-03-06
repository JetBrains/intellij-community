// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.objectTree.ThrowableInterner;
import org.junit.Test;

import static junit.framework.TestCase.*;

public class ThrowableInternerTest {
  @Test
  public void testItDoesWork() {
    Throwable t1 = new Throwable();
    assertStackTraceIsExpanded(t1, false);
    Throwable i1 = ThrowableInterner.intern(t1);
    assertStackTraceIsExpanded(t1, false);
    assertSame(i1, ThrowableInterner.intern(t1));
    assertStackTraceIsExpanded(t1, false);
    assertNotSame(i1, ThrowableInterner.intern(new Throwable()));

    // must have exactly the same stacktraces
    Throwable[] t = new Throwable[2];
    for (int i = 0; i < t.length; i++) {
      t[i] = new Throwable();
      assertStackTraceIsExpanded(t[i], false);
    }
    Throwable in0 = ThrowableInterner.intern(t[0]);
    Throwable in1 = ThrowableInterner.intern(t[1]);
    assertSame(in0, in1);
    assertStackTraceIsExpanded(t[0], false);
    assertStackTraceIsExpanded(t[1], false);

    // expand stacktrace after interning should not affect hashcode
    t[0].getStackTrace();
    assertStackTraceIsExpanded(t[0], true);
    assertStackTraceIsExpanded(t[1], false);
    assertSame(in0, ThrowableInterner.intern(t[0]));
    assertSame(in0, ThrowableInterner.intern(t[1]));

    // expand stacktrace before interning should not affect hashcode
    for (int i = 0; i < t.length; i++) {
      t[i] = new Throwable();
    }
    t[0].getStackTrace();
    assertStackTraceIsExpanded(t[0], true);
    assertStackTraceIsExpanded(t[1], false);
    in0 = ThrowableInterner.intern(t[0]);
    assertSame(in0, ThrowableInterner.intern(t[0]));
    assertSame(in0, ThrowableInterner.intern(t[1]));
  }

  private static void assertStackTraceIsExpanded(Throwable t, boolean expanded) {
    StackTraceElement[] traces = ReflectionUtil.getField(t.getClass(), t, StackTraceElement[].class, "stackTrace");
    assertEquals(expanded, traces.length != 0);
  }
}
