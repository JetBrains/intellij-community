/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private var pluginSdk: Sdk? = null

  override fun setUpJdk() {
    super.setUpJdk()
    runWriteAction {
      val table = ProjectJdkTable.getInstance()
      pluginSdk = table.createSdk("IDEA plugin SDK", SdkType.findInstance(IdeaJdk::class.java))
      val modificator = pluginSdk!!.sdkModificator
      modificator.sdkAdditionalData = Sandbox(getSandboxPath(), testProjectJdk, pluginSdk)
      val rootPath = FileUtil.toSystemIndependentName(PathManager.getJarPathForClass(FileUtilRt::class.java)!!)
      modificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath)!!, OrderRootType.CLASSES)
      modificator.commitChanges()
      table.addJdk(pluginSdk!!)
    }
  }

  private fun getSandboxPath() = "$projectBasePath/sandbox"

  override fun tearDown() {
    try {
      if (pluginSdk != null) {
        runWriteAction { ProjectJdkTable.getInstance().removeJdk(pluginSdk!!) }
      }
    }
    finally {
      super.tearDown()
    }
  }

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

  private fun setupSimplePluginProject() = copyAndCreateModule("plugins/devkit/testData/build/simple")

  private fun copyAndCreateModule(relativePath: String): Module {
    copyToProject(relativePath)
    val module = loadModule("$projectBasePath/pluginProject.iml")
    assertThat(ModuleType.get(module)).isEqualTo(PluginModuleType.getInstance())
    return module
  }

  private fun setupPluginProjectWithJpsModule(): Module {
    val module = copyAndCreateModule("plugins/devkit/testData/build/withJpsModule")
    val jpsModule = loadModule("$projectBasePath/jps-plugin/jps-plugin.iml")
    ModuleRootModificationUtil.setModuleSdk(jpsModule, testProjectJdk)
    return module
  }
}
