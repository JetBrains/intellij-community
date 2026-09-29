// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

class ToolWindowStripeTitleInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    // DevKit recognizes a plugin project by this class, see PsiUtil.IDE_PROJECT_MARKER_CLASS.
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}")
    // DOM takes the allowed attributes from the bean. Without the annotations, it reports its own problems.
    myFixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Attribute { String value() default \"\"; }")
    myFixture.addClass(
      """
      package com.intellij.openapi.wm;
      import com.intellij.util.xmlb.annotations.Attribute;
      public class ToolWindowEP {
        @Attribute public String id;
        @Attribute public String factoryClass;
      }
      """.trimIndent()
    )
    // An id-less descriptor declares the extension point under the 'com.intellij' prefix.
    addFile(
      "META-INF/extensionPoints.xml", """
      <idea-plugin>
        <extensionPoints>
          <extensionPoint name="toolWindow" beanClass="com.intellij.openapi.wm.ToolWindowEP"/>
        </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.enableInspections(ToolWindowStripeTitleInspection())
  }

  fun `test reports the extension and the key when the key is in another bundle`() {
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.RightBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.RightBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'. Move the key to 'messages.RightBundle', or declare 'messages.WrongBundle' in '$moduleName'.">My Tool Window</warning>"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    val wrongBundle = addFile(
      "messages/WrongBundle.properties", """
      <warning descr="The module '$moduleName' declares the tool window 'My Tool Window'. The platform reads its stripe title from 'messages.RightBundle'. It does not read this bundle. Move this key to 'messages.RightBundle'.">toolwindow.stripe.My_Tool_Window</warning>=My Tool Window
      """.trimIndent()
    )

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, wrongBundle)
  }

  fun `test reports the extension and the key when the descriptor declares no bundle`() {
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window but no <resource-bundle>, so the platform reads no stripe title. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.MyBundle'. Declare 'messages.MyBundle' as the <resource-bundle> of '$moduleName'.">My Tool Window</warning>"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    val bundle = addFile(
      "messages/MyBundle.properties", """
      <warning descr="The module '$moduleName' declares the tool window 'My Tool Window' but no <resource-bundle>, so the platform does not read this key. Declare this bundle as the <resource-bundle> of '$moduleName'.">toolwindow.stripe.My_Tool_Window</warning>=My Tool Window
      """.trimIndent()
    )

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, bundle)
  }

  fun `test reports a core plugin tool window against IdeBundle`() {
    // The core plugin ignores the declared bundle, so the platform reads messages.IdeBundle.
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.intellij</id>
        <resource-bundle>messages.MyBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="<warning descr="The core plugin owns the module '$moduleName'. The platform therefore reads the stripe title from 'messages.IdeBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.MyBundle'. Move the key to 'messages.IdeBundle'.">My Tool Window</warning>"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    val bundle = addFile(
      "messages/MyBundle.properties", """
      <warning descr="The module '$moduleName' declares the tool window 'My Tool Window'. The platform reads its stripe title from 'messages.IdeBundle'. It does not read this bundle. Move this key to 'messages.IdeBundle'.">toolwindow.stripe.My_Tool_Window</warning>=My Tool Window
      """.trimIndent()
    )

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, bundle)
  }

  fun `test does not report a key in the declared bundle`() {
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.MyBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="My Tool Window"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    val bundle = addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, bundle)
  }

  fun `test does not report a tool window without any key`() {
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.MyBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="My Tool Window"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/MyBundle.properties", "unrelated.key=Value\n")

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml)
  }

  fun `test does not report when the factory sets the stripe title itself`() {
    addFactory(
      "MyToolWindowFactory.java", """
      public class MyToolWindowFactory {
        public void init(Object toolWindow) {
          setStripeTitle("My Tool Window");
        }

        void setStripeTitle(String title) {}
      }
      """.trimIndent()
    )
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.RightBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="My Tool Window" factoryClass="MyToolWindowFactory"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    val wrongBundle = addFile("messages/WrongBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, wrongBundle)
  }

  fun `test does not report when the factory sets the title and the descriptor declares no bundle`() {
    addFactory(
      "MyToolWindowFactory.java", """
      public class MyToolWindowFactory {
        public void init(Object toolWindow) {
          setStripeTitle("My Tool Window");
        }

        void setStripeTitle(String title) {}
      }
      """.trimIndent()
    )
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="My Tool Window" factoryClass="MyToolWindowFactory"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    val bundle = addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, bundle)
  }

  fun `test reports when the factory sets only the short title`() {
    addFactory(
      "ShortTitleToolWindowFactory.java", """
      public class ShortTitleToolWindowFactory {
        public void init(Object toolWindow) {
          setStripeShortTitleProvider("TW");
        }

        void setStripeShortTitleProvider(String title) {}
      }
      """.trimIndent()
    )
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.RightBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.RightBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'. Move the key to 'messages.RightBundle', or declare 'messages.WrongBundle' in '$moduleName'.">My Tool Window</warning>" factoryClass="ShortTitleToolWindowFactory"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    val wrongBundle = addFile(
      "messages/WrongBundle.properties", """
      <warning descr="The module '$moduleName' declares the tool window 'My Tool Window'. The platform reads its stripe title from 'messages.RightBundle'. It does not read this bundle. Move this key to 'messages.RightBundle'.">toolwindow.stripe.My_Tool_Window</warning>=My Tool Window
      """.trimIndent()
    )

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, wrongBundle)
  }

  fun `test does not report a key in a language pack`() {
    addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.MyBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="My Tool Window"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")
    val localizationBundle = addFile("localization/zh/messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=Translated\n")

    myFixture.testHighlightingAllFiles(true, false, false, localizationBundle)
  }

  fun `test fix declares the resource bundle of the descriptor`() {
    addFile("messages/MyBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")
    // The fix formats the new tag with the default XML indent of 4 spaces, so the descriptor uses it too.
    myFixture.configureByText(
      "plugin.xml", """
      <idea-plugin>
          <id>com.example.plugin</id>
          <extensions defaultExtensionNs="com.intellij">
              <toolWindow id="My Tool<caret> Window"/>
          </extensions>
      </idea-plugin>
      """.trimIndent()
    )

    myFixture.launchAction(myFixture.findSingleIntention("Declare 'messages.MyBundle' as the resource bundle"))

    myFixture.checkResult("""
      <idea-plugin>
          <id>com.example.plugin</id>
          <extensions defaultExtensionNs="com.intellij">
              <toolWindow id="My Tool Window"/>
          </extensions>
          <resource-bundle>messages.MyBundle</resource-bundle>
      </idea-plugin>
      """.trimIndent())
  }

  /** The fixture creates the JPS module that holds the descriptor, so its name is not a literal. */
  private val moduleName: String get() = myFixture.module.name

  private fun addFile(path: String, @Language("") text: String): VirtualFile = myFixture.addFileToProject(path, text).virtualFile

  /**
   * The inspection reads the body of the factory class. The fixture forbids the tree of any file except the checked one,
   * and it cannot allow a single file, so this allows all files.
   */
  private fun addFactory(path: String, @Language("JAVA") text: String) {
    myFixture.allowTreeAccessForAllFiles()
    addFile(path, text)
  }

}
