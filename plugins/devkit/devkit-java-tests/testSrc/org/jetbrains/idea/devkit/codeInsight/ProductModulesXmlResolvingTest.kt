// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType

class ProductModulesXmlResolvingTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
  }

  override fun tearDown() {
    try {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test bundled plugin resolves to plugin xml in directory root`() {
    val pluginXml = myFixture.addFileToProject("plugin/resources/META-INF/plugin.xml", "<idea-plugin/>").virtualFile
    val module = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "intellij.example.plugin", pluginXml.parent.parent.parent)
    PsiTestUtil.addSourceRoot(module, pluginXml.parent.parent, JavaResourceRootType.RESOURCE)

    assertThat(resolveBundledPlugin()).isEqualTo(pluginXml)
  }

  fun `test bundled plugin resolves to plugin xml in single-file root`() {
    val pluginXml = myFixture.addFileToProject("plugin/resources/META-INF/plugin.xml", "<idea-plugin/>").virtualFile
    val module = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "intellij.example.plugin", pluginXml.parent.parent.parent)
    PsiTestUtil.addSourceRoot(module, pluginXml, JavaResourceRootType.RESOURCE, JavaResourceRootProperties("META-INF", false))

    assertThat(resolveBundledPlugin()).isEqualTo(pluginXml)
  }

  fun `test included module resolves to product modules xml in directory root with output path that contains dots`() {
    val productModulesXml = myFixture.addFileToProject("product/resources/product-modules.xml", "<product-modules/>").virtualFile
    val module = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "intellij.example.product", productModulesXml.parent.parent)
    PsiTestUtil.addSourceRoot(module, productModulesXml.parent, JavaResourceRootType.RESOURCE,
                              JavaResourceRootProperties("META-INF/intellij.example.product", false))

    assertThat(resolveIncludedModule()).isEqualTo(productModulesXml)
  }

  fun `test included module resolves to product modules xml in single-file root`() {
    val productModulesXml = myFixture.addFileToProject("product/resources/product-modules.xml", "<product-modules/>").virtualFile
    val module = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "intellij.example.product", productModulesXml.parent.parent)
    PsiTestUtil.addSourceRoot(module, productModulesXml, JavaResourceRootType.RESOURCE,
                              JavaResourceRootProperties("META-INF/intellij.example.product", false))

    assertThat(resolveIncludedModule()).isEqualTo(productModulesXml)
  }

  private fun resolveBundledPlugin() = resolveAtCaret("""
      <product-modules>
        <bundled-plugins>
          <module>intellij.<caret>example.plugin</module>
        </bundled-plugins>
      </product-modules>
    """.trimIndent())

  private fun resolveIncludedModule() = resolveAtCaret("""
      <product-modules>
        <include>
          <from-module>intellij.<caret>example.product</from-module>
        </include>
      </product-modules>
    """.trimIndent())

  private fun resolveAtCaret(text: String) = myFixture.run {
    configureByText("product-modules.xml", text)
    (getReferenceAtCaretPositionWithAssertion().resolve() as? PsiFile)?.virtualFile
  }
}
