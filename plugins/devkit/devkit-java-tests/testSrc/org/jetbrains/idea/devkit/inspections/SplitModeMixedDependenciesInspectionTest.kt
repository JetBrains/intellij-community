// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeImplicitModuleKindInspection
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeMixedDependenciesInspection
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeModuleKindResolver
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.recognizeSplitModeModuleKind
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.Assert
import kotlin.time.Duration.Companion.seconds

internal class SplitModeMixedDependenciesInspectionTest : JavaCodeInsightFixtureTestCase() {
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

    myFixture.enableInspections(SplitModeMixedDependenciesInspection(), SplitModeImplicitModuleKindInspection())
  }

  fun testMixedModuleDependenciesInPluginXml() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.17",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.17'

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.17'">idea-plugin</error>>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.rpc.split"/>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testMixedDependsInPluginXml() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.28",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.28'

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.28'">idea-plugin</error>>
          <depends>intellij.platform.frontend</depends>
          <depends>intellij.platform.backend</depends>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testOptionalDependsDoesNotMakePluginXmlMixed() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.64",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <depends optional="true" config-file="optional-backend.xml">intellij.platform.backend</depends>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testMixedDependenciesInContentModuleXml() {
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.19",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.19.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend.split' from descriptor 'unique.module.name.19.xml' in module 'unique.module.name.19'

Backend dependency 'intellij.platform.kernel.backend' from descriptor 'unique.module.name.19.xml' in module 'unique.module.name.19'">idea-plugin</error>>
          <dependencies>
            <module name="intellij.platform.frontend.split"/>
            <module name="intellij.platform.kernel.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testOnlyFrontendDependenciesAreAllowed() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.20",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.plugins.frontend.split"/>
            <plugin id="com.intellij.jetbrains.client"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testMixedDependenciesInContentModuleOfBackendOnlyPlugin() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.21",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <id>com.example.backend.plugin</id>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.22"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.22",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.22.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.plugins.frontend.split' from descriptor 'unique.module.name.22.xml' in module 'unique.module.name.22'

Backend dependency 'intellij.platform.backend' from containing plugin descriptor 'plugin.xml' in module 'unique.module.name.21'">idea-plugin</error>>
          <dependencies>
            <module name="intellij.platform.plugins.frontend.split"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testMixedDependenciesInContentModuleOfFrontendOnlyPlugin() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.23",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <id>com.example.frontend.plugin</id>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.24"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.24",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.24.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from containing plugin descriptor 'plugin.xml' in module 'unique.module.name.23'

Backend dependency 'intellij.platform.kernel.backend' from descriptor 'unique.module.name.24.xml' in module 'unique.module.name.24'">idea-plugin</error>>
          <dependencies>
            <module name="intellij.platform.kernel.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testNoMixedErrorForSharedContentModuleWithoutOwnDependenciesBlock() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.25",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <id>com.example.frontend.plugin</id>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.27"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.26",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <id>com.example.backend.plugin</id>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.27"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.27",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.27.xml",
      pluginXmlContent = """
        <idea-plugin/>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testMixedDependenciesWithExplicitMonolithDependency() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.29",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.backend"/>
            <module name="intellij.platform.monolith"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testPluginXmlWithIndirectFrontendOnlyDependenciesGetsSingleRootErrorWhenXmlInspectionsAreDisabled() {
    RegistryManager.getInstance().get("devkit.split.mode.inspections.enable.in.implicit.module.kind")
      .setValue(false, testRootDisposable)

    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.50.frontend.support",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.50.frontend.support.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.50",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<weak_warning descr="This plugin effectively depends on frontend-only modules and will work only in frontend in Split Mode. Consider adding a frontend dependency to explicitly indicate target IDE.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.50'
via dependency 'unique.module.name.50.frontend.support' -> descriptor 'unique.module.name.50.frontend.support.xml' in module 'unique.module.name.50.frontend.support'.">idea-plugin</weak_warning>>
          <dependencies>
            <module name="unique.module.name.50.frontend.support"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
    moveCaretToIdeaPluginTag()
    myFixture.findSingleIntention("Make module 'unique.module.name.50' work in 'frontend' only")
    myFixture.findSingleIntention("Make module 'unique.module.name.50' work in 'monolith' only")
    Assert.assertTrue(myFixture.filterAvailableIntentions("Make module 'unique.module.name.50' work in 'backend' only").isEmpty())
  }

  fun testPluginXmlWithIndirectBackendOnlyDependenciesGetsSingleRootErrorWhenXmlInspectionsAreDisabled() {
    RegistryManager.getInstance().get("devkit.split.mode.inspections.enable.in.implicit.module.kind")
      .setValue(false, testRootDisposable)

    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.52.backend.support",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.52.backend.support.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.52",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<weak_warning descr="This plugin effectively depends on backend-only modules and will work only in backend in Split Mode. Consider adding a backend dependency to explicitly indicate target IDE.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.52'
via dependency 'unique.module.name.52.backend.support' -> descriptor 'unique.module.name.52.backend.support.xml' in module 'unique.module.name.52.backend.support'.">idea-plugin</weak_warning>>
          <dependencies>
            <module name="unique.module.name.52.backend.support"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
    moveCaretToIdeaPluginTag()
    myFixture.findSingleIntention("Make module 'unique.module.name.52' work in 'backend' only")
    myFixture.findSingleIntention("Make module 'unique.module.name.52' work in 'monolith' only")
    Assert.assertTrue(myFixture.filterAvailableIntentions("Make module 'unique.module.name.52' work in 'frontend' only").isEmpty())
  }

  fun testPluginXmlWithAmbiguousAliasDependencyBecomesMixed() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.53.frontend.alias.provider",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <id>unique.module.name.53.frontend.alias.provider</id>
          <module value="unique.module.name.53.shared.alias"/>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.53.backend.alias.provider",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <id>unique.module.name.53.backend.alias.provider</id>
          <module value="unique.module.name.53.shared.alias"/>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.53.consumer",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <depends>unique.module.name.53.shared.alias</depends>
        </idea-plugin>
      """.trimIndent()
    )

    val recognizedKind = recognizeSplitModeModuleKind(pluginXml as com.intellij.psi.xml.XmlFile)
    Assert.assertNotNull("Module kind should be recognized", recognizedKind)
    Assert.assertEquals("unique.module.name.53.consumer", recognizedKind!!.moduleName)
    Assert.assertEquals("mixed", recognizedKind.kind.id)
    Assert.assertTrue(
      "Reasoning should mention the frontend alias provider.\nReasoning: ${recognizedKind.reasoning}",
      recognizedKind.reasoning.contains("unique.module.name.53.frontend.alias.provider"),
    )
    Assert.assertTrue(
      "Reasoning should mention the backend alias provider.\nReasoning: ${recognizedKind.reasoning}",
      recognizedKind.reasoning.contains("unique.module.name.53.backend.alias.provider"),
    )
  }

  fun testMixedPluginXmlGetsSingleRootErrorWhenXmlInspectionsAreDisabled() {
    RegistryManager.getInstance().get("devkit.split.mode.inspections.enable.in.implicit.module.kind")
      .setValue(false, testRootDisposable)

    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.51.frontend.support",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.51.frontend.support.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.51.backend.support",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.51.backend.support.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.51",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.51'
via dependency 'unique.module.name.51.frontend.support' -> descriptor 'unique.module.name.51.frontend.support.xml' in module 'unique.module.name.51.frontend.support'.

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.51'
via dependency 'unique.module.name.51.backend.support' -> descriptor 'unique.module.name.51.backend.support.xml' in module 'unique.module.name.51.backend.support'.">idea-plugin</error>>
          <dependencies>
            <module name="unique.module.name.51.frontend.support"/>
            <module name="unique.module.name.51.backend.support"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testContentModuleWithDifferentContainingPluginKindsByNamingConventionIsShared() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.30.frontend",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <content>
            <module name="unique.module.name.32"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.31.backend",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <content>
            <module name="unique.module.name.32"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.32",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.32.xml",
      pluginXmlContent = """
        <idea-plugin/>
      """.trimIndent()
    )

    assertSharedModuleKindWithContainingPluginsOfDifferentKinds("unique.module.name.32")
  }

  fun testContentModuleWithDifferentContainingPluginKindsByTransitiveDependenciesIsShared() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.33",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.35"/>
          </dependencies>
          <content>
            <module name="unique.module.name.37"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.34",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.36"/>
          </dependencies>
          <content>
            <module name="unique.module.name.37"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.35",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.35.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.36",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.36.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.37",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.37.xml",
      pluginXmlContent = """
        <idea-plugin/>
      """.trimIndent()
    )

    assertSharedModuleKindWithContainingPluginsOfDifferentKinds("unique.module.name.37")
  }

  fun testRecognizeModuleKindApiReturnsKindAndReasoning() {
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.38",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.38.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )

    val recognizedKind = recognizeSplitModeModuleKind(contentModuleDescriptor as com.intellij.psi.xml.XmlFile)
    Assert.assertNotNull("Module kind should be recognized", recognizedKind)
    Assert.assertEquals("unique.module.name.38", recognizedKind!!.moduleName)
    Assert.assertEquals("backend", recognizedKind.kind.id)
    Assert.assertTrue(
      "Reasoning should mention the backend dependency.\nReasoning: ${recognizedKind.reasoning}",
      recognizedKind.reasoning.contains("intellij.platform.backend"),
    )
  }

  fun testPredefinedSharedDependencyOverridesFrontendSuffix() {
    addModuleWithXmlDescriptor(
      moduleName = "intellij.platform.resources",
      descriptorRelativePathToResourcesDirectory = "META-INF/PlatformLangPlugin.xml",
      pluginXmlContent = """
        <idea-plugin/>
      """.trimIndent(),
      resourceRootDirectoryName = "src",
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.39",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.39.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
            <module name="intellij.platform.resources"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()

    val recognizedKind = recognizeSplitModeModuleKind(contentModuleDescriptor as com.intellij.psi.xml.XmlFile)
    Assert.assertNotNull("Module kind should be recognized", recognizedKind)
    Assert.assertEquals("unique.module.name.39", recognizedKind!!.moduleName)
    Assert.assertEquals("backend", recognizedKind.kind.id)
  }

  fun testTransitivelyPredefinedSharedDependencyOverridesFrontendSuffix() {
    addModuleWithXmlDescriptor(
      moduleName = "intellij.platform.resources",
      descriptorRelativePathToResourcesDirectory = "META-INF/PlatformLangPlugin.xml",
      pluginXmlContent = """
        <idea-plugin/>
      """.trimIndent(),
      resourceRootDirectoryName = "src",
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.40",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.40.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
            <module name="intellij.platform.resources"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.41",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.41.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.40"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()

    val recognizedKind = recognizeSplitModeModuleKind(contentModuleDescriptor as com.intellij.psi.xml.XmlFile)
    Assert.assertNotNull("Module kind should be recognized", recognizedKind)
    Assert.assertEquals("unique.module.name.41", recognizedKind!!.moduleName)
    Assert.assertEquals("backend", recognizedKind.kind.id)
  }

  private fun assertSharedModuleKindWithContainingPluginsOfDifferentKinds(moduleName: String) {
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName)
    Assert.assertNotNull("Module $moduleName was not created", module)

    val moduleAnalysis = SplitModeModuleKindResolver.getOrComputeModuleAnalysis(module!!)
    Assert.assertEquals(SplitModeApiRestrictionsService.ModuleKind.SHARED, moduleAnalysis.resolvedModuleKind.kind)
    Assert.assertTrue(
      "Computed module kind reasoning should not be blank for shared module '$moduleName'",
      moduleAnalysis.resolvedModuleKind.reasoning.isNotBlank(),
    )
    Assert.assertTrue(
      "Shared module reasoning should mention containing plugin descriptors.\nReasoning: ${moduleAnalysis.resolvedModuleKind.reasoning}",
      moduleAnalysis.resolvedModuleKind.reasoning.contains("containing plugin.xml files do"),
    )
  }

  private fun moveCaretToIdeaPluginTag() {
    val ideaPluginTagOffset = myFixture.file.text.indexOf("idea-plugin")
    Assert.assertTrue("idea-plugin tag should be present in the test descriptor", ideaPluginTagOffset >= 0)
    myFixture.editor.caretModel.moveToOffset(ideaPluginTagOffset)
  }

  private fun addModuleWithXmlDescriptor(
    moduleName: String,
    descriptorRelativePathToResourcesDirectory: String,
    pluginXmlContent: String,
    resourceRootDirectoryName: String = "resources",
  ): PsiFile {
    val addedModule =
      PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, myFixture.tempDirFixture.findOrCreateDir(moduleName))
    PsiTestUtil.addSourceRoot(
      addedModule,
      myFixture.tempDirFixture.findOrCreateDir("$moduleName/$resourceRootDirectoryName"),
      JavaResourceRootType.RESOURCE,
    )
    val createdDescriptorFile = myFixture.addFileToProject(
      "$moduleName/$resourceRootDirectoryName/$descriptorRelativePathToResourcesDirectory",
      pluginXmlContent,
    )
    Assert.assertNotNull("XML descriptor for module $moduleName was not created", createdDescriptorFile)
    if (descriptorRelativePathToResourcesDirectory == "META-INF/plugin.xml") {
      val buildConfiguration = PluginBuildConfiguration.getInstance(addedModule)
      Assert.assertNotNull("Plugin build configuration for module $moduleName was not created", buildConfiguration)
      buildConfiguration!!.setPluginXmlFromVirtualFile(createdDescriptorFile!!.virtualFile)
    }
    return createdDescriptorFile!!
  }
}
