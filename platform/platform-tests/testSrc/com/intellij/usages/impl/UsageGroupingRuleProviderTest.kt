// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewSettings
import com.intellij.usages.impl.rules.FileGroupingRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class UsageGroupingRuleProviderTest {
  companion object {
    private val project = projectFixture()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `file names remain available without file structure providers`(groupByFileStructure: Boolean, @TestDisposable disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(FileStructureGroupRuleProvider.EP_NAME, emptyList(), disposable)
    val settings = UsageViewSettings(isGroupByFileStructure = groupByFileStructure).apply {
      showShortFilePath = false
    }
    val rules = UsageGroupingRuleProviderImpl().getActiveRules(project.get(), settings, null)
    runReadActionBlocking {
      val file = PsiManager.getInstance(project.get()).findFile(LightVirtualFile("result.txt", "hello"))!!
      val usage = UsageInfo2UsageAdapter(UsageInfo(file))
      val group = rules.filterIsInstance<FileGroupingRule>().single().getParentGroupFor(usage, UsageTarget.EMPTY_ARRAY)
      assertEquals("result.txt", group?.presentableGroupText)
    }
  }
}
