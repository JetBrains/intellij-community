// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.quickfix

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.kotlin.inspections.ktCompanionObjectInExtensionProjectFixture
import org.jetbrains.idea.devkit.kotlin.inspections.quickfix.KtRemoveEmptyCompanionObjectFixTest

@TestApplication
class K2RemoveEmptyCompanionObjectFixTest : KtRemoveEmptyCompanionObjectFixTest(projectFixture) {
  companion object {
    private val projectFixture = ktCompanionObjectInExtensionProjectFixture()
  }
}