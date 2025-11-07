// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.findUsages

import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.module.PluginModuleType

class PluginAliasFindUsagesTest : JavaCodeInsightFixtureTestCase() {

  fun `test find aliases`() {
    addModule("intellij.test.plugin")
    myFixture.addXmlFile("intellij.test.plugin/META-INF/plugin.xml", """
      <idea-plugin>
        <id>PluginId</id>
        <module value="intellij.test.plugin.alias1"/>
        <module value="intellij.test.plugin.alias2"/>
      </idea-plugin>
      """.trimIndent()
    )
    addModule("intellij.test.module1")
    myFixture.addXmlFile("intellij.test.module1/intellij.test.module1.xml", """
      <idea-plugin>
        <dependencies>
          <plugin id="PluginId">
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    addModule("intellij.test.module2")
    myFixture.addXmlFile("intellij.test.module2/intellij.test.module2.xml", """
      <idea-plugin>
        <depends>intellij.test.plugin.alias1</depends>
      </idea-plugin>
      """.trimIndent()
    )
    addModule("intellij.test.module3")
    myFixture.addXmlFile("intellij.test.module3/intellij.test.module3.xml", """
      <idea-plugin>
        <incompatible-with>intellij.test.<caret>plugin.alias2</incompatible-with>
      </idea-plugin>
      """.trimIndent()
    )

    val usages = myFixture.testFindUsages("intellij.test.module3/intellij.test.module3.xml")
      .map { it.element!!.text }
    assertThat(usages)
      .containsExactlyInAnyOrder(
        "\"PluginId\"",
        "<depends>intellij.test.plugin.alias1</depends>",
        "<incompatible-with>intellij.test.plugin.alias2</incompatible-with>"
      )
  }

  private fun addModule(moduleName: String) {
    PsiTestUtil.addModule(
      project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
  }

  private fun CodeInsightTestFixture.addXmlFile(relativePath: String, @Language("XML") fileText: String): PsiFile {
    return this.addFileToProject(relativePath, fileText)
  }

}
