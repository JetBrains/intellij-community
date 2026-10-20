// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5

import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.BazelTestUtil

object JUnitTestData {
  @JvmStatic
  fun getTestDataPath(): String {
    if (BazelTestUtil.isUnderBazelTest) {
      val label = BazelLabel.fromString("@community//plugins/junit5_rt_tests:testData")
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString()
    }
    return PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData"
  }
}
