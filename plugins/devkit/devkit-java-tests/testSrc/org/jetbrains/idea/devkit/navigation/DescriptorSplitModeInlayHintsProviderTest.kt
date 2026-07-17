// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.module.PluginModuleType

@TestDataPath($$"$CONTENT_ROOT/testData/navigation/descriptorsIncludingContentModule")
internal class DescriptorSplitModeInlayHintsProviderTest : JavaCodeInsightFixtureTestCase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/descriptorsIncludingContentModule"

  fun testFrontendContentModuleInlay() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )

    testDescriptorInlay(
      descriptorPath = "test.module/test.module.xml",
      expectedInlayText = "Frontend content module",
      expectedTooltip = expectedTooltip(
        descriptorKind = "content module",
        compatibilityTarget = "frontend IDE",
        reason = "Frontend dependency 'intellij.platform.frontend' from descriptor 'test.module.xml' in module 'test.module'",
      ),
    )
  }

  fun testSharedContentModuleInlay() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea-plugin>
        <!-- any -->
      </idea-plugin>
      """.trimIndent()
    )

    testDescriptorInlay(
      descriptorPath = "test.module/test.module.xml",
      expectedInlayText = "Shared content module",
      expectedTooltip = expectedTooltip(
        descriptorKind = "content module",
        compatibilityTarget = "both frontend and backend IDE",
        reason = "No frontend or backend dependencies were found for descriptor 'test.module.xml' in module 'test.module'",
      ),
    )
  }

  fun testBackendPluginInlay() {
    createModuleWithModuleDescriptor("root.plugin", "plugin.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )

    testDescriptorInlay(
      descriptorPath = "root.plugin/plugin.xml",
      expectedInlayText = "Backend plugin",
      expectedTooltip = expectedTooltip(
        descriptorKind = "plugin",
        compatibilityTarget = "backend IDE",
        reason = "Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'root.plugin'",
      ),
    )
  }

  fun testMixedContentModuleInlayUsesMonolithPresentation() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )

    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )

    testDescriptorInlay(
      descriptorPath = "test.module/test.module.xml",
      expectedInlayText = "Monolith content module",
      expectedTooltip = expectedTooltip(
        descriptorKind = "content module",
        compatibilityTarget = "monolith IDE",
        reason = """
        Frontend dependency 'intellij.platform.frontend' from descriptor 'test.module.xml' in module 'test.module'

        Backend dependency 'intellij.platform.backend' from descriptor 'test.module.xml' in module 'test.module'
      """.trimIndent(),
      ),
    )
  }

  fun testMonolithPluginInlay() {
    createModuleWithModuleDescriptor("root.plugin", "plugin.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.monolith"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )

    testDescriptorInlay(
      descriptorPath = "root.plugin/plugin.xml",
      expectedInlayText = "Monolith plugin",
      expectedTooltip = expectedTooltip(
        descriptorKind = "plugin",
        compatibilityTarget = "monolith IDE",
        reason = "Monolith dependency 'intellij.platform.monolith' from descriptor 'plugin.xml' in module 'root.plugin'",
      ),
    )
  }

  fun testNonPluginXmlDescriptorInlay() {
    createModuleWithModuleDescriptor("root.plugin", "root.plugin.optional.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )

    testDescriptorInlay(
      descriptorPath = "root.plugin/root.plugin.optional.xml",
      expectedInlayText = "Backend XML descriptor",
      expectedTooltip = expectedTooltip(
        descriptorKind = "XML descriptor",
        compatibilityTarget = "backend IDE",
        reason = "Backend dependency 'intellij.platform.backend' from descriptor 'root.plugin.optional.xml' in module 'root.plugin'",
      ),
    )
  }

  fun testRegistryDisablesInlay() {
    Registry.get("devkit.split.mode.inlay.plugin.descriptor.kind").setValue(false, testRootDisposable)

    createModuleWithModuleDescriptor("root.plugin", "plugin.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )

    myFixture.configureFromTempProjectFile("root.plugin/plugin.xml")

    runInlayPass()

    assertEmpty(myFixture.editor.inlayModel.getInlineElementsInRange(0, myFixture.editor.document.textLength))
  }

  private fun createModuleWithModuleDescriptor(
    moduleName: String,
    moduleDescriptorName: String,
    @Language("XML") moduleDescriptorText: String,
  ) {
    PsiTestUtil.addModule(
      project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
    myFixture.addFileToProject("$moduleName/$moduleDescriptorName", moduleDescriptorText)
  }

  private fun testDescriptorInlay(
    descriptorPath: String,
    expectedInlayText: String,
    expectedTooltip: String,
  ) {
    val xmlFile = myFixture.configureFromTempProjectFile(descriptorPath) as XmlFile

    runInlayPass()

    val inlay = myFixture.editor.inlayModel
      .getInlineElementsInRange(0, myFixture.editor.document.textLength, DeclarativeInlayRenderer::class.java)
      .single()
    assertEquals(expectedInlayText, inlay.renderer.presentationLists.single().getText())
    assertEquals(expectedTooltip, inlay.renderer.toInlayData().single().tooltip)

    val inlayInfo = getDescriptorSplitModeInlayInfo(xmlFile)
    assertNotNull(inlayInfo)
    assertEquals(expectedInlayText, inlayInfo!!.text)
    assertEquals(expectedTooltip, inlayInfo.tooltip)
  }

  private fun expectedTooltip(descriptorKind: String, compatibilityTarget: String, reason: String): String {
    val normalizedReason = reason.lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
    return "Split mode compatibility: $descriptorKind can be installed into $compatibilityTarget. Reason: $normalizedReason"
  }

  private fun runInlayPass() {
    val providerInfo = InlayProviderPassInfo(DescriptorSplitModeInlayHintsProvider(), "test.inlay.provider", emptyMap())
    val pass = ActionUtil.underModalProgress(project, "") {
      DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(providerInfo), isPreview = false)
    }
    pass.setContext(myFixture.file.codeInsightContext)
    ActionUtil.underModalProgress(project, "") {
      pass.doCollectInformation(EmptyProgressIndicator())
    }
    pass.applyInformationToEditor()
  }

  private fun InlayPresentationList.getText(): String {
    return getEntries().joinToString(separator = "") { (it as TextInlayPresentationEntry).text }
  }
}
