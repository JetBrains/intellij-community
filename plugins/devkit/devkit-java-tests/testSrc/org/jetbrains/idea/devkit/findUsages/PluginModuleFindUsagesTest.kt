// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.findUsages

import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.module.PluginModuleType

class PluginModuleFindUsagesTest : JavaCodeInsightFixtureTestCase() {

  fun `test find modules`() {
    addModule("intellij.test.module1")
    val moduleFile = myFixture.addXmlFile("intellij.test.module1/intellij.test.module1.xml", """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent()
    )
    addModule("intellij.test.plugin1")
    myFixture.addXmlFile("intellij.test.plugin1/plugin.xml", """
      <idea-plugin>
        <content>
          <module name="intellij.test.module1"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
    addModule("intellij.test.plugin2")
    myFixture.addXmlFile("intellij.test.plugin2/plugin.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.test.module1"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )

    val usages = myFixture.findUsages(moduleFile)
    assertThat(usages)
      .hasSize(2)
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
