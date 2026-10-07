// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.assertj.core.api.AbstractStringAssert
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType

class PluginXmlContentModuleResolvingTest : JavaCodeInsightFixtureTestCase() {

  fun `test should resolve plugin module file`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.module1",
      "com.example.module1/com.example.module1.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())
    myFixture.addModuleWithPluginDescriptor(
      "com.example.module2",
      "com.example.module2/com.example.module2.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())
    myFixture.addModuleWithPluginDescriptor(
      "com.example.module3",
      "com.example.module3/com.example.module3.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    myFixture.assertThatResolvedElementIsFileWithPath(
      "com.example.plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="com.example.module1"/>
          <module name="com.example<caret>.module2"/>
          <module name="com.example.module3"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    ).endsWith("/com.example.module2/com.example.module2.xml")
  }

  fun `test should resolve plugin module file in Gradle module structure`() {
    myFixture.addModule("SplitPlugin", "split-plugin")

    myFixture.addModule("SplitPlugin.backend", "split-plugin/backend")
    myFixture.addModule("SplitPlugin.frontend", "split-plugin/frontend")
    myFixture.addModule("SplitPlugin.shared", "split-plugin/shared")

    myFixture.addModule("SplitPlugin.main", "split-plugin/src/main")
    myFixture.addModuleWithResourcesRoot("SplitPlugin.backend.main", "split-plugin/backend/src/main", "resources")
    myFixture.addModuleWithResourcesRoot("SplitPlugin.frontend.main", "split-plugin/frontend/src/main", "frontendResources")
    myFixture.addModuleWithResourcesRoot("SplitPlugin.shared.main", "split-plugin/shared/src/main", "resources")

    myFixture.addXmlFile(
      "split-plugin/backend/src/main/resources/SplitPlugin.backend.xml",
      """
      <idea-plugin>
          <dependencies>
              <module name="intellij.platform.backend"/>
              <module name="intellij.platform.rpc.backend"/>
              <module name="SplitPlugin.shared"/>
          </dependencies>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "split-plugin/frontend/src/main/frontendResources/SplitPlugin.frontend.xml",
      """
      <idea-plugin>
          <dependencies>
              <module name="intellij.platform.frontend"/>
              <module name="SplitPlugin.shared"/>
          </dependencies>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "split-plugin/shared/src/main/resources/SplitPlugin.shared.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    myFixture.assertThatResolvedElementIsFileWithPath(
      "split-plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.splitplugin</id>
        <content>
          <module name="SplitPlugin.backend"/>
          <module name="SplitPlugin<caret>.frontend"/>
          <module name="SplitPlugin.shared"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    ).endsWith("/split-plugin/frontend/src/main/frontendResources/SplitPlugin.frontend.xml")
  }

  fun `test should resolve plugin module file in Gradle module structure with dots in project name`() {
    myFixture.addModule("modular_plugin", "modular-plugin")

    myFixture.addModule("modular_plugin.backend", "modular-plugin/backend")
    myFixture.addModule("modular_plugin.frontend", "modular-plugin/frontend")
    myFixture.addModule("modular_plugin.shared", "modular-plugin/shared")

    myFixture.addModule("modular_plugin.main", "modular-plugin/src/main")
    myFixture.addModuleWithResourcesRoot("modular_plugin.backend.main", "modular-plugin/backend/src/main", "resources")
    myFixture.addModuleWithResourcesRoot("modular_plugin.frontend.main", "modular-plugin/frontend/src/main", "resources")
    myFixture.addModuleWithResourcesRoot("modular_plugin.shared.main", "modular-plugin/shared/src/main", "resources")

    myFixture.addXmlFile(
      "modular-plugin/backend/src/main/resources/modular.plugin.backend.xml",
      """
      <idea-plugin>
          <dependencies>
              <module name="modular.plugin.shared"/>
          </dependencies>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "modular-plugin/frontend/src/main/resources/modular.plugin.frontend.xml",
      """
      <idea-plugin>
          <dependencies>
              <module name="modular.plugin.shared"/>
          </dependencies>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "modular-plugin/shared/src/main/resources/modular.plugin.shared.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    myFixture.assertThatResolvedElementIsFileWithPath(
      "modular-plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.modularplugin</id>
        <content>
          <module name="modular.plugin.backend"/>
          <module name="modular.plugin<caret>.shared"/>
          <module name="modular.plugin.frontend"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    ).endsWith("/modular-plugin/shared/src/main/resources/modular.plugin.shared.xml")
  }

  fun `test should resolve plugin module file in single-file resource root`() {
    val module = myFixture.addModule("com.example.module", "com.example.module")
    myFixture.addSingleFileResourceRoot(
      module,
      "com.example.module/resources/com.example.module.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    myFixture.assertThatResolvedElementIsFileWithPath(
      "com.example.plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="com.example<caret>.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    ).endsWith("/com.example.module/resources/com.example.module.xml")
  }

  fun `test should resolve plugin sub-module file in single-file resource root`() {
    val module = myFixture.addModule("com.example.module", "com.example.module")
    myFixture.addSingleFileResourceRoot(
      module,
      "com.example.module/resources/com.example.module.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())
    myFixture.addSingleFileResourceRoot(
      module,
      "com.example.module/resources/com.example.module.sub.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    myFixture.assertThatResolvedElementIsFileWithPath(
      "com.example.plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="com.example.module/s<caret>ub"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    ).endsWith("/com.example.module/resources/com.example.module.sub.xml")
  }

  fun `test should not resolve plugin module file when single-file resource root is another descriptor`() {
    val module = myFixture.addModule("com.example.module", "com.example.module")
    myFixture.addSingleFileResourceRoot(
      module,
      "com.example.module/resources/com.example.module.sub.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    myFixture.configureFromExistingVirtualFile(myFixture.addXmlFile(
      "com.example.plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="com.example<caret>.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()).virtualFile)
    val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
    assertThat(reference).isNotNull()
    assertThat(reference!!.resolve()).isNull()
  }

  fun `test should not resolve plugin module file outside output root`() {
    val module = myFixture.addModule("com.example.module", "com.example.module")
    myFixture.addSingleFileResourceRoot(
      module,
      "com.example.module/resources/sub/com.example.module.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent(),
      relativeOutputPath = "sub")

    myFixture.configureFromExistingVirtualFile(myFixture.addXmlFile(
      "com.example.plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="com.example<caret>.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()).virtualFile)
    val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
    assertThat(reference).isNotNull()
    assertThat(reference!!.resolve()).isNull()
  }

  fun `test should resolve plugin module file in Gradle module structure with single-file resource root`() {
    myFixture.addModule("SplitPlugin", "split-plugin")
    myFixture.addModule("SplitPlugin.frontend", "split-plugin/frontend")
    myFixture.addModule("SplitPlugin.main", "split-plugin/src/main")
    val frontendMainModule = myFixture.addModule("SplitPlugin.frontend.main", "split-plugin/frontend/src/main")
    myFixture.addSingleFileResourceRoot(
      frontendMainModule,
      "split-plugin/frontend/src/main/resources/SplitPlugin.frontend.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    myFixture.assertThatResolvedElementIsFileWithPath(
      "split-plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.splitplugin</id>
        <content>
          <module name="SplitPlugin<caret>.frontend"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    ).endsWith("/split-plugin/frontend/src/main/resources/SplitPlugin.frontend.xml")
  }

  private fun CodeInsightTestFixture.addModuleWithPluginDescriptor(
    moduleName: String,
    pluginDescriptorFilePath: String,
    @Language("XML") pluginDescriptorContent: String,
  ): PsiFile {
    PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, tempDirFixture.findOrCreateDir(moduleName))
    return addXmlFile(pluginDescriptorFilePath, pluginDescriptorContent)
  }

  private fun CodeInsightTestFixture.addModule(name: String, path: String): Module {
    return PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), name, tempDirFixture.findOrCreateDir(path))
  }

  private fun CodeInsightTestFixture.addModuleWithResourcesRoot(name: String, path: String, resourcesDirName: String): Module {
    return addModule(name, path).apply {
      val resourcesDir = myFixture.tempDirFixture.findOrCreateDir("$path/$resourcesDirName")
      PsiTestUtil.addSourceRoot(this, resourcesDir, JavaResourceRootType.RESOURCE)
    }
  }

  private fun CodeInsightTestFixture.addXmlFile(relativePath: String, @Language("XML") fileText: String): PsiFile {
    return addFileToProject(relativePath, fileText)
  }

  private fun CodeInsightTestFixture.addSingleFileResourceRoot(
    module: Module,
    relativePath: String,
    @Language("XML") fileText: String,
    relativeOutputPath: String = "",
  ): PsiFile {
    return addXmlFile(relativePath, fileText).also {
      PsiTestUtil.addSourceRoot(module, it.virtualFile, JavaResourceRootType.RESOURCE, JavaResourceRootProperties(relativeOutputPath, false))
    }
  }

  private fun CodeInsightTestFixture.assertThatResolvedElementIsFileWithPath(
    filePath: String,
    @Language("XML") fileText: String,
  ): AbstractStringAssert<*> {
    myFixture.configureFromExistingVirtualFile(addXmlFile(filePath, fileText).virtualFile)
    val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
    assertThat(reference).isNotNull()
    val element = reference!!.resolve()
    return assertThat(element)
      .isNotNull()
      .isInstanceOf(XmlFile::class.java)
      .extracting { (it as XmlFile).virtualFile.path }
      .asString()
  }

}
