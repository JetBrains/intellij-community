// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnitMalformedDeclarationInspectionTestBase
import com.intellij.junit.testFramework.MavenTestLib
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil

abstract class KotlinJUnitMalformedDeclarationInspectionTestBase(
  vararg versions: MavenTestLib,
) : JUnitMalformedDeclarationInspectionTestBase(*versions) {
  override fun setUp() {
    super.setUp()
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
  }
}