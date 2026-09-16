// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

class ToolWindowStripeTitleInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    // DevKit recognizes a plugin project by this class, see PsiUtil.IDE_PROJECT_MARKER_CLASS.
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}")
    myFixture.addClass("package com.intellij.openapi.wm; public class ToolWindowEP { public String factoryClass; }")
    // An id-less descriptor declares the extension point under the 'com.intellij' prefix.
    addFile("META-INF/extensionPoints.xml", """
      <idea-plugin>
        <extensionPoints>
          <extensionPoint name="toolWindow" beanClass="com.intellij.openapi.wm.ToolWindowEP"/>
        </extensionPoints>
      </idea-plugin>
      """.trimIndent())
    myFixture.enableInspections(ToolWindowStripeTitleInspection())
  }

  fun `test reports the extension and the key when the key is in another bundle`() {
    addPluginDescriptor(resourceBundle = "messages.RightBundle", toolWindowId = "My Tool Window")
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    addFile("messages/WrongBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    val descriptorProblem = singleWarningIn("META-INF/plugin.xml")
    assertEquals(
      "The module '${declaringModuleName()}' declares this tool window." +
      " The platform reads its stripe title from 'messages.RightBundle'." +
      " The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'." +
      " Move the key to 'messages.RightBundle', or declare 'messages.WrongBundle' in '${declaringModuleName()}'.",
      descriptorProblem
    )

    val keyProblem = singleWarningIn("messages/WrongBundle.properties")
    assertEquals(
      "The module '${declaringModuleName()}' declares the tool window 'My Tool Window'." +
      " The platform reads its stripe title from 'messages.RightBundle'." +
      " It does not read this bundle. Move this key to 'messages.RightBundle'.",
      keyProblem
    )
  }

  fun `test reports the extension and the key when the descriptor declares no bundle`() {
    addPluginDescriptor(resourceBundle = null, toolWindowId = "My Tool Window")
    addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    val descriptorProblem = singleWarningIn("META-INF/plugin.xml")
    assertTrue(descriptorProblem, descriptorProblem.contains("messages.MyBundle"))
    assertTrue(descriptorProblem, descriptorProblem.contains("resource-bundle"))
    assertTrue(descriptorProblem, descriptorProblem.contains(declaringModuleName()))

    val keyProblem = singleWarningIn("messages/MyBundle.properties")
    assertTrue(keyProblem, keyProblem.contains("resource-bundle"))
    assertTrue(keyProblem, keyProblem.contains(declaringModuleName()))
  }

  fun `test reports a core plugin tool window against IdeBundle`() {
    // The core plugin ignores the declared bundle, so the platform reads messages.IdeBundle.
    addFile("META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.intellij</id>
        <resource-bundle>messages.MyBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="My Tool Window"/>
        </extensions>
      </idea-plugin>
      """.trimIndent())
    addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    val descriptorProblem = singleWarningIn("META-INF/plugin.xml")
    assertEquals(
      "The core plugin owns the module '${declaringModuleName()}'." +
      " The platform therefore reads the stripe title from 'messages.IdeBundle'." +
      " The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.MyBundle'." +
      " Move the key to 'messages.IdeBundle'.",
      descriptorProblem
    )

    val keyProblem = singleWarningIn("messages/MyBundle.properties")
    assertEquals(
      "The module '${declaringModuleName()}' declares the tool window 'My Tool Window'." +
      " The platform reads its stripe title from 'messages.IdeBundle'." +
      " It does not read this bundle. Move this key to 'messages.IdeBundle'.",
      keyProblem
    )
  }

  fun `test does not report a key in the declared bundle`() {
    addPluginDescriptor(resourceBundle = "messages.MyBundle", toolWindowId = "My Tool Window")
    addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    assertNoWarningsIn("META-INF/plugin.xml")
    assertNoWarningsIn("messages/MyBundle.properties")
  }

  fun `test replaces a space in the id with an underscore`() {
    addPluginDescriptor(resourceBundle = "messages.MyBundle", toolWindowId = "Version Control")
    addFile("messages/MyBundle.properties", "toolwindow.stripe.Version_Control=Version Control\n")

    assertNoWarningsIn("META-INF/plugin.xml")
    assertNoWarningsIn("messages/MyBundle.properties")
  }

  fun `test does not report a tool window without any key`() {
    addPluginDescriptor(resourceBundle = "messages.MyBundle", toolWindowId = "My Tool Window")
    addFile("messages/MyBundle.properties", "unrelated.key=Value\n")

    assertNoWarningsIn("META-INF/plugin.xml")
  }

  fun `test does not report when the factory sets the stripe title itself`() {
    addFile("MyToolWindowFactory.java", """
      public class MyToolWindowFactory {
        public void init(Object toolWindow) {
          setStripeTitle("My Tool Window");
        }

        void setStripeTitle(String title) {}
      }
      """.trimIndent())
    addPluginDescriptor(
      resourceBundle = "messages.RightBundle",
      toolWindowId = "My Tool Window",
      factoryClass = "MyToolWindowFactory",
    )
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    addFile("messages/WrongBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    assertNoWarningsIn("META-INF/plugin.xml")
    assertNoWarningsIn("messages/WrongBundle.properties")
  }

  fun `test does not report when the factory sets the title and the descriptor declares no bundle`() {
    addFile("MyToolWindowFactory.java", """
      public class MyToolWindowFactory {
        public void init(Object toolWindow) {
          setStripeTitle("My Tool Window");
        }

        void setStripeTitle(String title) {}
      }
      """.trimIndent())
    addPluginDescriptor(resourceBundle = null, toolWindowId = "My Tool Window", factoryClass = "MyToolWindowFactory")
    addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    assertNoWarningsIn("META-INF/plugin.xml")
    assertNoWarningsIn("messages/MyBundle.properties")
  }

  fun `test reports when the factory sets only the short title`() {
    addFile("ShortTitleToolWindowFactory.java", """
      public class ShortTitleToolWindowFactory {
        public void init(Object toolWindow) {
          setStripeShortTitleProvider("TW");
        }

        void setStripeShortTitleProvider(String title) {}
      }
      """.trimIndent())
    addPluginDescriptor(
      resourceBundle = "messages.RightBundle",
      toolWindowId = "My Tool Window",
      factoryClass = "ShortTitleToolWindowFactory",
    )
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    addFile("messages/WrongBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    assertSize(1, warningsIn("META-INF/plugin.xml"))
    assertSize(1, warningsIn("messages/WrongBundle.properties"))
  }

  fun `test does not report a key in a language pack`() {
    addPluginDescriptor(resourceBundle = "messages.MyBundle", toolWindowId = "My Tool Window")
    addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")
    addFile("localization/zh/messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=Translated\n")

    assertNoWarningsIn("localization/zh/messages/MyBundle.properties")
  }

  fun `test fix declares the resource bundle of the descriptor`() {
    addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")
    val descriptor = myFixture.configureByText("plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="My Tool<caret> Window"/>
        </extensions>
      </idea-plugin>
      """.trimIndent())

    myFixture.launchAction(myFixture.findSingleIntention("Declare 'messages.MyBundle' as the resource bundle"))

    assertTrue(descriptor.text, descriptor.text.contains("<resource-bundle>messages.MyBundle</resource-bundle>"))
  }

  private fun addPluginDescriptor(
    resourceBundle: String?,
    toolWindowId: String,
    factoryClass: String? = null,
  ): PsiFile {
    val bundleTag = if (resourceBundle == null) "" else "\n  <resource-bundle>$resourceBundle</resource-bundle>"
    val factoryAttribute = if (factoryClass == null) "" else " factoryClass=\"$factoryClass\""
    return addFile("META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>$bundleTag
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="$toolWindowId"$factoryAttribute/>
        </extensions>
      </idea-plugin>
      """.trimIndent())
  }

  /** The JPS module that holds the descriptor. Every message names it, so a reader can find the extension. */
  private fun declaringModuleName(): String {
    val name = myFixture.module.name
    // A blank name would make every `contains` assertion below pass for the wrong reason.
    assertTrue("the fixture module must have a name", name.isNotBlank())
    return name
  }

  private fun addFile(path: String, @Language("") text: String): PsiFile = myFixture.addFileToProject(path, text)

  /**
   * Keeps only the problems of the inspection under test. A stub bean class makes the XML DOM report its own problems.
   */
  private fun warningsIn(path: String): List<String> {
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(path))
    return myFixture.doHighlighting(HighlightSeverity.WARNING)
      .filter { it.inspectionToolId == INSPECTION_SHORT_NAME }
      .mapNotNull { it.description }
  }

  private fun singleWarningIn(path: String): String = warningsIn(path).single()

  private fun assertNoWarningsIn(path: String) = assertEmpty(warningsIn(path))
}

private const val INSPECTION_SHORT_NAME = "ToolWindowStripeTitle"
