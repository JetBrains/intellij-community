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

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project
import com.intellij.rt.execution.junit.RepeatCount
import com.intellij.util.ObjectUtils.consumeIfCast

/**
 * Created by Nikita.Skvortsov
 * date: 11.09.2017.
 */
class JUnitRunConfigurationImporter : RunConfigurationImporter {

  private enum class TestKind(val dslName: String) {
    PACKAGE_NAME("packageName"),
    DIRECTORY("directory"),
    PATTERN("pattern"),
    CLASS("class"),
    METHOD("method"),
    CATEGORY("category");

    companion object {
      fun byDslName(dslName: String): TestKind? = TestKind.values().find { it.dslName == dslName }
    }
  }

  override fun canImport(typeName: String): Boolean = "junit" == typeName

  override fun process(project: Project, runConfig: RunConfiguration, cfg: MutableMap<String, *>, modelsProvider: IdeModifiableModelsProvider) {
    if (runConfig !is JUnitConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfig::class.java}")
    }

    val data = runConfig.persistentData
    val testKindName = cfg.keys.firstOrNull { it in TestKind.values().map { testKind -> testKind.dslName } && cfg[it] != null }
    if (testKindName != null) {
      consumeIfCast(cfg[testKindName], String::class.java) { testKindValue ->
        data.TEST_OBJECT = when (TestKind.byDslName(testKindName)) {
          TestKind.PACKAGE_NAME -> JUnitConfiguration.TEST_PACKAGE.also { data.PACKAGE_NAME = testKindValue }
          TestKind.DIRECTORY -> JUnitConfiguration.TEST_DIRECTORY.also { data.dirName = testKindValue }
          TestKind.PATTERN -> JUnitConfiguration.TEST_PATTERN.also { data.setPatterns(LinkedHashSet(testKindValue.split(','))) }
          TestKind.CLASS -> JUnitConfiguration.TEST_CLASS.also { data.MAIN_CLASS_NAME = testKindValue }
          TestKind.METHOD -> JUnitConfiguration.TEST_METHOD.also {
            val className = testKindValue.substringBefore('#')
            val methodName = testKindValue.substringAfter('#')
            data.MAIN_CLASS_NAME = className
            data.METHOD_NAME = methodName
          }
          TestKind.CATEGORY -> JUnitConfiguration.TEST_CATEGORY.also { data.setCategoryName(testKindValue) }
          null -> data.TEST_OBJECT
        }
      }
    }

    val repeatValue = cfg["repeat"]
    runConfig.repeatMode = when (repeatValue) {
      "untilStop"    -> RepeatCount.UNLIMITED
      "untilFailure" -> RepeatCount.UNTIL_FAILURE
      "untilSuccess" -> RepeatCount.UNTIL_SUCCESS
      is Number      -> RepeatCount.N.also { runConfig.repeatCount = repeatValue.toInt() }
      else           -> runConfig.repeatMode
    }

    consumeIfCast(cfg["vmParameters"], String::class.java) { runConfig.vmParameters = it }
    consumeIfCast(cfg["workingDirectory"], String::class.java) { runConfig.workingDirectory = it }
    consumeIfCast(cfg["passParentEnvs"], Boolean::class.java) { runConfig.isPassParentEnvs = it }
    consumeIfCast(cfg["envs"], Map::class.java) { runConfig.envs = it as Map<String, String> }

    consumeIfCast(cfg["moduleName"], String::class.java) {
      val module = modelsProvider.modifiableModuleModel.findModuleByName(it)
      if (module != null) {
        runConfig.setModule(module)
      }
    }

    consumeIfCast(cfg["shortenCommandLine"], String::class.java) {
      try {
        runConfig.shortenCommandLine = ShortenCommandLine.valueOf(it)
      } catch (e: IllegalArgumentException) {
        LOG.warn("Illegal value of 'shortenCommandLine': $it", e)
      }
    }
  }

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil
      .findConfigurationType(JUnitConfigurationType::class.java)
      .configurationFactories[0]

  companion object {
    val LOG = Logger.getInstance(JUnitRunConfigurationImporter::class.java)
  }
}