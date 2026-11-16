// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration
import org.jetbrains.idea.devkit.module.PluginModuleType

/**
 * The fixture of a test in a plugin module. Create it with [pluginModuleFixture] in the project of [pluginModuleProjectFixture].
 *
 * The fixtures of the DevKit inspection tests in a plugin module extend it with their own helpers.
 * After each test, the fixture removes the `plugin.xml` that [setPluginXml] set.
 */
open class PluginModuleFixture(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  /**
   * The extension of the test data files, without the dot. [doTest] uses it.
   */
  val fileExtension: String,
) {
  /**
   * Copies the test data file [pluginXml] to `META-INF/plugin.xml` and makes it the `plugin.xml` of the module.
   * Call it from any thread. It changes the plugin build configuration on the EDT.
   */
  fun setPluginXml(@TestDataFile pluginXml: String) {
    val file = fixture.copyFileToProject(pluginXml, "META-INF/plugin.xml")
    runInEdtAndWait {
      getPluginBuildConfiguration(fixture.module).setPluginXmlFromVirtualFile(file)
    }
  }

  /**
   * Checks the highlighting of the test data file `<test name>.<file extension>`.
   */
  fun doTest() {
    fixture.testHighlighting(fixture.getTestName(false) + '.' + fileExtension)
  }

  companion object {
    /**
     * The descriptor of the plugin module: the module type of [PluginModuleType] and the JetBrains annotations.
     */
    @JvmField
    val DESCRIPTOR: LightProjectDescriptor = object : DefaultLightProjectDescriptor() {
      override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        addJetBrainsAnnotations(model)
      }

      override fun getModuleTypeId(): String = PluginModuleType.ID
    }
  }
}

/**
 * Creates the project of a plugin module test class with [PluginModuleFixture.DESCRIPTOR].
 * Declare it in a static field or in a companion object.
 */
fun pluginModuleProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(PluginModuleFixture.DESCRIPTOR)

/**
 * Creates a [PluginModuleFixture] for one test. Declare it as an instance field.
 *
 * [projectFixture] must have a plugin module, for example from [pluginModuleProjectFixture].
 * [basePath] is the test data path under the community directory. [fileExtension] is the extension of the test data files.
 */
@JvmOverloads
fun pluginModuleFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
  fileExtension: String = "",
): TestFixture<PluginModuleFixture> = testFixture("pluginModuleFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  initialized(PluginModuleFixture(fixture, fileExtension)) {
    withContext(Dispatchers.EDT) {
      getPluginBuildConfiguration(fixture.module).cleanupForNextTest()
    }
  }
}

private fun getPluginBuildConfiguration(module: Module): PluginBuildConfiguration =
  requireNotNull(PluginBuildConfiguration.getInstance(module)) { "The module ${module.name} is not a plugin module" }
