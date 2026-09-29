// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import org.jetbrains.idea.devkit.inspections.ToolWindowStripeTitleInspectionTestBase

internal class KtToolWindowStripeTitleInspectionTest : ToolWindowStripeTitleInspectionTestBase() {

  fun `test does not report when the factory assigns the stripe title property`() {
    addFactory(
      "KotlinToolWindowFactory.kt",
      //language=kotlin
      """
      import com.intellij.openapi.wm.ToolWindow

      class KotlinToolWindowFactory {
        fun init(toolWindow: ToolWindow) {
          toolWindow.stripeTitle = "My Tool Window"
        }
      }
      """.trimIndent()
    )
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.RightBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="My Tool Window" factoryClass="KotlinToolWindowFactory"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    val wrongBundle = addFile("messages/WrongBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, wrongBundle)
  }
}
