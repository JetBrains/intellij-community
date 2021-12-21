// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder.Companion.settingsScript
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.junit.Test

class GradleTasksIndicesTest : GradleTasksIndicesTestCase() {

  @Test
  fun `test test tasks matching`() {
    importProject(buildscript {
      withTask("myTask")
      withTask("myTask1")
      withTask("myTask2")
    })

    findTasks("Task").assertTasks()
    findTasks(":Task").assertTasks()
    findTasks("my").assertTasks(":myTask", ":myTask1", ":myTask2")
    findTasks(":my").assertTasks(":myTask", ":myTask1", ":myTask2")
    findTasks("myTask").assertTasks(":myTask")
    findTasks(":myTask").assertTasks(":myTask")
    findTasks("myTask1").assertTasks(":myTask1")
    findTasks(":myTask1").assertTasks(":myTask1")
    findTasks("myTask2").assertTasks(":myTask2")
    findTasks(":myTask2").assertTasks(":myTask2")
  }

  @Test
  fun `test task finding`() {
    createProjectSubFile("build.gradle", buildscript { withJavaPlugin() })
    createProjectSubFile("settings.gradle", settingsScript("project") {
      include("module")
      include("module:sub-module")
      includeBuild("composite")
      includeBuild("../composite-flat")
    })
    createProjectSubFile("module/build.gradle", buildscript { withJavaPlugin() })
    createProjectSubFile("module/sub-module/build.gradle", buildscript { withJavaPlugin() })
    createProjectSubFile("composite/build.gradle", buildscript { withJavaPlugin() })
    createProjectSubFile("composite/module/build.gradle", buildscript { withJavaPlugin() })
    createProjectSubFile("composite/settings.gradle", settingsScript("composite") { include("module") })
    createProjectSubFile("../composite-flat/build.gradle", buildscript { withJavaPlugin() })
    createProjectSubFile("../composite-flat/module/build.gradle", buildscript { withJavaPlugin() })
    createProjectSubFile("../composite-flat/settings.gradle", settingsScript("composite-flat") { include("module")  })
    importProject()

    findTasks("test").assertTasks(":test", ":module:test", ":module:sub-module:test")
    findTasks(":test").assertTasks(":test")
    findTasks("module:test").assertTasks(":module:test")
    findTasks(":module:test").assertTasks(":module:test")
    findTasks("sub-module:test").assertTasks()
    findTasks(":sub-module:test").assertTasks()
    findTasks("module:sub-module:test").assertTasks(":module:sub-module:test")
    findTasks(":module:sub-module:test").assertTasks(":module:sub-module:test")
    findTasks("test", modulePath = "module").assertTasks(":module:test", ":module:sub-module:test")
    findTasks(":test", modulePath = "module").assertTasks(":test")
    findTasks("megalodon:test", modulePath = "module").assertTasks()
    findTasks("sub-module:test", modulePath = "module").assertTasks(":module:sub-module:test")
    findTasks(":sub-module:test", modulePath = "module").assertTasks()
    findTasks("module:sub-module:test", modulePath = "module").assertTasks()
    findTasks(":module:sub-module:test", modulePath = "module").assertTasks(":module:sub-module:test")
    findTasks("test", modulePath = "module/sub-module").assertTasks(":module:sub-module:test")
    findTasks(":test", modulePath = "module/sub-module").assertTasks(":test")
    findTasks("megalodon:test", modulePath = "module/sub-module").assertTasks()
    findTasks("sub-module:test", modulePath = "module/sub-module").assertTasks()
    findTasks(":sub-module:test", modulePath = "module/sub-module").assertTasks()
    findTasks("module:sub-module:test", modulePath = "module/sub-module").assertTasks()
    findTasks(":module:sub-module:test", modulePath = "module/sub-module").assertTasks(":module:sub-module:test")

    findTasks("composite:test").assertTasks()
    findTasks(":composite:test").assertTasks(":composite:test")
    findTasks("composite:module:test").assertTasks()
    findTasks(":composite:module:test").assertTasks(":composite:module:test")
    findTasks("test", modulePath = "composite").assertTasks(":composite:test", ":composite:module:test")
    findTasks(":test", modulePath = "composite").assertTasks(":composite:test")
    findTasks("composite:test", modulePath = "composite").assertTasks()
    findTasks(":composite:test", modulePath = "composite").assertTasks()
    findTasks("module:test", modulePath = "composite").assertTasks(":composite:module:test")
    findTasks(":module:test", modulePath = "composite").assertTasks(":composite:module:test")
    findTasks("composite-flat:test", modulePath = "composite").assertTasks()
    findTasks(":composite-flat:test", modulePath = "composite").assertTasks(":composite-flat:test")
    findTasks("composite-flat:module:test", modulePath = "composite").assertTasks()
    findTasks(":composite-flat:module:test", modulePath = "composite").assertTasks(":composite-flat:module:test")
    findTasks("test", modulePath = "composite/module").assertTasks(":composite:module:test")
    findTasks(":test", modulePath = "composite/module").assertTasks(":composite:test")
    findTasks("module:test", modulePath = "composite/module").assertTasks()
    findTasks(":module:test", modulePath = "composite/module").assertTasks(":composite:module:test")
    findTasks("composite-flat:test", modulePath = "composite/module").assertTasks()
    findTasks(":composite-flat:test", modulePath = "composite/module").assertTasks(":composite-flat:test")
    findTasks("composite-flat:module:test", modulePath = "composite/module").assertTasks()
    findTasks(":composite-flat:module:test", modulePath = "composite/module").assertTasks(":composite-flat:module:test")

    findTasks("composite-flat:test").assertTasks()
    findTasks(":composite-flat:test").assertTasks(":composite-flat:test")
    findTasks("composite-flat:module:test").assertTasks()
    findTasks(":composite-flat:module:test").assertTasks(":composite-flat:module:test")
    findTasks("test", modulePath = "../composite-flat").assertTasks(":composite-flat:test", ":composite-flat:module:test")
    findTasks(":test", modulePath = "../composite-flat").assertTasks(":composite-flat:test")
    findTasks("module:test", modulePath = "../composite-flat").assertTasks(":composite-flat:module:test")
    findTasks(":module:test", modulePath = "../composite-flat").assertTasks(":composite-flat:module:test")
    findTasks("composite-flat:test", modulePath = "../composite-flat").assertTasks()
    findTasks(":composite-flat:test", modulePath = "../composite-flat").assertTasks()
    findTasks("composite-flat:module:test", modulePath = "../composite-flat").assertTasks()
    findTasks(":composite-flat:module:test", modulePath = "../composite-flat").assertTasks()
  }
}