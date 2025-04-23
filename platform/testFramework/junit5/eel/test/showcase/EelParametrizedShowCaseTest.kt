// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.showcase

import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import org.junit.jupiter.params.ParameterizedTest
import org.junitpioneer.jupiter.cartesian.CartesianTest

/**
 * You need 3 things: [TestApplicationWithEel],[EelSource] and [EelHolder].
 */
@TestApplicationWithEel(atLeastOneRemoteEelRequired = false)
class EelParametrizedShowCaseTest {
  @ParameterizedTest
  @EelSource
  fun classicParametrizedTest(eelProvider: EelHolder) {
    println(eelProvider.eel.userInfo)
  }

  @CartesianTest
  fun pioneerParametrizedTest(@EelSource eelProvider: EelHolder) {
    println(eelProvider.eel.userInfo)
  }
}