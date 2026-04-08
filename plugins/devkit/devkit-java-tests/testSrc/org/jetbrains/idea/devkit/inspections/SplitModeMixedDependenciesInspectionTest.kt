// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeMixedDependenciesInspection
import kotlin.time.Duration.Companion.seconds

internal class SplitModeMixedDependenciesInspectionTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    val service = SplitModeApiRestrictionsService.getInstance()
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }

    PsiTestUtil.addResourceContentToRoots(module, myFixture.tempDirFixture.findOrCreateDir("resources"), false)
    myFixture.enableInspections(SplitModeMixedDependenciesInspection())
  }

  fun testMixedModuleDependenciesInPluginXml() {
    configurePluginXml(
      """
        <idea-plugin>
          <dependencies>
            <error descr="This dependency list mixes frontend-only dependencies (intellij.platform.frontend) and backend-only dependencies (intellij.platform.backend)"><module name="intellij.platform.frontend"/></error>
            <module name="intellij.platform.rpc.split"/>
            <error descr="This dependency list mixes frontend-only dependencies (intellij.platform.frontend) and backend-only dependencies (intellij.platform.backend)"><module name="intellij.platform.backend"/></error>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testMixedPluginDependenciesInPluginXml() {
    configurePluginXml(
      """
        <idea-plugin>
          <dependencies>
            <error descr="This dependency list mixes frontend-only dependencies (com.intellij.jetbrains.client) and backend-only dependencies (com.jetbrains.remoteDevelopment)"><plugin id="com.intellij.jetbrains.client"/></error>
            <error descr="This dependency list mixes frontend-only dependencies (com.intellij.jetbrains.client) and backend-only dependencies (com.jetbrains.remoteDevelopment)"><plugin id="com.jetbrains.remoteDevelopment"/></error>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testMixedDependenciesInContentModuleXml() {
    configureContentModuleXml(
      """
        <idea-plugin>
          <dependencies>
            <error descr="This dependency list mixes frontend-only dependencies (intellij.platform.frontend.split) and backend-only dependencies (intellij.platform.kernel.backend)"><module name="intellij.platform.frontend.split"/></error>
            <error descr="This dependency list mixes frontend-only dependencies (intellij.platform.frontend.split) and backend-only dependencies (intellij.platform.kernel.backend)"><module name="intellij.platform.kernel.backend"/></error>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testOnlyFrontendDependenciesAreAllowed() {
    configurePluginXml(
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.plugins.frontend.split"/>
            <plugin id="com.intellij.jetbrains.client"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  private fun configurePluginXml(pluginXmlContent: String) {
    configureDescriptor("resources/META-INF/plugin.xml", pluginXmlContent)
  }

  private fun configureContentModuleXml(pluginXmlContent: String) {
    configureDescriptor("resources/light_idea_test_case.xml", pluginXmlContent)
  }

  private fun configureDescriptor(relativePath: String, pluginXmlContent: String) {
    val descriptor = myFixture.addFileToProject(relativePath, pluginXmlContent)
    myFixture.configureFromExistingVirtualFile(descriptor.virtualFile)
  }
}
