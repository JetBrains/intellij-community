// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeMixedDependenciesInspection
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeXmlApiUsageInspection
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService.ModuleKind
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.Assert
import kotlin.time.Duration.Companion.seconds

internal class SplitModeLiteModuleKindTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    RegistryManager.getInstance().get("devkit.split.mode.analysis.containing.plugins")
      .setValue(true, testRootDisposable)
    RegistryManager.getInstance().get("devkit.split.mode.inspections.enable.in.implicit.module.kind")
      .setValue(true, testRootDisposable)

    val service = SplitModeApiRestrictionsService.getInstance(project)
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }

    myFixture.enableInspections(SplitModeXmlApiUsageInspection(), SplitModeMixedDependenciesInspection())
  }

  fun testFrontendModuleWithoutRpcIsLiteFrontendAndNotInspected() {
    val descriptor = addContentModule(
      "lite.module.frontend",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <localInspection/>
          </extensions>
        </idea-plugin>
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(descriptor.virtualFile)

    myFixture.checkHighlighting()

    val resolvedModuleKind = SplitModeModuleKindResolver.getOrComputeDescriptorAnalysis(descriptor as XmlFile).resolvedModuleKind
    Assert.assertEquals(ModuleKind.LITE_FRONTEND, resolvedModuleKind.kind)
    Assert.assertEquals(
      """
        Frontend dependency 'intellij.platform.frontend' from descriptor 'lite.module.frontend.xml' in module 'lite.module.frontend'
        No dependency on 'intellij.platform.rpc' was found
      """.trimIndent(),
      resolvedModuleKind.reasoning,
    )
  }

  fun testBackendModuleWithoutRpcIsBackend() {
    val descriptor = addContentModule(
      "regular.module.backend",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.BACKEND, descriptor)
  }

  fun testFrontendModuleWithRpcIsFrontend() {
    val descriptor = addContentModule(
      "regular.module.frontend",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.rpc"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.FRONTEND, descriptor)
  }

  fun testFrontendModuleWithTransitiveRpcIsFrontend() {
    addContentModule(
      "rpc.user",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.rpc"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )
    val descriptor = addContentModule(
      "regular.module.frontend",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="rpc.user"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.FRONTEND, descriptor)
  }

  fun testPredefinedFrontendDependencyImpliesRpc() {
    val descriptor = addContentModule(
      "regular.module.frontend",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend.split"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.FRONTEND, descriptor)
  }

  fun testResolvedPredefinedFrontendDependencyImpliesRpc() {
    addContentModule("intellij.platform.frontend.split", "<idea-plugin/>")
    val descriptor = addContentModule(
      "regular.module.frontend",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend.split"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.FRONTEND, descriptor)
  }

  fun testResolvedPlatformFrontendDependencyDoesNotImplyRpc() {
    addContentModule("intellij.platform.frontend", "<idea-plugin/>")
    val descriptor = addContentModule(
      "lite.module.frontend",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.LITE_FRONTEND, descriptor)
  }

  fun testPredefinedModuleKindWinsOverLiteKind() {
    val descriptor = addContentModule(
      "intellij.platform.frontend.split",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.FRONTEND, descriptor)
  }

  fun testFrontendAndBackendModuleWithoutRpcIsMixed() {
    val descriptor = addContentModule(
      "mixed.module",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.MIXED, descriptor)
  }

  fun testModuleWithOnlyRpcIsShared() {
    val descriptor = addContentModule(
      "shared.module",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.rpc"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.SHARED, descriptor)
  }

  fun testContentModuleInheritsLiteFrontendKindFromContainingPlugin() {
    addPluginModule(
      "lite.plugin",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="lite.plugin.content"/>
          </content>
        </idea-plugin>
      """.trimIndent(),
    )
    val descriptor = addContentModule("lite.plugin.content", "<idea-plugin/>")

    assertModuleKind(ModuleKind.LITE_FRONTEND, descriptor)
  }

  fun testContentModuleWithOwnRpcInheritsFrontendKindFromLiteContainingPlugin() {
    addPluginModule(
      "lite.plugin",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="lite.plugin.content"/>
          </content>
        </idea-plugin>
      """.trimIndent(),
    )
    val descriptor = addContentModule(
      "lite.plugin.content",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.rpc"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )

    assertModuleKind(ModuleKind.FRONTEND, descriptor)
  }

  fun testContentModuleOfRegularAndLiteFrontendPluginsIsFrontend() {
    addPluginModule(
      "lite.plugin",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="shared.content"/>
          </content>
        </idea-plugin>
      """.trimIndent(),
    )
    addPluginModule(
      "regular.plugin",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.rpc"/>
          </dependencies>
          <content>
            <module name="shared.content"/>
          </content>
        </idea-plugin>
      """.trimIndent(),
    )
    val descriptor = addContentModule("shared.content", "<idea-plugin/>")

    assertModuleKind(ModuleKind.FRONTEND, descriptor)
  }

  private fun assertModuleKind(expectedModuleKind: ModuleKind, descriptor: PsiFile) {
    val resolvedModuleKind = SplitModeModuleKindResolver.getOrComputeDescriptorAnalysis(descriptor as XmlFile).resolvedModuleKind
    Assert.assertEquals("Reasoning: ${resolvedModuleKind.reasoning}", expectedModuleKind, resolvedModuleKind.kind)
  }

  private fun addContentModule(moduleName: String, descriptorContent: String): PsiFile {
    return addModuleWithXmlDescriptor(moduleName, "$moduleName.xml", descriptorContent)
  }

  private fun addPluginModule(moduleName: String, pluginXmlContent: String): PsiFile {
    return addModuleWithXmlDescriptor(moduleName, "META-INF/plugin.xml", pluginXmlContent)
  }

  private fun addModuleWithXmlDescriptor(
    moduleName: String,
    descriptorRelativePathToResourcesDirectory: String,
    descriptorContent: String,
  ): PsiFile {
    val addedModule =
      PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, myFixture.tempDirFixture.findOrCreateDir(moduleName))
    PsiTestUtil.addSourceRoot(addedModule, myFixture.tempDirFixture.findOrCreateDir("$moduleName/resources"), JavaResourceRootType.RESOURCE)
    val createdDescriptorFile =
      myFixture.addFileToProject("$moduleName/resources/$descriptorRelativePathToResourcesDirectory", descriptorContent)
    if (descriptorRelativePathToResourcesDirectory == "META-INF/plugin.xml") {
      val buildConfiguration = PluginBuildConfiguration.getInstance(addedModule)
      Assert.assertNotNull("Plugin build configuration for module $moduleName was not created", buildConfiguration)
      buildConfiguration!!.setPluginXmlFromVirtualFile(createdDescriptorFile.virtualFile)
    }
    return createdDescriptorFile
  }
}
