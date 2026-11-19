// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.kotlin.inspections.KtPolySymbolPropertyTypeInspectionTest
import org.jetbrains.idea.devkit.kotlin.inspections.polySymbolPropertyTypeProjectFixture

@TestApplication
internal class K2PolySymbolPropertyTypeInspectionTest : KtPolySymbolPropertyTypeInspectionTest(projectFixture) {
  companion object {
    private val projectFixture = polySymbolPropertyTypeProjectFixture()
  }
}