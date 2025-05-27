/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.execution.helper

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.execution.ParametersListUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.gradle.service.execution.toGroovyStringLiteral
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager.INIT_SCRIPT_KEY
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager.INIT_SCRIPT_PREFIX_KEY
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readLines

abstract class GradleExecutionHelperJvmArgumentsTestCase : GradleExecutionTestCase() {

  fun executeTaskAndCollectDaemonOptions(customVmOptions: List<String>): List<String> {
    val jvmArgumentsPath = createFile("jvmArguments.txt").toNioPath()
    val jvmPropertiesPath = createFile("jvmProperties.txt").toNioPath()
    try {
      waitForAnyGradleTaskExecution {
        ExternalSystemUtil.runTask(
          TaskExecutionSpec.create()
            .withProject(project)
            .withSystemId(GradleConstants.SYSTEM_ID)
            .withSettings(ExternalSystemTaskExecutionSettings().also {
              it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
              it.externalProjectPath = projectPath
              it.vmOptions = ParametersListUtil.join(customVmOptions)
              it.taskNames = listOf("help")
            })
            .withUserData(UserDataHolderBase().also {
              it.putUserData(INIT_SCRIPT_PREFIX_KEY, "ijJvmArgumentsCollector")
              it.putUserData(INIT_SCRIPT_KEY, INIT_SCRIPT
                .replace("JVM_ARGUMENTS_PATH", jvmArgumentsPath.toString().toGroovyStringLiteral())
                .replace("JVM_PROPERTIES_PATH", jvmPropertiesPath.toString().toGroovyStringLiteral())
              )
            })
            .build()
        )
      }
      return jvmArgumentsPath.readLines() + jvmPropertiesPath.readLines()
    }
    finally {
      jvmArgumentsPath.deleteIfExists()
      jvmPropertiesPath.deleteIfExists()
    }
  }

  companion object {

    @Language("Groovy")
    private val INIT_SCRIPT = """
        |import java.lang.management.ManagementFactory
        |import java.lang.management.RuntimeMXBean
        |import java.nio.file.Files
        |import java.nio.file.Path
        |import java.nio.file.Paths
        |
        |interface Properties {
        |  @SuppressWarnings('GroovyAssignabilityCheck')
        |  public static final Path jvmArgumentsPath = Paths.get(JVM_ARGUMENTS_PATH)
        |  @SuppressWarnings('GroovyAssignabilityCheck')
        |  public static final Path jvmPropertiesPath = Paths.get(JVM_PROPERTIES_PATH)
        |}
        |
        |RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean()
        |StringJoiner jvmArguments = new StringJoiner("\n")
        |for (String argument : runtimeMxBean.getInputArguments()) {
        |    jvmArguments.add(argument)
        |}
        |Files.write(Properties.jvmArgumentsPath, jvmArguments.toString().bytes)
        |
        |StringJoiner jvmProperties = new StringJoiner("\n")
        |for (Map.Entry<Object, Object> property: System.properties) {
        |  jvmProperties.add("-D" + property.key.toString() + "=" +property.value.toString())
        |}
        |Files.write(Properties.jvmPropertiesPath, jvmProperties.toString().bytes)
      """.trimMargin()
  }
}
