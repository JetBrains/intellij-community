// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.internal.UseEqualsInspectionTestBase
import org.jetbrains.idea.devkit.inspections.internal.UsePluginIdEqualsInspection
import org.jetbrains.idea.devkit.inspections.internal.UsePrimitiveTypesEqualsInspection
import org.jetbrains.idea.devkit.inspections.internal.UseVirtualFileEqualsInspection
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/internal")
class KtUseEqualsInspectionTest : UseEqualsInspectionTestBase() {

    

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/internal"

  override fun testVirtualFile() {
    doTest(UseVirtualFileEqualsInspection::class.java, "VirtualFile.kt")
  }

  override fun testPluginId() {
    doTest(UsePluginIdEqualsInspection::class.java, "PluginId.kt")
  }

  override fun testPrimitiveTypes() {
    doTest(UsePrimitiveTypesEqualsInspection::class.java, "PsiType.kt")
  }
}