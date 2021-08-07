// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AlsoTest {
  @Test
  public void testShouldFail() {
    // Breakpoint! suspendPolicy(SuspendNone) LogExpression("Debugger: testShouldFail() reached")
    assertEquals(2 * 2, 5);
  }
}