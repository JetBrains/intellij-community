// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

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
