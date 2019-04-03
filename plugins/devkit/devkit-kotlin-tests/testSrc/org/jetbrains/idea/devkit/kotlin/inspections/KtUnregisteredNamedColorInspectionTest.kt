// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.inspections.UnregisteredNamedColorInspectionTestBase

class KtUnregisteredNamedColorInspectionTest : UnregisteredNamedColorInspectionTestBase() {

  fun testInspection() {
    myFixture.configureByText("InspectionTest.kt", """
      import com.intellij.ui.JBColor

      class InspectionTest {
        fun smth() {
          JBColor.namedColor("${knownNamedColor}", 0xcdcdcd)
          JBColor.<warning descr="Named color key 'NotRegisteredKey' is not registered in '*.themeMetadata.json' (Documentation)">namedColor</warning>("NotRegisteredKey", 0xcdcdcd)
        }
      }
    """.trimIndent())
    myFixture.testHighlighting("InspectionTest.kt")
  }
}