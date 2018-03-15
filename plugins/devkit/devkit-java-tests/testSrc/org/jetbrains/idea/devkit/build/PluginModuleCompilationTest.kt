// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.build

import com.intellij.compiler.BaseCompilerTestCase
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.SmartList
import com.intellij.util.io.TestFileSystemBuilder.fs
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk
import org.jetbrains.idea.devkit.projectRoots.Sandbox
import java.io.File
import java.util.*

/**
 * @author nik
 */
class PluginModuleCompilationTest : BaseCompilerTestCase() {
  override fun setUpJdk() {
    super.setUpJdk()
    runWriteAction {
      val table = ProjectJdkTable.getInstance()
      val pluginSdk: Sdk = table.createSdk("IDEA plugin SDK", SdkType.findInstance(IdeaJdk::class.java))
      val modificator = pluginSdk.sdkModificator
      modificator.sdkAdditionalData = Sandbox(getSandboxPath(), testProjectJdk, pluginSdk)
      val rootPath = FileUtil.toSystemIndependentName(PathManager.getJarPathForClass(FileUtilRt::class.java)!!)
      modificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath)!!, OrderRootType.CLASSES)
      modificator.commitChanges()
      table.addJdk(pluginSdk, testRootDisposable)
    }
  }

  private fun getSandboxPath() = "$projectBasePath/sandbox"

  fun testMakeSimpleModule() {
    val module = setupSimplePluginProject()
    make(module)
    BaseCompilerTestCase.assertOutput(module, fs().dir("xxx").file("MyAction.class"))

    val sandbox = File(getSandboxPath())
    assertThat(sandbox).isDirectory()
    fs()
      .dir("plugins")
        .dir("pluginProject")
          .dir("META-INF").file("plugin.xml").end()
          .dir("classes")
            .dir("xxx").file("MyAction.class")
      .build().assertDirectoryEqual(sandbox)
  }

  fun testRebuildSimpleProject() {
    setupSimplePluginProject()
    val log = rebuild()
    assertThat(log.warnings).`as`("Rebuild finished with warnings: ${Arrays.toString(log.warnings)}").isEmpty()
  }

  fun testPrepareSimpleProjectForDeployment() {
    val module = setupSimplePluginProject()
    rebuild()
    prepareForDeployment(module)

    val outputFile = File("$projectBasePath/pluginProject.jar")
    assertThat(outputFile).isFile()
    fs()
      .archive("pluginProject.jar")
        .dir("META-INF").file("plugin.xml").file("MANIFEST.MF").end()
        .dir("xxx").file("MyAction.class")
      .build().assertFileEqual(outputFile)
  }

  fun testBuildProjectWithJpsModule() {
    val module = setupPluginProjectWithJpsModule()
    rebuild()
    prepareForDeployment(module)

    val outputFile = File("$projectBasePath/pluginProject.zip")
    assertThat(outputFile).isFile()
    fs()
      .archive("pluginProject.zip")
        .dir("pluginProject")
          .dir("lib")
            .archive("pluginProject.jar")
              .dir("META-INF").file("plugin.xml").file("MANIFEST.MF").end()
              .dir("xxx").file("MyAction.class").end()
              .end()
            .dir("jps")
              .archive("jps-plugin.jar").file("Builder.class")
      .build().assertFileEqual(outputFile)
  }

  private fun prepareForDeployment(module: Module) {
    val errorMessages = SmartList<String>()
    PrepareToDeployAction.doPrepare(module, errorMessages, SmartList<String>())
    assertThat(errorMessages).`as`("Building plugin zip finished with errors: $errorMessages").isEmpty()
  }

  private fun setupSimplePluginProject() = copyAndCreateModule("plugins/devkit/devkit-java-tests/testData/build/simple")

  private fun copyAndCreateModule(relativePath: String): Module {
    copyToProject(relativePath)
    val module = loadModule("$projectBasePath/pluginProject.iml")
    assertThat(ModuleType.get(module)).isEqualTo(PluginModuleType.getInstance())
    return module
  }

  private fun setupPluginProjectWithJpsModule(): Module {
    val module = copyAndCreateModule("plugins/devkit/devkit-java-tests/testData/build/withJpsModule")
    val jpsModule = loadModule("$projectBasePath/jps-plugin/jps-plugin.iml")
    ModuleRootModificationUtil.setModuleSdk(jpsModule, testProjectJdk)
    return module
  }
}
