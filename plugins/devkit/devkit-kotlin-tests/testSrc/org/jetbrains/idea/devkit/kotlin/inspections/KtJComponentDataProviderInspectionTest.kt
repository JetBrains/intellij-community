// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.inspections.JComponentDataProviderInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

class KtJComponentDataProviderInspectionTest : JComponentDataProviderInspectionTestBase() {

  override fun getFileExtension() = "kt"

  override fun getBasePath(): String = DevkitKtTestsUtil.TESTDATA_PATH + "/inspections/jComponentDataProvider/"

  fun testObject() {
    doTest()
  }

}