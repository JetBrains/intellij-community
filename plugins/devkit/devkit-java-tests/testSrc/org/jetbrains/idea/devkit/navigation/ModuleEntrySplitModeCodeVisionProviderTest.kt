// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.codeVision.CodeVisionTestUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class ModuleEntrySplitModeCodeVisionProviderTest {
  private val projectFixture = codeInsightProjectFixture()
  private val codeInsightFixture by codeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    CodeVisionTestUtil.setUp(codeInsightFixture)
  }

  @Test
  fun testContentEntries() {
    createKindModules()

    testCodeVision("root.plugin", "plugin.xml", """
      <idea-plugin>
        <content>
          <module name="frontend.module"/>/*<# [Frontend] #>*/
          <module name="backend.module"/>/*<# [Backend] #>*/
          <module name="shared.module"/>/*<# [Shared] #>*/
          <module name="mixed.module"/>/*<# [Monolith] #>*/
          <module name="missing.module"/>
        </content>
      </idea-plugin>
      """.trimIndent())
  }

  @Test
  fun testDependencyEntries() {
    createKindModules()

    testCodeVision("consumer.module", "consumer.module.xml", """
      <idea-plugin>
        <dependencies>
          <module name="frontend.module"/>/*<# [Frontend] #>*/
          <module name="shared.module"/>/*<# [Shared] #>*/
          <module name="missing.module"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
  }

  @Test
  fun testNoEntriesOutsideOfPluginDescriptor() {
    createKindModules()

    testCodeVision("other.module", "other.xml", """
      <project>
        <module name="frontend.module"/>
      </project>
      """.trimIndent())
  }

  /**
   * The kind of the descriptor and the kinds of its `module` entries show together, in one code vision style.
   */
  @Test
  fun testDescriptorKindAndEntryKinds() {
    createKindModules()

    testCodeVision(
      "root.plugin", "plugin.xml", """
      <idea-plugin>/*<# [Frontend plugin] #>*/
        <dependencies>
          <module name="frontend.module"/>/*<# [Frontend] #>*/
        </dependencies>
        <content>
          <module name="shared.module"/>/*<# [Shared] #>*/
        </content>
      </idea-plugin>
      """.trimIndent(),
      enabledProviderGroupIds = listOf(DescriptorSplitModeCodeVisionProvider.ID, ModuleEntrySplitModeCodeVisionProvider.ID),
    )
  }

  @Test
  fun testTooltip() {
    createKindModules()
    configure("root.plugin", "plugin.xml", """
      <idea-plugin>
        <content>
          <module name="frontend.module"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    // The daemon runs the provider with a progress, and the provider needs one to load the restrictions.
    val entry = ProgressManager.getInstance().runProcess<CodeVisionEntry>({
      runReadActionBlocking {
        ModuleEntrySplitModeCodeVisionProvider().computeForEditor(codeInsightFixture.editor, codeInsightFixture.file).single().second
      }
    }, EmptyProgressIndicator())
    assertInstanceOf(ClickableTextCodeVisionEntry::class.java, entry)
    assertEquals(
      "Split mode compatibility: content module can be installed into frontend IDE. " +
      "Reason: Frontend dependency 'intellij.platform.frontend' from descriptor 'frontend.module.xml' in module 'frontend.module'",
      entry.tooltip,
    )
  }

  /**
   * The reason popup of an entry shows the descriptor in the editor as the current one, and gives the entry target a link.
   */
  @Test
  fun testReasonPopupLinksEntryTarget() {
    createKindModules()
    configure("root.plugin", "plugin.xml", """
      <idea-plugin>
        <content>
          <module name="frontend.module"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    val reason = runReadActionBlocking {
      val target = codeInsightFixture.findFileInTempDir("frontend.module/resources/frontend.module.xml")
      val targetFile = codeInsightFixture.psiManager.findFile(target) as XmlFile
      val currentFile = codeInsightFixture.file as XmlFile
      getDescriptorSplitModeInlayInfo(targetFile, "inlay.split.mode.module.kind.content.module", currentFile)!!.reason
    }
    val targetLink = DescriptorLink("frontend.module.xml", "frontend.module")
    assertEquals(targetLink, reason.analyzedDescriptor)
    assertEquals(DescriptorLink("plugin.xml", "root.plugin"), reason.currentDescriptor)
    assertTrue(targetLink in reason.toHtml().links, "The entry target has no link")
  }

  @Test
  fun testPreview() {
    val previewPath = "codeVisionProviders/${ModuleEntrySplitModeCodeVisionProvider.ID}/preview.xml"
    val previewText = ModuleEntrySplitModeCodeVisionProvider::class.java.classLoader.getResource(previewPath)!!.readText()
    codeInsightFixture.configureByText("preview.xml", previewText)

    val provider = ModuleEntrySplitModeCodeVisionProvider()
    provider.preparePreview(codeInsightFixture.editor, codeInsightFixture.file)
    val entries = runReadActionBlocking { provider.computeForEditor(codeInsightFixture.editor, codeInsightFixture.file) }

    assertEquals(listOf("Frontend", "Frontend", "Backend", "Shared"), entries.map { (it.second as TextCodeVisionEntry).text })
  }

  private fun testCodeVision(
    moduleName: String,
    descriptorName: String,
    @Language("XML") expectedText: String,
    enabledProviderGroupIds: List<String> = listOf(ModuleEntrySplitModeCodeVisionProvider.ID),
  ) {
    CodeVisionTestUtil.testProviders(
      codeInsightFixture,
      expectedText,
      { sourceText -> configure(moduleName, descriptorName, sourceText) },
      false,
      *enabledProviderGroupIds.toTypedArray(),
    )
  }

  private fun configure(moduleName: String, descriptorName: String, @Language("XML") text: String) {
    val path = if (descriptorName == "plugin.xml") "META-INF/$descriptorName" else descriptorName
    createModule(moduleName, path, text)
    codeInsightFixture.configureFromTempProjectFile("$moduleName/resources/$path")
  }

  /**
   * Creates one content module for each split-mode kind.
   */
  private fun createKindModules() {
    createContentModule("frontend.module", "intellij.platform.frontend", "intellij.platform.rpc")
    createContentModule("backend.module", "intellij.platform.backend")
    createContentModule("shared.module")
    createContentModule("mixed.module", "intellij.platform.frontend", "intellij.platform.backend")
  }

  private fun createContentModule(moduleName: String, vararg dependencies: String) {
    val entries = dependencies.joinToString("") { "<module name=\"$it\"/>" }
    createModule(moduleName, "$moduleName.xml", "<idea-plugin><dependencies>$entries</dependencies></idea-plugin>")
  }

  private fun createModule(moduleName: String, descriptorPath: String, text: String) {
    val module = PsiTestUtil.addModule(
      codeInsightFixture.project,
      PluginModuleType.getInstance(),
      moduleName,
      codeInsightFixture.tempDirFixture.findOrCreateDir(moduleName),
    )
    val resourceRoot = codeInsightFixture.tempDirFixture.findOrCreateDir("$moduleName/resources")
    PsiTestUtil.addSourceRoot(module, resourceRoot, JavaResourceRootType.RESOURCE)
    codeInsightFixture.addFileToProject("$moduleName/resources/$descriptorPath", text)
  }
}
