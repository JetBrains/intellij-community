// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.module.JavaModuleType
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ObjectArrayAssert
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.module.PluginModuleType

class PluginXmlContentModuleCompletionTest : JavaCodeInsightFixtureTestCase() {

  fun `test should complete modules in plugin content`() {
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

    myFixture.assertThatCompletion(
      "com.example.plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="<caret>"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    ).extracting<String> { it.lookupString }
      .containsExactlyInAnyOrder(
        "com.example.module1",
        "com.example.module2",
        "com.example.module3"
      )
  }

  fun `test should complete modules in plugin content Gradle module structure`() {
    myFixture.addModule("SplitPlugin", "split-plugin")

    myFixture.addModule("SplitPlugin.backend", "split-plugin/backend")
    myFixture.addModule("SplitPlugin.frontend", "split-plugin/frontend")
    myFixture.addModule("SplitPlugin.shared", "split-plugin/shared")

    myFixture.addModule("SplitPlugin.main", "split-plugin/src/main")
    myFixture.addModule("SplitPlugin.backend.main", "split-plugin/backend/src/main")
    myFixture.addModule("SplitPlugin.frontend.main", "split-plugin/frontend/src/main")
    myFixture.addModule("SplitPlugin.shared.main", "split-plugin/shared/src/main")

    myFixture.addXmlFile(
      "split-plugin/backend/src/main/resources/SplitPlugin.backend.xml",
      """
      <idea-plugin>
          <dependencies>
              <module name="intellij.platform.backend"/>
              <module name="intellij.platform.kernel.backend"/>
              <module name="SplitPlugin.shared"/>
          </dependencies>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "split-plugin/frontend/src/main/resources/SplitPlugin.frontend.xml",
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

    myFixture.assertThatCompletion(
      "split-plugin/src/main/resources/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.splitplugin</id>
        <content>
          <module name="<caret>"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    ).extracting<String> { it.lookupString }
      .containsExactlyInAnyOrder(
        "SplitPlugin.backend",
        "SplitPlugin.frontend",
        "SplitPlugin.shared"
      )
  }

  private fun CodeInsightTestFixture.addModuleWithPluginDescriptor(
    moduleName: String,
    pluginDescriptorFilePath: String,
    @Language("XML") pluginDescriptorContent: String,
  ): PsiFile {
    PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, tempDirFixture.findOrCreateDir(moduleName))
    return addXmlFile(pluginDescriptorFilePath, pluginDescriptorContent)
  }

  private fun CodeInsightTestFixture.addModule(name: String, path: String) {
    PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), name, tempDirFixture.findOrCreateDir(path))
  }

  private fun CodeInsightTestFixture.addXmlFile(relativePath: String, @Language("XML") fileText: String): PsiFile {
    return addFileToProject(relativePath, fileText)
  }

  private fun CodeInsightTestFixture.assertThatCompletion(
    filePath: String,
    @Language("XML") fileText: String,
  ): ObjectArrayAssert<LookupElement> {
    myFixture.configureFromExistingVirtualFile(addXmlFile(filePath, fileText).virtualFile)
    return assertThat(myFixture.completeBasic())
  }

}
