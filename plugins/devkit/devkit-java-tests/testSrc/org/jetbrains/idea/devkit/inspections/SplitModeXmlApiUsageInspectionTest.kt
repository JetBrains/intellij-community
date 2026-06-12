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
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeXmlApiUsageInspection
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.Assert
import kotlin.time.Duration.Companion.seconds

internal class SplitModeXmlApiUsageInspectionTest : JavaCodeInsightFixtureTestCase() {
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

    myFixture.enableInspections(SplitModeXmlApiUsageInspection())
  }

  fun testFrontendExtensionInBackendModule() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.1",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.1'">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInFrontendModule() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.2",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.2'">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testOptionalDependsDoesNotAffectPluginXmlKind() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.63",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <depends optional="true" config-file="optional-backend.xml">intellij.platform.backend</depends>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testApiRestrictionsJsonHasNoDuplicateApiTargets() {
    SplitModeApiRestrictionsService.getInstance(project).assertApiRestrictionsCanBeReadForTest()
  }

  fun testModuleKindCanBePredefinedInRestrictionsService() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "intellij.platform.frontend",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
            <localInspection/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testModuleKindCanBePredefinedForDescriptorPath() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "intellij.platform.resources",
      descriptorRelativePathToResourcesDirectory = "META-INF/PlatformLangPlugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
            <localInspection/>
          </extensions>
        </idea-plugin>
      """.trimIndent(),
      resourceRootDirectoryName = "src",
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testPredefinedModuleSkipsAllSplitModeInspections() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "intellij.platform.resources",
      descriptorRelativePathToResourcesDirectory = "META-INF/PlatformLangPlugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
            <localInspection/>
          </extensions>
        </idea-plugin>
      """.trimIndent(),
      resourceRootDirectoryName = "src",
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testSkippingPredefinedModuleInspectionsCanBeDisabled() {
    RegistryManager.getInstance().get("devkit.split.mode.inspections.skip.predefined")
      .setValue(false, testRootDisposable)

    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "intellij.platform.frontend",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Predefined module kind for module 'intellij.platform.frontend'">typedHandler</warning>/>
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Predefined module kind for module 'intellij.platform.frontend'">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testFrontendAndBackendExtensionsInSharedModule() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.3",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
            <module name="intellij.platform.ide"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
            <<warning descr="'com.intellij.fileEditorProvider' should be used in 'frontend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

No frontend or backend dependencies were found for descriptor 'plugin.xml' in module 'unique.module.name.3'">fileEditorProvider</warning>/>
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

No frontend or backend dependencies were found for descriptor 'plugin.xml' in module 'unique.module.name.3'">localInspection</warning>/>
            <lang.parserDefinition/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInFrontendContentModule() {
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.4",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.4.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'unique.module.name.4.xml' in module 'unique.module.name.4'">localInspection<caret></warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
    Assert.assertTrue(
      myFixture.filterAvailableIntentions("Make module 'unique.module.name.4' work in 'frontend' only").isEmpty()
    )
    myFixture.findSingleIntention("Make module 'unique.module.name.4' work in 'monolith' only")
  }

  fun testFrontendExtensionInContentModuleOfBackendOnlyPlugin() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.5",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <id>com.example.backend.plugin</id>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.6"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.6",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.6.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'unique.module.name.5'  -> backend">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testFrontendAndBackendExtensionsInSharedContentModuleWithMultipleContainingPlugins() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.7",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <id>com.example.frontend.plugin</id>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.9"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.8",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <id>com.example.backend.plugin</id>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.9"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.9",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.9.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'unique.module.name.7'  -> frontend
Module 'unique.module.name.8'  -> backend">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testFrontendExtensionInTransitivelyFrontendModule() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.11",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.11.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.12",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.11"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.12'
via dependency 'unique.module.name.11' -> descriptor 'unique.module.name.11.xml' in module 'unique.module.name.11'.">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInTransitivelyFrontendModule() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.23",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.23.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.24",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.23"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.24'
via dependency 'unique.module.name.23' -> descriptor 'unique.module.name.23.xml' in module 'unique.module.name.23'.">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInModuleWithTransitivelyPredefinedFrontendContentModule() {
    addModuleWithXmlDescriptor(
      moduleName = "intellij.platform.frontend",
      descriptorRelativePathToResourcesDirectory = "intellij.platform.frontend.xml",
      pluginXmlContent = """
        <idea-plugin/>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.40",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.40.xml",
      pluginXmlContent = """
        <idea-plugin>
          <content>
            <module name="intellij.platform.frontend" loading="required"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.41",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.40"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.41'
via dependency 'unique.module.name.40' -> required content module Predefined module kind for module 'intellij.platform.frontend'.">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testNoWarningsForFrontendExtensionInSingleModuleExternalPluginWithBackendVcsDependency() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.13",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.vcs.impl"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInContentModuleWithMultipleContainingFrontendPlugins() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.14",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <id>com.example.frontend.plugin.one</id>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.16"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.15",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <id>com.example.frontend.plugin.two</id>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.16"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.16",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.16.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'unique.module.name.14'  -> frontend
Module 'unique.module.name.15'  -> frontend">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInContentModuleOfPluginModuleWithOwnContentDescriptor() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.17",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <id>com.example.plugin.with.content.descriptor</id>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.17"/>
            <module name="unique.module.name.18"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.17",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.17.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.18",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.18.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'unique.module.name.17'  -> backend">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInContentModuleOfPredefinedSharedContainingPlugin() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.42",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <module value="com.intellij.modules.lang"/>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.43"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.43",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.43.xml",
      pluginXmlContent = """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'unique.module.name.42'  -> shared">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInMutuallyContainingContentModules() {
    val originalContentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.19",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.19.xml",
      """
        <idea-plugin>
          <content>
            <module name="unique.module.name.20"/>
          </content>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'unique.module.name.20'  -> backend">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.20",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.20.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.19"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(originalContentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInModuleWithCyclicTransitiveFrontendDependency() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.25",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.26"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.25'
via dependency 'unique.module.name.26' -> descriptor 'unique.module.name.26.xml' in module 'unique.module.name.26'.">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.26",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.26.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.25"/>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInOwnContentDescriptorOfBackendPluginModule() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.27",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <id>com.example.self.including.backend.plugin</id>
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
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'unique.module.name.27'  -> backend">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testFrontendExtensionInOwnEmbeddedContentDescriptorOfPluginModuleWithRequiredBackendSibling() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.37",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <content>
            <module name="unique.module.name.37" loading="embedded"/>
            <module name="unique.module.name.38" loading="required"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.37",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.37.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'unique.module.name.37'  -> backend">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.38",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.38.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testPredefinedSharedContainingPluginOverridesFrontendNamingConvention() {
    addModuleWithXmlDescriptor(
      moduleName = "intellij.platform.resources",
      descriptorRelativePathToResourcesDirectory = "META-INF/PlatformLangPlugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <content>
            <module name="unique.module.name.39" loading="embedded"/>
          </content>
        </idea-plugin>
      """.trimIndent(),
      resourceRootDirectoryName = "src",
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.39",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.39.xml",
      pluginXmlContent = """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

Module declares no own FE/BE dependencies, but the containing plugin.xml files do:
Module 'intellij.platform.resources'  -> shared">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testFrontendExtensionInPluginXmlWithRequiredBackendContentModule() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.28",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.28.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.29",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <content>
            <module name="unique.module.name.28" loading="required"/>
          </content>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from required content module descriptor 'unique.module.name.28.xml' in module 'unique.module.name.28'">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testSiblingPluginDescriptorsAreAnalyzedIndependently() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.61",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val sharedDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.61",
      descriptorRelativePathToResourcesDirectory = "META-INF/shared-fragment.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    val backendDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.61",
      descriptorRelativePathToResourcesDirectory = "META-INF/backend-fragment.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'backend-fragment.xml' in module 'unique.module.name.61'">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(sharedDescriptor.virtualFile)
    myFixture.checkHighlighting()

    myFixture.configureFromExistingVirtualFile(backendDescriptor.virtualFile)
    myFixture.checkHighlighting()
  }

  fun testCustomMetaInfDescriptorDoesNotInheritPluginXmlKind() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.62",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val customDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.62",
      descriptorRelativePathToResourcesDirectory = "META-INF/custom-descriptor.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(customDescriptor.virtualFile)
    myFixture.checkHighlighting()
  }

  fun testBackendExtensionInModuleWithDuplicateTransitiveBackendDependencies() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.39",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.40"/>
            <module name="unique.module.name.41"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.39'
via dependency 'unique.module.name.40' -> descriptor 'unique.module.name.40.xml' in module 'unique.module.name.40'.">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.40",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.40.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.41",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.41.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testFrontendExtensionInModuleWithTransitiveAnalysisDisabled() {
    RegistryManager.getInstance().get("devkit.split.mode.analysis.transitive.dependencies")
      .setValue(false, testRootDisposable)

    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.44",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.44.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.45",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="unique.module.name.44"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
    RegistryManager.getInstance().get("devkit.split.mode.analysis.transitive.dependencies")
      .setValue(true, testRootDisposable)
  }

  fun testBackendExtensionInContentModuleWithContainingPluginAnalysisDisabled() {
    RegistryManager.getInstance().get("devkit.split.mode.analysis.containing.plugins")
      .setValue(false, testRootDisposable)

    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.46",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <id>com.example.backend.only.plugin.with.flag.disabled</id>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.47"/>
          </content>
        </idea-plugin>
      """.trimIndent()
    )
    val contentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.47",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.47.xml",
      """
        <idea-plugin>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'shared'.

Computed module kind reasoning:

No frontend or backend dependencies were found for descriptor 'unique.module.name.47.xml' in module 'unique.module.name.47'">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(contentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
    RegistryManager.getInstance().get("devkit.split.mode.analysis.containing.plugins")
      .setValue(true, testRootDisposable)
  }

  fun testSharedExtensionInBackendModule() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.48",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.lang.parserDefinition' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.48'">lang.parserDefinition</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testSharedExtensionInFrontendModule() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.49",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.lang.parserDefinition' should be used in 'shared' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.49'">lang.parserDefinition</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  private fun addModuleWithXmlDescriptor(
    moduleName: String,
    descriptorRelativePathToResourcesDirectory: String,
    pluginXmlContent: String,
    resourceRootDirectoryName: String = "resources",
  ): PsiFile {
    val existingModule = ModuleManager.getInstance(project).findModuleByName(moduleName)
    val targetModule = if (existingModule != null) {
      existingModule
    }
    else {
      val addedModule =
        PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, myFixture.tempDirFixture.findOrCreateDir(moduleName))
      PsiTestUtil.addSourceRoot(addedModule,
                                myFixture.tempDirFixture.findOrCreateDir("$moduleName/$resourceRootDirectoryName"),
                                JavaResourceRootType.RESOURCE)
      addedModule
    }
    val createdDescriptorFile =
      myFixture.addFileToProject("$moduleName/$resourceRootDirectoryName/$descriptorRelativePathToResourcesDirectory", pluginXmlContent)
    Assert.assertNotNull("XML descriptor for module $moduleName was not created", createdDescriptorFile)
    if (descriptorRelativePathToResourcesDirectory == "META-INF/plugin.xml") {
      val buildConfiguration = PluginBuildConfiguration.getInstance(targetModule)
      Assert.assertNotNull("Plugin build configuration for module $moduleName was not created", buildConfiguration)
      buildConfiguration!!.setPluginXmlFromVirtualFile(createdDescriptorFile!!.virtualFile)
    }
    return createdDescriptorFile!!
  }
}
