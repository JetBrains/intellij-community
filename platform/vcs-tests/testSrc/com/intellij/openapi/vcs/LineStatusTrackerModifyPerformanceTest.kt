// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
          simpleTracker.setBaseRevision(text2)
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
          simpleTracker.setBaseRevision(text3)
        })
      }
    }.assertTiming()
  }
}
