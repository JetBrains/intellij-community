/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.junit

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project
import com.intellij.rt.execution.junit.RepeatCount
import java.util.*

/**
 * Created by Nikita.Skvortsov
 * date: 11.09.2017.
 */
class JUnitRunConfigurationImporter : RunConfigurationImporter {
  override fun canImport(typeName: String): Boolean = "junit" == typeName

  override fun process(project: Project, runConfig: RunConfiguration, cfg: MutableMap<String, *>, modelsProvider: IdeModifiableModelsProvider) {
    if (runConfig !is JUnitConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfig::class.java}")
    }

    val data = runConfig.persistentData
    val testKind = cfg.keys.firstOrNull { it in listOf("packageName", "directory", "pattern", "className", "method", "category") && cfg[it] != null }
    if (testKind != null) {
      (cfg[testKind] as? String)?.let { testKindValue ->
        data.TEST_OBJECT = when (testKind) {
          "package" -> JUnitConfiguration.TEST_PACKAGE.also { data.PACKAGE_NAME = testKindValue }
          "directory" -> JUnitConfiguration.TEST_DIRECTORY.also { data.dirName = testKindValue }
          "pattern" -> JUnitConfiguration.TEST_PATTERN.also { data.setPatterns(LinkedHashSet(testKindValue.split(delimiters = ','))) }
          "class" -> JUnitConfiguration.TEST_CLASS.also { data.MAIN_CLASS_NAME = testKindValue }
          "method" -> JUnitConfiguration.TEST_METHOD.also {
            val className = testKindValue.substringBefore('#')
            val methodName = testKindValue.substringAfter('#')
            data.MAIN_CLASS_NAME = className
            data.METHOD_NAME = methodName
          }
          "category" -> JUnitConfiguration.TEST_CATEGORY.also { data.setCategoryName(testKindValue) }
          else -> data.TEST_OBJECT
        }
      }
    }

    val repeatValue = cfg["repeat"]
    runConfig.repeatMode = when (repeatValue) {
      "untilStop"    -> RepeatCount.UNLIMITED
      "untilFailure" -> RepeatCount.UNTIL_FAILURE
      is Number      -> RepeatCount.N.also { runConfig.repeatCount = repeatValue.toInt() }
      else           -> runConfig.repeatMode
    }

    (cfg["vmParameters"] as? String)?.let { runConfig.vmParameters = it }
    (cfg["workingDirectory"] as? String)?.let { runConfig.workingDirectory = it }
    (cfg["passParentEnvs"] as? Boolean)?.let { runConfig.isPassParentEnvs = it }
    (cfg["envs"] as? Map<*,*>)?.let { runConfig.envs = it as Map<String, String> }

    (cfg["moduleName"] as? String)
      ?.let { modelsProvider.modifiableModuleModel.findModuleByName(it) }
      ?.let { runConfig.setModule(it) }
  }

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil
      .findConfigurationType<JUnitConfigurationType>(JUnitConfigurationType::class.java)
      .configurationFactories[0]
}