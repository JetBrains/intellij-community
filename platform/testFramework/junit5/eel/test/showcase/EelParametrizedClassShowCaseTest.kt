// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.showcase

import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass

/**
 * You need 3 things: [TestApplicationWithEel],[ParameterizedClass] and [EelHolder].
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
class EelParametrizedClassShowCaseTest(val eelProvider: EelHolder) {
  //private val projectFixture = projectFixture()
  private val tempDir = tempPathFixture()

  @BeforeEach
  fun setUp() {
    println(eelProvider.eel.userInfo)
  }

  @Test
  fun testEel() {
    println(eelProvider.eel.userInfo)
  }

  @Test
  fun testProject() {
    println(tempDir.get())
  }
}