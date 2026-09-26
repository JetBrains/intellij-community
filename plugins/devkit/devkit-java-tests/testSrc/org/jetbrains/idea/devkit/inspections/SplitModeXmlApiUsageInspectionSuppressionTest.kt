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
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeMixedDependenciesInspection
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeXmlApiUsageInspection
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.Assert
import kotlin.time.Duration.Companion.seconds

internal class SplitModeXmlApiUsageInspectionSuppressionTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    RegistryManager.getInstance().get("devkit.split.mode.analysis.containing.plugins")
      .setValue(true, testRootDisposable)
    RegistryManager.getInstance().get("devkit.split.mode.inspections.enable.in.implicit.module.kind")
      .setValue(false, testRootDisposable)

    val service = SplitModeApiRestrictionsService.getInstance(project)
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }

    myFixture.enableInspections(
      SplitModeXmlApiUsageInspection(),
      SplitModeMixedDependenciesInspection(),
      SplitModeImplicitModuleKindInspection(),
    )
  }

  fun testPluginXmlWithIndirectFrontendOnlyDependenciesShowsSingleRootError() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.60.frontend.support",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.60.frontend.support.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.60",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<weak_warning descr="This plugin effectively depends on frontend-only modules and will work only in frontend in Split Mode. Consider adding a frontend dependency to explicitly indicate target IDE.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.60'
via dependency 'unique.module.name.60.frontend.support' -> descriptor 'unique.module.name.60.frontend.support.xml' in module 'unique.module.name.60.frontend.support'.">idea-plugin</weak_warning>>
          <extensions defaultExtensionNs="com.intellij">
            <localInspection/>
          </extensions>
          <dependencies>
            <module name="unique.module.name.60.frontend.support"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testPluginXmlWithIndirectBackendOnlyDependenciesShowsSingleRootError() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.61.backend.support",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.61.backend.support.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.61",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<weak_warning descr="This plugin effectively depends on backend-only modules and will work only in backend in Split Mode. Consider adding a backend dependency to explicitly indicate target IDE.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.61'
via dependency 'unique.module.name.61.backend.support' -> descriptor 'unique.module.name.61.backend.support.xml' in module 'unique.module.name.61.backend.support'.">idea-plugin</weak_warning>>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
          </extensions>
          <dependencies>
            <module name="unique.module.name.61.backend.support"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testFrontendAndBackendExtensionsInMixedModule() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.10",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.10'

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.10'">idea-plugin</error>>
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
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testMixedDependenciesInMutuallyContainingContentModules() {
    val originalContentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.21",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.21.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'unique.module.name.21.xml' in module 'unique.module.name.21'

Backend dependency 'intellij.platform.backend' from containing plugin descriptor 'unique.module.name.22.xml' in module 'unique.module.name.22'">idea-plugin</error>>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.22"/>
          </content>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
            <localInspection/>
          </extensions>
        </idea-plugin>
      """.trimIndent(),
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.22",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.22.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <content>
            <module name="unique.module.name.21"/>
          </content>
        </idea-plugin>
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(originalContentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testPluginXmlWithExplicitFrontendDependencyKeepsXmlWarnings() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.62",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.localInspection' should be used in 'backend' module type. Actual module type is 'frontend'.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'plugin.xml' in module 'unique.module.name.62'">localInspection</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testPluginXmlWithExplicitBackendDependencyKeepsXmlWarnings() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.63",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <<warning descr="'com.intellij.typedHandler' should be used in 'shared' module type. Actual module type is 'backend'.

Computed module kind reasoning:

Backend dependency 'intellij.platform.backend' from descriptor 'plugin.xml' in module 'unique.module.name.63'">typedHandler</warning>/>
          </extensions>
        </idea-plugin>
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testMixedPluginXmlWithRequiredAndEmbeddedContentModules() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.31",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.31.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.32",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.32.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.33",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from required content module descriptor 'unique.module.name.31.xml' in module 'unique.module.name.31'

Backend dependency 'intellij.platform.backend' from embedded content module descriptor 'unique.module.name.32.xml' in module 'unique.module.name.32'">idea-plugin</error>>
          <content>
            <module name="unique.module.name.31" loading="required"/>
            <module name="unique.module.name.32" loading="embedded"/>
          </content>
          <extensions defaultExtensionNs="com.intellij">
            <typedHandler/>
            <localInspection/>
          </extensions>
        </idea-plugin>
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.checkHighlighting()
  }

  fun testFrontendContentModuleIsMixedWhenContainingPluginRequiresBackendSiblingModule() {
    addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.34",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <content>
            <module name="unique.module.name.35"/>
            <module name="unique.module.name.36" loading="required"/>
          </content>
        </idea-plugin>
      """.trimIndent(),
    )
    val frontendContentModuleDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.35",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.35.xml",
      pluginXmlContent = """
        <<error descr="This module effectively depends on frontend-only and backend-only modules simultaneously. It will not get loaded in Split Mode.

Computed module kind reasoning:

Frontend dependency 'intellij.platform.frontend' from descriptor 'unique.module.name.35.xml' in module 'unique.module.name.35'

Backend dependency 'intellij.platform.backend' from containing plugin required content module descriptor 'unique.module.name.36.xml' in module 'unique.module.name.36'">idea-plugin</error>>
          <dependencies>
            <module name="intellij.platform.frontend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent(),
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
      """.trimIndent(),
    )
    myFixture.configureFromExistingVirtualFile(frontendContentModuleDescriptor.virtualFile)

    myFixture.checkHighlighting()
    myFixture.findSingleIntention("Make module 'unique.module.name.35' work in 'frontend' only")
    myFixture.findSingleIntention("Make module 'unique.module.name.35' work in 'backend' only")
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
      PsiTestUtil.addSourceRoot(
        addedModule,
        myFixture.tempDirFixture.findOrCreateDir("$moduleName/$resourceRootDirectoryName"),
        JavaResourceRootType.RESOURCE,
      )
      addedModule
    }
    val createdDescriptorFile = myFixture.addFileToProject(
      "$moduleName/$resourceRootDirectoryName/$descriptorRelativePathToResourcesDirectory",
      pluginXmlContent,
    )
    Assert.assertNotNull("XML descriptor for module $moduleName was not created", createdDescriptorFile)
    if (descriptorRelativePathToResourcesDirectory == "META-INF/plugin.xml") {
      val buildConfiguration = PluginBuildConfiguration.getInstance(targetModule)
      Assert.assertNotNull("Plugin build configuration for module $moduleName was not created", buildConfiguration)
      buildConfiguration!!.setPluginXmlFromVirtualFile(createdDescriptorFile!!.virtualFile)
    }
    return createdDescriptorFile!!
  }
}
