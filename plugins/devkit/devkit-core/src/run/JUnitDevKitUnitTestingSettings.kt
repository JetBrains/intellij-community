// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.run

import com.intellij.configurationStore.Property
import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class JUnitDevKitUnitTestingSettings(
  private val project: Project,
) : SimplePersistentStateComponent<JUnitDevKitUnitTestingSettingsState>(JUnitDevKitUnitTestingSettingsState()) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): JUnitDevKitUnitTestingSettings = project.service()
  }

  fun apply(module: Module?, javaParameters: JavaParameters) {
    if (module == null) return
    val runner = state.runners.firstOrNull {
      it.enabledForModules.any { matchesName(module.name, it) } && it.disabledForModules.none { matchesName(module.name, it) }
    } ?: return

    for (arg in runner.jvmArgs) {
      if (arg.startsWith("-D")) {
        val parts = arg.substring(2).split('=', limit = 2)
        if (parts.size == 1) {
          javaParameters.vmParametersList.addProperty(parts[0])
        }
        else {
          javaParameters.vmParametersList.addProperty(parts[0], expandMacros(parts[1], module, javaParameters, project))
        }
      }
      else {
        javaParameters.vmParametersList.add(arg)
      }
    }

    for (env in runner.envs) {
      val parts = env.split('=', limit = 2)
      if (parts.size == 1) continue
      javaParameters.addEnv(parts[0], expandMacros(parts[1], module, javaParameters, project))
    }

    val mainClassModule = runner.mainClassModule?.let { ModuleManager.getInstance(project).findModuleByName(it) }
    if (mainClassModule != null) {
      javaParameters.mainClass = runner.mainClass
      javaParameters.classPath.clear()
      javaParameters.configureByModule(mainClassModule, JavaParameters.CLASSES_ONLY)
    }

    javaParameters.setShortenCommandLine(ShortenCommandLine.ARGS_FILE)
    javaParameters.setUseDynamicVMOptions(true)
  }

  private fun expandMacros(value: String, module: Module, javaParameters: JavaParameters, project: Project) = value
    .replace("${'$'}TEST_MODULE_CLASSPATH$", javaParameters.classPath.pathsString)
    .replace("${'$'}TEST_MODULE_NAME$", module.name)
    .replace("${'$'}TEST_PROJECT_BASE_PATH$", project.basePath ?: "")

  private fun matchesName(moduleName: String, pattern: String) =
    if (pattern.lastOrNull() == '*') {
      moduleName.startsWith(pattern.substring(0, pattern.length - 1))
    }
    else {
      moduleName == pattern
    }
}

@ApiStatus.Internal
internal class JUnitDevKitUnitTestingSettingsState : BaseState() {
  @Property(description = "List of available runners")
  var runners by list<JUnitDevKitUnitTestingSettingsRunner>()
}

@ApiStatus.Internal
internal class JUnitDevKitUnitTestingSettingsRunner : BaseState() {
  @Property(description = "Name")
  var name by string()

  @Property(description = "Module name where the main class is defined")
  var mainClassModule by string()

  @Property(description = "Main class")
  var mainClass by string()

  @Property(description = "List of module names (or patterns) this runner should be applied to")
  var enabledForModules by list<String>()

  @Property(description = "List of module names (or patterns) this runner must not be applied to")
  var disabledForModules by list<String>()

  @Property(description = "Additional JVM arguments")
  var jvmArgs by list<String>()

  @Property(description = "Additional environment variables")
  var envs by list<String>()
}
