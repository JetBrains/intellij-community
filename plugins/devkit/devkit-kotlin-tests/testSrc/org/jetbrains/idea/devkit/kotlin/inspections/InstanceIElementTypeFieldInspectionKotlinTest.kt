// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

class InstanceIElementTypeFieldInspectionKotlinTest : InstanceIElementTypeFieldInspectionTestBase() {

  override fun getFileExtension(): String = "kt"

  fun testInstancePropertyWithIElementType() {
    doTest()
  }

  fun testCompanionObjectNoWarning() {
    doTest()
  }

  fun testObjectDeclarationNoWarning() {
    doTest()
  }

  fun testQualifiedNameWithLanguage() {
    doTest()
  }

  fun testEnumFieldsNoWarning() {
    doTest()
  }
}
