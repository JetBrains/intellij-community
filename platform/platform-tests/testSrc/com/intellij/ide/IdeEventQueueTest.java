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
package com.intellij.ide;

import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

public class IdeEventQueueTest extends PlatformTestCase {
  public void testManyEvents() {
    int N = 100/*000*/;
    PlatformTestUtil.startPerformanceTest("Event queue dispatch", 10000, () -> {
      UIUtil.dispatchAllInvocationEvents();
      AtomicInteger count = new AtomicInteger();
      for (int i = 0; i < N; i++) {
        SwingUtilities.invokeLater(count::incrementAndGet);
      }
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(N, count.get());
    }).assertTiming();
  }
}
