// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.internal.UsePluginIdEqualsInspection
import org.jetbrains.idea.devkit.inspections.internal.UsePrimitiveTypesEqualsInspection
import org.jetbrains.idea.devkit.inspections.internal.UseVirtualFileEqualsInspection
import org.jetbrains.idea.devkit.inspections.internal.useEqualsFixture
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/internal")
@TestApplication
class KtUseEqualsInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val useEquals by useEqualsFixture(projectFixture, DevkitKtTestsUtil.TESTDATA_PATH + "inspections/internal")

  @Test
  fun testVirtualFile() {
    useEquals.doTest(UseVirtualFileEqualsInspection::class.java, "VirtualFile.kt")
  }

  @Test
  fun testPluginId() {
    useEquals.doTest(UsePluginIdEqualsInspection::class.java, "PluginId.kt")
  }

  @Test
  fun testPrimitiveTypes() {
    useEquals.doTest(UsePrimitiveTypesEqualsInspection::class.java, "PsiType.kt")
  }
}