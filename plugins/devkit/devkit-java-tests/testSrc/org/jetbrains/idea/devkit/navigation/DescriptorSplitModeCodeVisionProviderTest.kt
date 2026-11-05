// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.codeVision.CodeVisionTestUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.ResolvedModuleKind
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService.ModuleKind
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class DescriptorSplitModeCodeVisionProviderTest {
  private val projectFixture = codeInsightProjectFixture()
  private val codeInsightFixture by codeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    CodeVisionTestUtil.setUp(codeInsightFixture)
  }

  @Test
  fun testFrontendContentModule() {
    createDeclaringModule()

    testCodeVision(
      "test.module", "test.module.xml", """
      <idea-plugin>/*<# [Frontend content module] #>*/
        <dependencies>
          <module name="intellij.platform.frontend"/>
          <module name="intellij.platform.rpc"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = expectedTooltip(
        descriptorKind = "content module",
        compatibilityTarget = "frontend IDE",
        reason = "Frontend dependency 'intellij.platform.frontend' from descriptor 'test.module.xml' in module 'test.module'",
      ),
    )
  }

  @Test
  fun testLiteFrontendContentModule() {
    createDeclaringModule()

    testCodeVision(
      "test.module", "test.module.xml", """
      <idea-plugin>/*<# [LiteFrontend content module] #>*/
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = expectedTooltip(
        descriptorKind = "content module",
        compatibilityTarget = "lite frontend IDE",
        reason = """
          Frontend dependency 'intellij.platform.frontend' from descriptor 'test.module.xml' in module 'test.module'
          No dependency on 'intellij.platform.rpc' was found
        """.trimIndent(),
      ),
    )
  }

  @Test
  fun testSharedContentModule() {
    createDeclaringModule()

    testCodeVision(
      "test.module", "test.module.xml", """
      <idea-plugin>/*<# [Shared content module] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = expectedTooltip(
        descriptorKind = "content module",
        compatibilityTarget = "both frontend and backend IDE",
        reason = "No frontend or backend dependencies were found for descriptor 'test.module.xml' in module 'test.module'",
      ),
    )
  }

  @Test
  fun testBackendPlugin() {
    testCodeVision(
      "root.plugin", "plugin.xml", """
      <idea-plugin>/*<# [Backend plugin] #>*/
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = expectedTooltip(
        descriptorKind = "plugin",
        compatibilityTarget = "backend IDE",
        reason = "Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'root.plugin'",
      ),
    )
  }

  @Test
  fun testMixedContentModuleUsesMonolithPresentation() {
    createDeclaringModule()

    testCodeVision(
      "test.module", "test.module.xml", """
      <idea-plugin>/*<# [Monolith content module] #>*/
        <dependencies>
          <module name="intellij.platform.frontend"/>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent(),
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

  @Test
  fun testMonolithPlugin() {
    testCodeVision(
      "root.plugin", "plugin.xml", """
      <idea-plugin>/*<# [Monolith plugin] #>*/
        <dependencies>
          <module name="intellij.platform.monolith"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = expectedTooltip(
        descriptorKind = "plugin",
        compatibilityTarget = "monolith IDE",
        reason = "Monolith dependency 'intellij.platform.monolith' from descriptor 'plugin.xml' in module 'root.plugin'",
      ),
    )
  }

  @Test
  fun testNonPluginXmlDescriptor() {
    testCodeVision(
      "root.plugin", "root.plugin.optional.xml", """
      <idea-plugin>/*<# [Backend XML descriptor] #>*/
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent(),
      expectedTooltip = expectedTooltip(
        descriptorKind = "XML descriptor",
        compatibilityTarget = "backend IDE",
        reason = "Backend dependency 'intellij.platform.backend' from descriptor 'root.plugin.optional.xml' in module 'root.plugin'",
      ),
    )
  }

  @Test
  fun testSplitModeKindGoesBeforeIncludingDescriptors() {
    createDeclaringModule()

    testCodeVision(
      "test.module", "test.module.xml", """
      <idea-plugin>/*<# [Shared content module   Included in 1 descriptor] #>*/
        <!-- any -->
      </idea-plugin>
      """.trimIndent(),
      enabledProviderGroupIds = listOf(DescriptorSplitModeCodeVisionProvider.ID, DescriptorsIncludingContentModuleCodeVisionProvider.ID),
    )
  }

  @Test
  fun testReasonPopupShowsTransitiveDependencyChain() {
    createModuleWithModuleDescriptor("dependency.module", "dependency.module.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    createModuleWithModuleDescriptor("root.plugin", "plugin.xml", """
      <idea-plugin>
        <dependencies>
          <module name="dependency.module"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    codeInsightFixture.configureFromTempProjectFile("root.plugin/plugin.xml")

    val entry = runReadActionBlocking { computeCodeVisionEntry() }
    assertInstanceOf(ClickableTextCodeVisionEntry::class.java, entry)
    (val html, val links, val icons) = computeReasonHtml()
    assertEquals(
      "Backend plugin Can be installed into: backend IDE " +
      "Backend dependency: " +
      "plugin.xml (current) " +
      "\u2193 depends on " +
      "dependency.module " +
      "\u2193 depends on " +
      "intellij.platform.backend",
      toPlainText(html),
    )
    assertEquals(
      listOf(DescriptorLink("dependency.module.xml", "dependency.module")),
      links,
    )
    assertEquals(setOf("plugin", "descriptor", "backend"), icons.keys)

    val currentFile = DescriptorLink("plugin.xml", "root.plugin")
    val resolvedDescriptors = runReadActionBlocking { resolveDescriptors(codeInsightFixture.project, links + currentFile) }
    assertEquals(
      mapOf(links[0] to "dependency.module/dependency.module.xml", currentFile to "root.plugin/plugin.xml"),
      resolvedDescriptors.mapValues { [_, resolved] -> "${resolved.file.parent.name}/${resolved.file.name}" },
    )
    val reasonHtmlWithFileIcons = computeReason().toHtml(resolvedDescriptors.mapValues { it.value.icon })
    assertEquals(
      setOf("file:root.plugin/plugin.xml", "file:dependency.module/dependency.module.xml", "backend"),
      reasonHtmlWithFileIcons.icons.keys,
    )
  }

  @Test
  fun testReasonPopupShowsPluginIdOfPluginDescriptor() {
    createModuleWithModuleDescriptor("other.plugin", "plugin.xml", """
      <idea-plugin>
        <id>com.example.other</id>
        <dependencies>
          <module name="intellij.platform.backend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    createModuleWithModuleDescriptor("root.plugin", "plugin.xml", """
      <idea-plugin>
        <id>com.example.root</id>
        <dependencies>
          <plugin id="com.example.other"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    codeInsightFixture.configureFromTempProjectFile("root.plugin/plugin.xml")

    val reasonHtml = computeReasonHtml()
    assertEquals(
      "Backend plugin Can be installed into: backend IDE " +
      "Backend dependency: " +
      "plugin.xml plugin com.example.root (current) " +
      "\u2193 depends on " +
      "plugin.xml plugin com.example.other " +
      "\u2193 depends on " +
      "intellij.platform.backend",
      toPlainText(reasonHtml.html),
    )
    assertEquals(listOf(DescriptorLink("plugin.xml", "other.plugin")), reasonHtml.links)
  }

  @Test
  fun testReasonPopupShowsMissingRpcDependency() {
    createDeclaringModule()
    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    codeInsightFixture.configureFromTempProjectFile("test.module/test.module.xml")

    assertEquals(
      "LiteFrontend content module Can be installed into: lite frontend IDE " +
      "Frontend dependency: " +
      "test.module (current) " +
      "\u2193 depends on " +
      "intellij.platform.frontend " +
      "No dependency on intellij.platform.rpc was found. The module can be installed only into a lite frontend.",
      toPlainText(computeReasonHtml().html),
    )
  }

  @Test
  fun testReasonPopupShowsPredefinedDependencyAsOneNode() {
    loadSplitModeRestrictions()
    createDeclaringModule()
    createPredefinedModule("intellij.platform.frontend", "<idea-plugin/>")
    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    codeInsightFixture.configureFromTempProjectFile("test.module/test.module.xml")

    (val html, val icons) = computeReasonHtml()
    assertEquals(
      "LiteFrontend content module Can be installed into: lite frontend IDE " +
      "Frontend dependency: " +
      "test.module (current) " +
      "\u2193 depends on " +
      "intellij.platform.frontend (predefined) " +
      "No dependency on intellij.platform.rpc was found. The module can be installed only into a lite frontend.",
      toPlainText(html),
    )
    assertEquals(setOf("descriptor", "frontend"), icons.keys)
  }

  @Test
  fun testReasonPopupShowsAliasAndModuleOfPredefinedDependency() {
    loadSplitModeRestrictions()
    createDeclaringModule()
    createPredefinedModule("intellij.platform.backend", """
      <idea-plugin>
        <module value="test.backend.alias"/>
      </idea-plugin>
      """.trimIndent()
    )
    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea-plugin>
        <dependencies>
          <plugin id="test.backend.alias"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    codeInsightFixture.configureFromTempProjectFile("test.module/test.module.xml")

    (val html, val links) = computeReasonHtml()
    assertEquals(
      "Backend content module Can be installed into: backend IDE " +
      "Backend dependency: " +
      "test.module (current) " +
      "\u2193 depends on test.backend.alias " +
      "intellij.platform.backend (predefined)",
      toPlainText(html),
    )
    val predefinedDescriptor = DescriptorLink("intellij.platform.backend.xml", "intellij.platform.backend")
    assertEquals(listOf(predefinedDescriptor), links)
    val resolvedDescriptors = runReadActionBlocking { resolveDescriptors(codeInsightFixture.project, links) }
    assertEquals("intellij.platform.backend.xml", resolvedDescriptors[predefinedDescriptor]?.file?.name)
  }

  @Test
  fun testAnalysisBeforeRestrictionsLoadIsStaleAfterLoad() {
    createDependencyOnPredefinedBackendModule()
    assertFalse(SplitModeApiRestrictionsService.getInstance(codeInsightFixture.project).isLoaded())
    assertEquals(ModuleKind.SHARED, computeResolvedModuleKind().kind)

    loadSplitModeRestrictions()

    assertEquals(
      "Backend content module Can be installed into: backend IDE " +
      "Backend dependency: " +
      "test.module (current) " +
      "↓ depends on " +
      "intellij.platform.vcs (predefined)",
      toPlainText(computeReasonHtml().html),
    )
  }

  @Test
  fun testCodeVisionLoadsRestrictions() {
    createDependencyOnPredefinedBackendModule()
    assertFalse(SplitModeApiRestrictionsService.getInstance(codeInsightFixture.project).isLoaded())

    val entry = runReadActionBlocking { computeCodeVisionEntry() }
    assertEquals("Backend content module", (entry as ClickableTextCodeVisionEntry).text)
  }

  @Test
  fun testPreview() {
    val previewPath = "codeVisionProviders/${DescriptorSplitModeCodeVisionProvider.ID}/preview.xml"
    val previewText = DescriptorSplitModeCodeVisionProvider::class.java.classLoader.getResource(previewPath)!!.readText()
    codeInsightFixture.configureByText("preview.xml", previewText)

    val provider = DescriptorSplitModeCodeVisionProvider()
    provider.preparePreview(codeInsightFixture.editor, codeInsightFixture.file)
    val [range, entry] = runReadActionBlocking { provider.computeForEditor(codeInsightFixture.editor, codeInsightFixture.file) }.single()

    assertEquals(0, range.startOffset)
    assertEquals("Backend plugin", (entry as TextCodeVisionEntry).text)
  }

  private fun testCodeVision(
    moduleName: String,
    descriptorName: String,
    @Language("XML") expectedText: String,
    expectedTooltip: String? = null,
    enabledProviderGroupIds: List<String> = listOf(DescriptorSplitModeCodeVisionProvider.ID),
  ) {
    CodeVisionTestUtil.testProviders(
      codeInsightFixture,
      expectedText,
      { sourceText ->
        createModuleWithModuleDescriptor(moduleName, descriptorName, sourceText)
        codeInsightFixture.configureFromTempProjectFile("$moduleName/$descriptorName")
      },
      false,
      *enabledProviderGroupIds.toTypedArray(),
    )
    if (expectedTooltip != null) {
      assertEquals(expectedTooltip, runReadActionBlocking { computeCodeVisionEntry() }.tooltip)
    }
  }

  private fun computeCodeVisionEntry(): CodeVisionEntry {
    return DescriptorSplitModeCodeVisionProvider().computeForEditor(codeInsightFixture.editor, codeInsightFixture.file).single().second
  }

  private fun loadSplitModeRestrictions() {
    val service = SplitModeApiRestrictionsService.getInstance(codeInsightFixture.project)
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }
  }

  /**
   * Creates a module with a name from the predefined module kinds, with its descriptor in a resource root.
   */
  private fun createPredefinedModule(moduleName: String, @Language("XML") descriptorText: String) {
    val module = PsiTestUtil.addModule(
      codeInsightFixture.project,
      PluginModuleType.getInstance(),
      moduleName,
      codeInsightFixture.tempDirFixture.findOrCreateDir(moduleName),
    )
    PsiTestUtil.addSourceRoot(module, codeInsightFixture.tempDirFixture.findOrCreateDir("$moduleName/resources"), JavaResourceRootType.RESOURCE)
    codeInsightFixture.addFileToProject("$moduleName/resources/$moduleName.xml", descriptorText)
  }

  /**
   * Creates `test.module` with a dependency on the predefined backend module `intellij.platform.vcs` and opens its descriptor.
   * Only the predefined module kinds make `test.module` a backend module.
   */
  private fun createDependencyOnPredefinedBackendModule() {
    createDeclaringModule()
    createPredefinedModule("intellij.platform.vcs", "<idea-plugin/>")
    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.vcs"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    codeInsightFixture.configureFromTempProjectFile("test.module/test.module.xml")
  }

  private fun computeResolvedModuleKind(): ResolvedModuleKind {
    return runReadActionBlocking {
      SplitModeModuleKindResolver.getOrComputeDescriptorAnalysis(codeInsightFixture.file as XmlFile).resolvedModuleKind
    }
  }

  private fun computeReason(): DescriptorSplitModeReason {
    return runReadActionBlocking { getDescriptorSplitModeInlayInfo(codeInsightFixture.file as XmlFile) }!!.reason
  }

  private fun computeReasonHtml(): DescriptorSplitModeReasonHtml = computeReason().toHtml()

  private fun toPlainText(html: String): String {
    return StringUtil.unescapeXmlEntities(StringUtil.removeHtmlTags(html, true)).replace(Regex("\\s+"), " ").trim()
  }

  private fun expectedTooltip(descriptorKind: String, compatibilityTarget: String, reason: String): String {
    val normalizedReason = reason.lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
    return "Split mode compatibility: $descriptorKind can be installed into $compatibilityTarget. Reason: $normalizedReason"
  }

  private fun createDeclaringModule() {
    createModuleWithModuleDescriptor("declaring.module", "declaring.module.xml", """
      <idea-plugin>
        <content>
          <module name="test.module"/>
        </content>
      </idea-plugin>
      """.trimIndent()
    )
  }

  private fun createModuleWithModuleDescriptor(
    moduleName: String,
    moduleDescriptorName: String,
    @Language("XML") moduleDescriptorText: String,
  ) {
    PsiTestUtil.addModule(
      codeInsightFixture.project,
      PluginModuleType.getInstance(),
      moduleName,
      codeInsightFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
    codeInsightFixture.addFileToProject("$moduleName/$moduleDescriptorName", moduleDescriptorText)
  }
}
