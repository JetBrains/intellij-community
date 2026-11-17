// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.themes.KNOWN_NAMED_COLOR
import org.jetbrains.idea.devkit.themes.unregisteredNamedColorInspectionFixture
import org.junit.jupiter.api.Test

@TestApplication
class UnregisteredNamedColorInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by unregisteredNamedColorInspectionFixture(projectFixture)

  @Test
  fun testInspection() {
    myFixture.configureByText("InspectionTest.java", """
      import com.intellij.ui.JBColor;

      class InspectionTest {
        public void smth() {
          JBColor.namedColor("$KNOWN_NAMED_COLOR", 0xcdcdcd);
          JBColor.<warning descr="Named color key 'NotRegisteredKey' is not registered in '*.themeMetadata.json' (Documentation)">namedColor</warning>("NotRegisteredKey", 0xcdcdcd);
        }
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}
