// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration
import org.jetbrains.idea.devkit.inspections.remotedev.analysis.SplitModeApiRestrictionsService
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeMixedDependenciesInspection
import org.jetbrains.idea.devkit.inspections.remotedev.SplitModeXmlApiUsageInspection
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.junit.Assert
import kotlin.time.Duration.Companion.seconds

internal class SplitModeDependencyQuickFixesTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    RegistryManager.getInstance().get("devkit.split.mode.inspections.enable.in.implicit.module.kind")
      .setValue(true, testRootDisposable)

    val service = SplitModeApiRestrictionsService.getInstance(project)
    service.scheduleLoadRestrictions()
    timeoutRunBlocking {
      waitUntil("API restrictions failed to load", 2.seconds) { service.isLoaded() }
    }

    myFixture.enableInspections(SplitModeXmlApiUsageInspection(), SplitModeMixedDependenciesInspection())
  }

  fun testAddFrontendDependencyFixInXmlInspection() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.1",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <toolWindow<caret>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    launchActionAndWait("Make module 'unique.module.name.quick.fix.1' work in 'frontend' only") {
      getModuleDependencyNames(pluginXml).contains("intellij.platform.frontend")
    }

    val result = myFixture.file.text
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.frontend\"/>"))
    Assert.assertTrue(getModuleDependencyNames(pluginXml).contains("intellij.platform.frontend"))
  }

  fun testMakeModuleFrontendDependenciesFixPreviewInXmlInspection() {
    addModuleWithXmlDescriptor(
      moduleName = "intellij.codeWithMe.abstract.main",
      pluginXmlContent = """
        <idea-plugin />
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.2",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
            <module name="intellij.codeWithMe.abstract.main"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <toolWindow<caret>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)
    addModuleDependencies(pluginXml, "intellij.codeWithMe.abstract.main")

    val intention = myFixture.findSingleIntention("Make module 'unique.module.name.quick.fix.2' work in 'frontend' only")
    myFixture.checkPreviewAndLaunchAction(intention)
    timeoutRunBlocking {
      waitUntil("Quick fix was not applied", 5.seconds) {
        val moduleDependencies = getModuleDependencyNames(pluginXml)
        moduleDependencies.contains("intellij.platform.frontend") && !moduleDependencies.contains("intellij.codeWithMe.abstract.main")
      }
    }

    val result = myFixture.file.text
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.frontend\"/>"))
    Assert.assertFalse(result.contains("intellij.codeWithMe.abstract.main"))

    val moduleDependencies = getModuleDependencyNames(pluginXml)
    Assert.assertTrue(moduleDependencies.contains("intellij.platform.frontend"))
    Assert.assertFalse(moduleDependencies.contains("intellij.codeWithMe.abstract.main"))
  }

  fun testMakeModuleFrontendDependenciesFixInXmlInspection() {
    addModuleWithXmlDescriptor(
      moduleName = "intellij.codeWithMe.abstract.main",
      pluginXmlContent = """
        <idea-plugin />
      """.trimIndent()
    )
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.2",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
            <module name="intellij.platform.backend"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <toolWindow<caret>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)
    addModuleDependencies(pluginXml, "intellij.platform.backend")

    launchActionAndWait("Make module 'unique.module.name.quick.fix.2' work in 'frontend' only") {
      val moduleDependencies = getModuleDependencyNames(pluginXml)
      moduleDependencies.contains("intellij.platform.frontend") && !moduleDependencies.contains("intellij.platform.backend")
    }

    val result = myFixture.file.text
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.frontend\"/>"))
    Assert.assertFalse(result.contains("intellij.platform.backend"))

    val moduleDependencies = getModuleDependencyNames(pluginXml)
    Assert.assertTrue(moduleDependencies.contains("intellij.platform.frontend"))
    Assert.assertFalse(moduleDependencies.contains("intellij.platform.backend"))
  }

  fun testMakeModuleFrontendDependenciesFixInMixedInspection() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.3",
      pluginXmlContent = """
        <idea-<caret>plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)
    addModuleDependencies(pluginXml, "intellij.platform.frontend", "intellij.platform.backend")

    launchActionAndWait("Make module 'unique.module.name.quick.fix.3' work in 'frontend' only") {
      val moduleDependencies = getModuleDependencyNames(pluginXml)
      moduleDependencies.contains("intellij.platform.frontend") && !moduleDependencies.contains("intellij.platform.backend")
    }

    val result = myFixture.file.text
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.frontend\"/>"))
    Assert.assertFalse(result.contains("intellij.platform.backend"))

    val moduleDependencies = getModuleDependencyNames(pluginXml)
    Assert.assertTrue(moduleDependencies.contains("intellij.platform.frontend"))
    Assert.assertFalse(moduleDependencies.contains("intellij.platform.backend"))
  }

  fun testMakeModuleMonolithOnlyFixInXmlInspection() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.4",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <toolWindow<caret>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    val intention = myFixture.findSingleIntention("Make module 'unique.module.name.quick.fix.4' work in 'monolith' only")
    myFixture.launchAction(intention)

    val result = myFixture.file.text
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.monolith\"/>"))
    Assert.assertTrue(getModuleDependencyNames(pluginXml).contains("intellij.platform.monolith"))
  }

  fun testMakeModuleMonolithOnlyFixInMixedInspection() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.5",
      pluginXmlContent = """
        <idea-<caret>plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
            <module name="intellij.platform.frontend"/>
            <module name="intellij.platform.backend"/>
            <plugin id="com.intellij.jetbrains.client"/>
            <plugin id="com.jetbrains.remoteDevelopment"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)
    addModuleDependencies(
      pluginXml,
      "intellij.platform.frontend",
      "intellij.platform.backend",
      "com.intellij.jetbrains.client",
      "com.jetbrains.remoteDevelopment",
    )

    val intention = myFixture.filterAvailableIntentions("Make module 'unique.module.name.quick.fix.5' work in 'monolith' only").single()
    myFixture.launchAction(intention)

    val result = myFixture.file.text
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.core\"/>"))
    Assert.assertTrue(result.contains("<module name=\"intellij.platform.monolith\"/>"))
    Assert.assertFalse(result.contains("intellij.platform.frontend"))
    Assert.assertFalse(result.contains("intellij.platform.backend"))
    Assert.assertTrue(result.contains("<plugin id=\"com.intellij.jetbrains.client\"/>"))
    Assert.assertTrue(result.contains("<plugin id=\"com.jetbrains.remoteDevelopment\"/>"))

    val moduleDependencies = getModuleDependencyNames(pluginXml)
    Assert.assertTrue(moduleDependencies.contains("intellij.platform.monolith"))
    Assert.assertTrue(moduleDependencies.contains("com.intellij.jetbrains.client"))
    Assert.assertTrue(moduleDependencies.contains("com.jetbrains.remoteDevelopment"))
    Assert.assertFalse(moduleDependencies.contains("intellij.platform.frontend"))
    Assert.assertFalse(moduleDependencies.contains("intellij.platform.backend"))
    myFixture.checkHighlighting()
  }

  fun testPluginXmlQuickFixDoesNotTouchOwnContentModuleDescriptor() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.6",
      descriptorRelativePathToResourcesDirectory = "META-INF/plugin.xml",
      pluginXmlContent = """
        <idea-plugin>
          <content>
            <module name="unique.module.name.quick.fix.6" loading="embedded"/>
          </content>
          <extensions defaultExtensionNs="com.intellij">
            <toolWindow<caret>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    val contentDescriptor = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.6",
      descriptorRelativePathToResourcesDirectory = "unique.module.name.quick.fix.6.xml",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.backend"/>
          </dependencies>
        </idea-plugin>
      """.trimIndent()
    )
    val originalContentDescriptorText = contentDescriptor.text
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    launchActionAndWait("Make module 'unique.module.name.quick.fix.6' work in 'frontend' only") {
      myFixture.file.text.contains("<module name=\"intellij.platform.frontend\"/>")
    }

    Assert.assertTrue(myFixture.file.text.contains("<module name=\"intellij.platform.frontend\"/>"))
    Assert.assertEquals(originalContentDescriptorText, contentDescriptor.text)
  }

  fun testCompositeTargetOffersFrontendAndBackendFixes() {
    val pluginXml = addModuleWithXmlDescriptor(
      moduleName = "unique.module.name.quick.fix.7",
      pluginXmlContent = """
        <idea-plugin>
          <dependencies>
            <module name="intellij.platform.core"/>
          </dependencies>
          <extensions defaultExtensionNs="com.intellij">
            <applicationConfigurable<caret>/>
          </extensions>
        </idea-plugin>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(pluginXml.virtualFile)

    myFixture.findSingleIntention("Make module 'unique.module.name.quick.fix.7' work in 'frontend' only")
    myFixture.findSingleIntention("Make module 'unique.module.name.quick.fix.7' work in 'backend' only")
    myFixture.findSingleIntention("Make module 'unique.module.name.quick.fix.7' work in 'monolith' only")
  }

  private fun addModuleDependencies(file: PsiFile, vararg dependencyNames: String) {
    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return
    ModuleRootModificationUtil.updateModel(module) { model ->
      for (dependencyName in dependencyNames) {
        if (model.orderEntries.filterIsInstance<ModuleOrderEntry>().none { it.moduleName == dependencyName }) {
          model.addInvalidModuleEntry(dependencyName)
        }
      }
    }
  }

  private fun getModuleDependencyNames(file: PsiFile): Set<String> {
    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return emptySet()
    return ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<ModuleOrderEntry>().map { it.moduleName }.toSet()
  }

  private fun launchActionAndWait(intentionText: String, condition: () -> Boolean) {
    val intention = myFixture.findSingleIntention(intentionText)
    launchActionAndWait(intention, condition)
  }

  private fun launchActionAndWait(intention: IntentionAction, condition: () -> Boolean) {
    myFixture.launchAction(intention)
    timeoutRunBlocking(15.seconds) {
      waitUntil("Quick fix was not applied", 15.seconds) { condition() }
    }
  }

  private fun addModuleWithXmlDescriptor(
    moduleName: String,
    descriptorRelativePathToResourcesDirectory: String = "META-INF/plugin.xml",
    pluginXmlContent: String,
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
        myFixture.tempDirFixture.findOrCreateDir("$moduleName/resources"),
        JavaResourceRootType.RESOURCE,
      )
      addedModule
    }
    val createdDescriptorFile = myFixture.addFileToProject("$moduleName/resources/$descriptorRelativePathToResourcesDirectory", pluginXmlContent)
    Assert.assertNotNull("XML descriptor for module $moduleName was not created", createdDescriptorFile)
    if (descriptorRelativePathToResourcesDirectory == "META-INF/plugin.xml") {
      val buildConfiguration = PluginBuildConfiguration.getInstance(targetModule)
      Assert.assertNotNull("Plugin build configuration for module $moduleName was not created", buildConfiguration)
      buildConfiguration!!.setPluginXmlFromVirtualFile(createdDescriptorFile!!.virtualFile)
    }
    return createdDescriptorFile
  }

}
