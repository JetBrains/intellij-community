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

    sdk = findOrCreateSdk()
  }

  private fun findOrCreateSdk(): Sdk {
    return findSdkInTable() ?: findAndAddSdk() ?: throw AssertionError(
      "Cannot find SDK with defined parameters. " +
      "Please, research SDK restrictions or discuss it with test author, " +
      "and install it manually."
    )
  }

  private fun findSdkInTable(): Sdk? {
    val table = ProjectJdkTable.getInstance()
    return runReadAction { table.allJdks }.asSequence()
      .filter { it.versionString != null && versionFilter(it.versionString!!) }
      .sortedBy { it.versionString }
      .firstOrNull()
  }

  private fun findAndAddSdk(): Sdk? {
    return sdkType.suggestHomePaths().asSequence()
      .filter { sdkType.isValidSdkHome(it) }
      .map { sdkType.getVersionString(it) to it }
      .filter { it.first != null && versionFilter(it.first!!) }
      .sortedBy { it.first }
      .mapNotNull { createAndAddSdk(it.second, sdkType, testRootDisposable) }
      .firstOrNull()
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