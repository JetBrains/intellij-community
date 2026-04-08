// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeXmlApiUsageInspection
import kotlin.time.Duration.Companion.seconds

internal class SplitModeXmlApiUsageInspectionTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    val service = SplitModeApiRestrictionsService.getInstance()
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }

    PsiTestUtil.addResourceContentToRoots(module, myFixture.tempDirFixture.findOrCreateDir("resources"), false)
    myFixture.enableInspections(SplitModeXmlApiUsageInspection())
  }

  fun testFrontendExtensionInBackendModule() {
    configurePluginXml(
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.fileEditorProvider' can only be used in 'frontend' module type">fileEditorProvider</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInFrontendModule() {
    configurePluginXml(
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' can only be used in 'backend' module type">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testFrontendAndBackendExtensionsInSharedModule() {
    configurePluginXml(
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
            <module name="intellij.platform.ide"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.fileEditorProvider' can only be used in 'frontend' module type">fileEditorProvider</warning>/>
            <<warning descr="'com.intellij.localInspection' can only be used in 'backend' module type">localInspection</warning>/>
            <lang.parserDefinition/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInFrontendContentModule() {
    configureContentModuleXml(
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' can only be used in 'backend' module type">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testFrontendAndBackendExtensionsInMixedModule() {
    configurePluginXml(
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.fileEditorProvider' can only be used in 'frontend' module type">fileEditorProvider</warning>/>
            <<warning descr="'com.intellij.localInspection' can only be used in 'backend' module type">localInspection</warning>/>
          </extensions>
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
