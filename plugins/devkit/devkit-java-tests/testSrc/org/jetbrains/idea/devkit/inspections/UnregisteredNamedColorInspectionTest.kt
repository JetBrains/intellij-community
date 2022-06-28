// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.themes.UnregisteredNamedColorInspectionTestBase

class UnregisteredNamedColorInspectionTest : UnregisteredNamedColorInspectionTestBase() {

  fun testInspection() {
    //language=JAVA
    myFixture.addClass("""
      import com.intellij.ui.JBColor;

      class InspectionTest {
        public void smth() {
          JBColor.namedColor("${knownNamedColor}", 0xcdcdcd);
          JBColor.<warning descr="Named color key 'NotRegisteredKey' is not registered in '*.themeMetadata.json' (Documentation)">namedColor</warning>("NotRegisteredKey", 0xcdcdcd);
        }
      }
    """.trimIndent())
    myFixture.testHighlighting("InspectionTest.java")
  }
}
