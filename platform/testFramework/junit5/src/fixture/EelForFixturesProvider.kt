// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.platform.eel.EelApi
import com.intellij.testFramework.junit5.fixture.EelForFixturesProvider.Companion.makeFixturesEelAware
import com.intellij.testFramework.junit5.impl.TypedStoreKey
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.get
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

/**
 * If extension provides eel for parametrized tests, it must call [makeFixturesEelAware] and must be registered *before* [TestFixtures].
 *
 * Warning: eel parameterization works for instance-level fixtures only.
 */
@TestOnly
@ApiStatus.Internal
fun interface EelForFixturesProvider {
  /**
   * @return eel for the certain [invocationContext] of parametrized test)
   */
  fun getEel(invocationContext: ReflectiveInvocationContext<Method>): EelApi

  companion object {

    @TestOnly
    @ApiStatus.Internal
    private val EEL_FOR_FIXTURES_PROVIDER = TypedStoreKey.createKey<EelForFixturesProvider>()

    @TestOnly
    @ApiStatus.Internal
    fun ExtensionContext.makeFixturesEelAware(eelForFixturesProvider: EelForFixturesProvider): Unit =
      set(EEL_FOR_FIXTURES_PROVIDER, eelForFixturesProvider)

    @TestOnly
    @ApiStatus.Internal
    internal fun ExtensionContext.getEelForParametrizedTestProvider(): EelForFixturesProvider? =
      get(EEL_FOR_FIXTURES_PROVIDER)
  }
}

