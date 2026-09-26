// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

import com.intellij.openapi.projectRoots.SdkType
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@TestApplication
class JdkFixtureShowCaseTest {
  private companion object {
    const val SDK_NAME = "MySDK"
  }

  private val tempDirFixture = tempPathFixture()
  private val sdkFixture = projectFixture().sdkFixture(SDK_NAME, SdkType.EP_NAME.extensionList.first(), tempDirFixture)

  @Test
  fun jdkFixtureTest() {
    val sdk = sdkFixture.get()
    Assertions.assertEquals(tempDirFixture.get(), Path(sdk.homePath!!), "Wrong home path")
    Assertions.assertEquals(SDK_NAME, sdk.name, "Wrong name")
  }
}
