/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class StringUtilPerformanceTest {
  private static final String TEST_STRING = "0123456789abcdefghijklmnopqrstuvwxyz";

  @Test
  public void containsAnyChar() throws Exception {
    assertTrue(StringUtil.containsAnyChar(TEST_STRING, Integer.toString(new Random().nextInt())));

    PlatformTestUtil.startPerformanceTest("StringUtil.containsAnyChar()", SystemInfo.isWindows ? 500 : 200, () -> {
      for (int i = 0; i < 1000000; i++) {
        StringUtil.containsAnyChar(TEST_STRING, "XYZ");
        StringUtil.containsAnyChar("XYZ", TEST_STRING);
      }
    }).useLegacyScaling().assertTiming();
  }
}
