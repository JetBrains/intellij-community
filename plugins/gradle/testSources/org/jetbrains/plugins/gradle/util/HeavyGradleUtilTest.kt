// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.plugins.gradle.GradleProjectTestCase

class HeavyGradleUtilTest : GradleProjectTestCase() {

  fun `test find module data`() {
    val projectNode =
      project(systemId = GradleConstants.SYSTEM_ID) {
        module("root", moduleFilePath = "root") {
          module("root.main", moduleFilePath = "root/main")
          module("root.test", moduleFilePath = "root/test")
        }
        module("module", moduleFilePath = "module", externalProjectPath = "$projectPath/module") {
          module("module.main", moduleFilePath = "module/main", externalProjectPath = "$projectPath/module")
          module("module.test", moduleFilePath = "module/test", externalProjectPath = "$projectPath/module")
        }
      }

    val linkedProjectNode =
      project(systemId = GradleConstants.SYSTEM_ID, projectPath = "$projectPath/linked") {
        module("linked", moduleFilePath = "linked", externalProjectPath = "$projectPath/linked") {
          module("linked.main", moduleFilePath = "linked/main", externalProjectPath = "$projectPath/linked")
          module("linked.test", moduleFilePath = "linked/test", externalProjectPath = "$projectPath/linked")
        }
      }

    applyProjectModel(projectNode, linkedProjectNode)
    assertGradleModuleData(mapOf(
      "root" to "root", "root.main" to "root", "root.test" to "root",
      "module" to "module", "module.main" to "module", "module.test" to "module",
      "linked" to "linked", "linked.main" to "linked", "linked.test" to "linked"
    ))
  }

  private fun assertGradleModuleData(modules: Map<String, String>) {
    val moduleManager = ModuleManager.getInstance(project)
    for ((name, path) in modules) {
      val module = moduleManager.findModuleByName(name)
      checkNotNull(module) { "Module '$name' isn't exist" }
      val moduleData = GradleUtil.findGradleModuleData(module)
      checkNotNull(moduleData) { "Data of module '$name' isn't exist" }
      assertEquals(path, moduleData.data.moduleFileDirectoryPath)
    }
  }
}