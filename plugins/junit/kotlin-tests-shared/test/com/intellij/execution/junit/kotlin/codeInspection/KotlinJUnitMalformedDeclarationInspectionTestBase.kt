// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnitLibrary
import com.intellij.junit.testFramework.JUnitMalformedDeclarationInspectionTestBase
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinJUnitMalformedDeclarationInspectionTestBase(
  vararg versions: JUnitLibrary,
) : JUnitMalformedDeclarationInspectionTestBase(*versions), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
  }
}