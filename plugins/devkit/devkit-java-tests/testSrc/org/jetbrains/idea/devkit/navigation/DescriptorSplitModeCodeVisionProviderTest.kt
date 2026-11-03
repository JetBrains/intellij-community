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
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class DescriptorSplitModeCodeVisionProviderTest {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    CodeVisionTestUtil.setUp(myFixture)
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
    myFixture.configureFromTempProjectFile("root.plugin/plugin.xml")

    val entry = runReadActionBlocking { computeCodeVisionEntry() }
    assertInstanceOf(ClickableTextCodeVisionEntry::class.java, entry)
    val reasonHtml = computeReasonHtml()
    assertEquals(
      "Backend plugin Can be installed into: backend IDE " +
      "Backend dependency: " +
      "plugin.xml module root.plugin " +
      "\u2193 depends on " +
      "dependency.module.xml " +
      "\u2193 depends on " +
      "intellij.platform.backend",
      toPlainText(reasonHtml.html),
    )
    assertEquals(
      listOf(DescriptorLink("plugin.xml", "root.plugin"), DescriptorLink("dependency.module.xml", "dependency.module")),
      reasonHtml.links,
    )
    assertEquals(setOf("plugin", "descriptor", "backend"), reasonHtml.icons.keys)

    val resolvedDescriptors = runReadActionBlocking { resolveDescriptors(myFixture.project, reasonHtml.links) }
    assertEquals(
      mapOf(reasonHtml.links[0] to "root.plugin/plugin.xml", reasonHtml.links[1] to "dependency.module/dependency.module.xml"),
      resolvedDescriptors.mapValues { (_, resolved) -> "${resolved.file.parent.name}/${resolved.file.name}" },
    )
    val reasonHtmlWithFileIcons = computeReason().toHtml(resolvedDescriptors.mapValues { it.value.icon })
    assertEquals(
      setOf("file:root.plugin/plugin.xml", "file:dependency.module/dependency.module.xml", "backend"),
      reasonHtmlWithFileIcons.icons.keys,
    )
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
    myFixture.configureFromTempProjectFile("test.module/test.module.xml")

    assertEquals(
      "LiteFrontend content module Can be installed into: lite frontend IDE " +
      "Frontend dependency: " +
      "test.module.xml " +
      "\u2193 depends on " +
      "intellij.platform.frontend " +
      "No dependency on intellij.platform.rpc was found. The module can be installed only into a lite frontend.",
      toPlainText(computeReasonHtml().html),
    )
  }

  @Test
  fun testReasonPopupShowsPredefinedDependencyAsOneNode() {
    val service = SplitModeApiRestrictionsService.getInstance(myFixture.project)
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }
    createDeclaringModule()
    val frontendModule = PsiTestUtil.addModule(
      myFixture.project,
      PluginModuleType.getInstance(),
      "intellij.platform.frontend",
      myFixture.tempDirFixture.findOrCreateDir("intellij.platform.frontend"),
    )
    PsiTestUtil.addSourceRoot(
      frontendModule,
      myFixture.tempDirFixture.findOrCreateDir("intellij.platform.frontend/resources"),
      JavaResourceRootType.RESOURCE,
    )
    myFixture.addFileToProject("intellij.platform.frontend/resources/intellij.platform.frontend.xml", "<idea-plugin/>")
    createModuleWithModuleDescriptor("test.module", "test.module.xml", """
      <idea-plugin>
        <dependencies>
          <module name="intellij.platform.frontend"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromTempProjectFile("test.module/test.module.xml")

    val reasonHtml = computeReasonHtml()
    assertEquals(
      "LiteFrontend content module Can be installed into: lite frontend IDE " +
      "Frontend dependency: " +
      "test.module.xml " +
      "\u2193 depends on " +
      "intellij.platform.frontend predefined " +
      "No dependency on intellij.platform.rpc was found. The module can be installed only into a lite frontend.",
      toPlainText(reasonHtml.html),
    )
    assertEquals(setOf("descriptor", "frontend"), reasonHtml.icons.keys)
  }

  @Test
  fun testPreview() {
    val previewPath = "codeVisionProviders/${DescriptorSplitModeCodeVisionProvider.ID}/preview.xml"
    val previewText = DescriptorSplitModeCodeVisionProvider::class.java.classLoader.getResource(previewPath)!!.readText()
    myFixture.configureByText("preview.xml", previewText)

    val provider = DescriptorSplitModeCodeVisionProvider()
    provider.preparePreview(myFixture.editor, myFixture.file)
    val (range, entry) = runReadActionBlocking { provider.computeForEditor(myFixture.editor, myFixture.file) }.single()

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
      myFixture,
      expectedText,
      { sourceText ->
        createModuleWithModuleDescriptor(moduleName, descriptorName, sourceText)
        myFixture.configureFromTempProjectFile("$moduleName/$descriptorName")
      },
      false,
      *enabledProviderGroupIds.toTypedArray(),
    )
    if (expectedTooltip != null) {
      assertEquals(expectedTooltip, runReadActionBlocking { computeCodeVisionEntry() }.tooltip)
    }
  }

  private fun computeCodeVisionEntry(): CodeVisionEntry {
    return DescriptorSplitModeCodeVisionProvider().computeForEditor(myFixture.editor, myFixture.file).single().second
  }

  private fun computeReason(): DescriptorSplitModeReason {
    return runReadActionBlocking { getDescriptorSplitModeInlayInfo(myFixture.file as XmlFile) }!!.reason
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
      myFixture.project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
    myFixture.addFileToProject("$moduleName/$moduleDescriptorName", moduleDescriptorText)
  }
}
