// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.rt.execution.junit.TestsRepeater;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code junit-rt.jar} of an installed IDE calls {@link TestsRepeater} from the class path of the project.
 * The two can have different versions, so the runner of an older IDE must find the members that it calls.
 */
public class TestsRepeaterCompatibilityTest {
  @Test
  void runnerOfOlderIdeFindsTheRepeatLoop() throws NoSuchMethodException {
    assertEquals(int.class, TestsRepeater.class.getMethod("repeat", int.class, boolean.class, TestsRepeater.TestRun.class).getReturnType());
  }

  @Test
  void runnerOfOlderIdeImplementsTheTestRun() throws NoSuchMethodException {
    assertEquals(int.class, TestsRepeater.TestRun.class.getMethod("execute", boolean.class).getReturnType());
    assertEquals(1, TestsRepeater.TestRun.class.getMethods().length);
  }
}
