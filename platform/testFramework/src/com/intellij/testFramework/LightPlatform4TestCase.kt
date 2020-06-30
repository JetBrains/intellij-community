// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
abstract class LightPlatform4TestCase : LightPlatformTestCase() {
  @Rule
  @JvmField
  val runBareRule = runBareTestRule
}
