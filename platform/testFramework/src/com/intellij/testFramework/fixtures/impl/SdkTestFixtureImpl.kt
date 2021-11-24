// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.SdkTestFixture

class SdkTestFixtureImpl : BaseFixture(), SdkTestFixture {
  override fun setUpSdk(sdkType: SdkType, versionFilter: (String) -> Boolean): Sdk {
    val jdkTable = ProjectJdkTable.getInstance()
    for (sdkHome in sdkType.suggestHomePaths()) {
      if (sdkType.isValidSdkHome(sdkHome)) {
        val versionString = sdkType.getVersionString(sdkHome)
        if (versionString != null && versionFilter(versionString)) {
          val sdk = SdkConfigurationUtil.createAndAddSDK(sdkHome, sdkType)
          if (sdk != null) {
            Disposer.register(testRootDisposable, Disposable { runWriteActionAndWait { jdkTable.removeJdk(sdk) } })
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
}