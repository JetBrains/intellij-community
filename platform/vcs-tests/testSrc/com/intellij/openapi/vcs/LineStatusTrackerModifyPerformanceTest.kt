/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs

import com.intellij.testFramework.PlatformTestUtil

class LineStatusTrackerModifyPerformanceTest : BaseLineStatusTrackerTestCase() {
  fun testInitialUnfreeze() {
    val sb1 = StringBuilder()
    val sb2 = StringBuilder()
    for (i in 1..100000) {
      val text = "Line $i\n"
      if (i % 4 == 0) sb2.append("Modified ")
      sb1.append(text)
      sb2.append(text)
    }
    val text1 = sb1.toString()
    val text2 = sb2.toString()

    PlatformTestUtil.startPerformanceTest(PlatformTestUtil.getTestName(name, true), 7000) {
      test(text1) {
        tracker.doFrozen(Runnable {
          tracker.setBaseRevision(text2)
        })
      }
    }.assertTiming()
  }

  fun testDirtyUnfreeze() {
    val sb1 = StringBuilder()
    val sb2 = StringBuilder()
    val sb3 = StringBuilder()
    for (i in 1..100000) {
      val text = "Line $i\n"
      if (i % 4 == 0) sb2.append("Modified ")
      if (i % 5 == 0) sb3.append("Modified ")
      sb1.append(text)
      sb2.append(text)
      sb3.append(text)
    }
    val text1 = sb1.toString()
    val text2 = sb2.toString()
    val text3 = sb3.toString()

    PlatformTestUtil.startPerformanceTest(PlatformTestUtil.getTestName(name, true), 10000) {
      test(text1, text2) {
        tracker.doFrozen(Runnable {
          tracker.setBaseRevision(text3)
        })
      }
    }.assertTiming()
  }
}
