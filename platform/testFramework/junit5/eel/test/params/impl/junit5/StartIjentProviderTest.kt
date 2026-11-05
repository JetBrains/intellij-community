// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolderImpl
import com.intellij.platform.testFramework.junit5.eel.params.api.WslTest
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider.StartResult
import com.intellij.testFramework.junit5.eel.EelFixtureFilter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.opentest4j.TestAbortedException

/**
 * [EelFixtureFilter] has a higher priority than a mandatory annotation.
 */
internal class StartIjentProviderTest {
  @Test
  fun testDisabledEnvironmentAbortsMandatoryTestWithoutStart() {
    EelFixtureFilter.replaceInstance(EelFixtureFilter(isWslEnabled = false)).use {
      // FakeWslProvider fails on start, so the abort also proves that the environment did not start.
      assertThrows<TestAbortedException> {
        startMandatoryWsl(FakeWslProvider)
      }
    }
  }

  @Test
  fun testEnabledAbsentEnvironmentFailsMandatoryTest() {
    EelFixtureFilter.replaceInstance(EelFixtureFilter()).use {
      assertThrows<IllegalStateException> {
        startMandatoryWsl(AbsentWslProvider)
      }
    }
  }

  private fun startMandatoryWsl(provider: EelIjentTestProvider<WslTest>) {
    val annotation = MandatoryWslClass::class.java.getDeclaredMethod("mandatoryWsl").getAnnotation(WslTest::class.java)
    runBlocking {
      EelHolderImpl(provider, annotation).startIjentProvider(ParentOfIjentScopes(this))
    }
  }
}

private class MandatoryWslClass {
  @WslTest
  fun mandatoryWsl(): Unit = Unit
}

private object AbsentWslProvider : EelIjentTestProvider<WslTest> by FakeWslProvider {
  override suspend fun start(scope: ParentOfIjentScopes, annotation: WslTest?): StartResult =
    StartResult.Skipped("No WSL distro found")
}
