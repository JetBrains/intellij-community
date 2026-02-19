// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SomeTest {
  @Tag("selected")
  @Test
  public void testSomething() {
    // Breakpoint! suspendPolicy(SuspendNone) LogExpression("Debugger: testSomething() reached")
    assertEquals(2 * 2, 4);
  }
}