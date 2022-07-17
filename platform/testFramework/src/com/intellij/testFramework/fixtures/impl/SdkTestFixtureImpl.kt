// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.runInEdtAndGet

class SdkTestFixtureImpl(
  private val sdkType: SdkType,
  private val versionFilter: (String) -> Boolean
) : BaseFixture(), SdkTestFixture {

  private lateinit var sdk: Sdk

  override fun getSdk(): Sdk {
    return sdk
  }

  override fun setUp() {
    super.setUp()

    sdk = setUpSdk(sdkType, versionFilter)
  }

  private fun setUpSdk(sdkType: SdkType, versionFilter: (String) -> Boolean): Sdk {
    val table = ProjectJdkTable.getInstance()
    for (sdk in runReadAction { table.allJdks }) {
      val versionString = sdk.versionString
      if (versionString != null && versionFilter(versionString)) {
        return sdk
      }
    }
    for (sdkHome in sdkType.suggestHomePaths()) {
      if (sdkType.isValidSdkHome(sdkHome)) {
        val versionString = sdkType.getVersionString(sdkHome)
        if (versionString != null && versionFilter(versionString)) {
          val sdk = createAndAddSdk(sdkHome, sdkType, testRootDisposable)
          if (sdk != null) {
            return sdk
          }
        }
      }
    }
    throw AssertionError(
      "Cannot find SDK with defined parameters. " +
      "Please, research SDK restrictions or discuss it with test author, " +
      "and install it manually."
    )
  }

  private fun createAndAddSdk(sdkHome: String, sdkType: SdkType, parentDisposable: Disposable): Sdk? {
    val table = ProjectJdkTable.getInstance()
    val sdk = runInEdtAndGet {
      SdkConfigurationUtil.createAndAddSDK(sdkHome, sdkType)
    }
    if (sdk != null) {
      Disposer.register(parentDisposable, Disposable {
        runWriteActionAndWait {
          table.removeJdk(sdk)
        }
      })
    }
    return sdk
  }
}