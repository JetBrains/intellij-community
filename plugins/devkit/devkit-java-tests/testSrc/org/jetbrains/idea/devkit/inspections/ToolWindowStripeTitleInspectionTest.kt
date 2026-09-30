// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TemporaryDirectory
import kotlin.io.path.createParentDirectories
import kotlin.io.path.name
import kotlin.io.path.writeText

class ToolWindowStripeTitleInspectionTest : ToolWindowStripeTitleInspectionTestBase() {

  fun `test reports the extension and the key when the key is in another bundle`() {
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.RightBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.RightBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'. Move the key to 'messages.RightBundle'.">My Tool Window</warning>"/>
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
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window but no <resource-bundle>, so the platform reads no stripe title. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.MyBundle'. Declare a <resource-bundle> in '$moduleName', and move the key to it.">My Tool Window</warning>"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    val bundle = addFile(
      "messages/MyBundle.properties", """
      <warning descr="The module '$moduleName' declares the tool window 'My Tool Window' but no <resource-bundle>, so the platform does not read this key. Declare a <resource-bundle> in '$moduleName', and move this key to it.">toolwindow.stripe.My_Tool_Window</warning>=My Tool Window
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

  fun `test reports a content module that does not inherit the bundle of its plugin`() {
    addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.PluginBundle</resource-bundle>
        <content>
          <module name="my.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
    val moduleXml = addFile(
      "my.module.xml", """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window but no <resource-bundle>, so the platform reads no stripe title. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.PluginBundle'. Declare a <resource-bundle> in '$moduleName', and move the key to it.">My Tool Window</warning>"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    val pluginBundle = addFile(
      "messages/PluginBundle.properties", """
      <warning descr="The module '$moduleName' declares the tool window 'My Tool Window' but no <resource-bundle>, so the platform does not read this key. Declare a <resource-bundle> in '$moduleName', and move this key to it.">toolwindow.stripe.My_Tool_Window</warning>=My Tool Window
      """.trimIndent()
    )

    myFixture.testHighlightingAllFiles(true, false, false, moduleXml, pluginBundle)
  }

  fun `test reports a content module of the core plugin against IdeBundle`() {
    addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.intellij</id>
        <content>
          <module name="my.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
    val moduleXml = addFile(
      "my.module.xml", """
      <idea-plugin>
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

    myFixture.testHighlightingAllFiles(true, false, false, moduleXml, bundle)
  }

  fun `test reports a sub-descriptor against the bundle of the descriptor that loads it`() {
    addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.RightBundle</resource-bundle>
        <depends optional="true" config-file="optional.xml">com.example.other</depends>
      </idea-plugin>
      """.trimIndent()
    )
    val optionalXml = addFile(
      "META-INF/optional.xml", """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.RightBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'. Move the key to 'messages.RightBundle'.">My Tool Window</warning>"/>
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

    myFixture.testHighlightingAllFiles(true, false, false, optionalXml, wrongBundle)
  }

  fun `test reports a sub-descriptor against its own bundle`() {
    addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.ParentBundle</resource-bundle>
        <depends optional="true" config-file="optional.xml">com.example.other</depends>
      </idea-plugin>
      """.trimIndent()
    )
    val optionalXml = addFile(
      "META-INF/optional.xml", """
      <idea-plugin>
        <resource-bundle>messages.OwnBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.OwnBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.ParentBundle'. Move the key to 'messages.OwnBundle'.">My Tool Window</warning>"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/OwnBundle.properties", "unrelated.key=Value\n")
    val parentBundle = addFile(
      "messages/ParentBundle.properties", """
      <warning descr="The module '$moduleName' declares the tool window 'My Tool Window'. The platform reads its stripe title from 'messages.OwnBundle'. It does not read this bundle. Move this key to 'messages.OwnBundle'.">toolwindow.stripe.My_Tool_Window</warning>=My Tool Window
      """.trimIndent()
    )

    myFixture.testHighlightingAllFiles(true, false, false, optionalXml, parentBundle)
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

  fun `test reports an extension point with a subclass of the tool window bean`() {
    myFixture.addClass(
      """
      package com.intellij.openapi.wm.ext;
      public class LibraryDependentToolWindow extends com.intellij.openapi.wm.ToolWindowEP {}
      """.trimIndent()
    )
    addFile(
      "META-INF/libraryExtensionPoints.xml", """
      <idea-plugin>
        <extensionPoints>
          <extensionPoint name="library.toolWindow" beanClass="com.intellij.openapi.wm.ext.LibraryDependentToolWindow"/>
        </extensionPoints>
      </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.RightBundle</resource-bundle>
        <extensions defaultExtensionNs="com.intellij">
          <library.toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.RightBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'. Move the key to 'messages.RightBundle'.">My Tool Window</warning>"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    // The key side cannot find this extension. See resolveToolWindowExtension.
    addFile("messages/WrongBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml)
  }

  fun `test does not report an extension point with the same name and another bean`() {
    myFixture.addClass(
      """
      package com.example;
      import com.intellij.util.xmlb.annotations.Attribute;
      public class OtherBean {
        @Attribute public String id;
      }
      """.trimIndent()
    )
    val pluginXml = addFile(
      "META-INF/plugin.xml", """
      <idea-plugin>
        <id>com.example.plugin</id>
        <resource-bundle>messages.RightBundle</resource-bundle>
        <extensionPoints>
          <extensionPoint name="toolWindow" beanClass="com.example.OtherBean"/>
        </extensionPoints>
        <extensions defaultExtensionNs="com.example.plugin">
          <toolWindow id="My Tool Window"/>
        </extensions>
      </idea-plugin>
      """.trimIndent()
    )
    addFile("messages/RightBundle.properties", "unrelated.key=Value\n")
    val wrongBundle = addFile("messages/WrongBundle.properties", "toolwindow.stripe.My_Tool_Window=My Tool Window\n")

    myFixture.testHighlightingAllFiles(true, false, false, pluginXml, wrongBundle)
  }

  fun `test does not report when the factory sets the stripe title itself`() {
    addFactory(
      "MyToolWindowFactory.java", """
      import com.intellij.openapi.wm.ToolWindow;

      public class MyToolWindowFactory {
        public void init(ToolWindow toolWindow) {
          toolWindow.setStripeTitle("My Tool Window");
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
      import com.intellij.openapi.wm.ToolWindow;

      public class MyToolWindowFactory {
        public void init(ToolWindow toolWindow) {
          toolWindow.setStripeTitleProvider(() -> "My Tool Window");
        }
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
      import com.intellij.openapi.wm.ToolWindow;

      public class ShortTitleToolWindowFactory {
        public void init(ToolWindow toolWindow) {
          toolWindow.setStripeShortTitleProvider(() -> "TW");
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
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.RightBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'. Move the key to 'messages.RightBundle'.">My Tool Window</warning>" factoryClass="ShortTitleToolWindowFactory"/>
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

  fun `test reports when the factory sets the title only when the content is created`() {
    addFactory(
      "LazyTitleToolWindowFactory.java", """
      import com.intellij.openapi.wm.ToolWindow;

      public class LazyTitleToolWindowFactory {
        public void init(ToolWindow toolWindow) {
        }

        public void createToolWindowContent(Object project, ToolWindow toolWindow) {
          toolWindow.setStripeTitle("My Tool Window");
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
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.RightBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'. Move the key to 'messages.RightBundle'.">My Tool Window</warning>" factoryClass="LazyTitleToolWindowFactory"/>
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

  fun `test reports when the factory only reads the title or calls an unrelated setter`() {
    addFactory(
      "ReadingToolWindowFactory.java", """
      import com.intellij.openapi.wm.ToolWindow;

      public class ReadingToolWindowFactory {
        public void init(ToolWindow toolWindow) {
          setStripeTitle(toolWindow.getStripeTitle());
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
          <toolWindow id="<warning descr="The module '$moduleName' declares this tool window. The platform reads its stripe title from 'messages.RightBundle'. The key 'toolwindow.stripe.My_Tool_Window' is in 'messages.WrongBundle'. Move the key to 'messages.RightBundle'.">My Tool Window</warning>" factoryClass="ReadingToolWindowFactory"/>
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

  fun `test does not report a key in a library`() {
    // A library root must be outside the content root of the fixture.
    val libraryDir = TemporaryDirectory.generateTemporaryPath("stripeTitleLibrary")
    libraryDir.resolve("messages/LibraryBundle.properties").apply {
      createParentDirectories()
      writeText("toolwindow.stripe.My_Tool_Window=My Tool Window\n")
    }
    PsiTestUtil.addLibrary(testRootDisposable, module, "stripeTitleLibrary", libraryDir.parent.toString(), libraryDir.name)
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
}
