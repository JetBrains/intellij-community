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
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.module.Module
import com.intellij.rt.execution.junit.RepeatCount
import java.util.*

/**
 * Created by Nikita.Skvortsov
 * date: 11.09.2017.
 */
class JUnitRunConfigurationImporter : RunConfigurationImporter {
  override fun canHandle(typeName: String): Boolean = "junit" == typeName

  override fun process(module: Module, runConfig: RunConfiguration, cfg: MutableMap<String, *>) {
    if (runConfig !is JUnitConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfig::class.java}")
    }

    val testKind = cfg.keys.firstOrNull { it in listOf("package", "directory", "pattern", "class", "method", "category") }
    val testKindValue = cfg[testKind] as? String

    if (testKindValue == null) {
      return
    }

    val data = runConfig.persistentData
    when (testKind) {
      "package"   -> { data.TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE; data.PACKAGE_NAME = testKindValue }
      "directory" -> { data.TEST_OBJECT = JUnitConfiguration.TEST_DIRECTORY; data.dirName = testKindValue  }
      "pattern"   -> { data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN; data.setPatterns(LinkedHashSet(testKindValue.split(delimiters = ','))) }
      "class"     -> {
        data.TEST_OBJECT = JUnitConfiguration.TEST_CLASS
        data.MAIN_CLASS_NAME = testKindValue
      }
      "method"    -> {
        data.TEST_OBJECT = JUnitConfiguration.TEST_METHOD
        val className = testKindValue.substringBefore('#')
        val methodName = testKindValue.substringAfter('#')
        data.MAIN_CLASS_NAME = className
        data.METHOD_NAME = methodName
      }
      "category"  -> {
        data.TEST_OBJECT = JUnitConfiguration.TEST_CATEGORY
        data.setCategoryName(testKindValue)
      }
    }

    val repeatValue = cfg["repeat"]
    runConfig.repeatMode = when (repeatValue) {
      "untilStop"    -> RepeatCount.UNLIMITED
      "untilFailure" -> RepeatCount.UNTIL_FAILURE
      is Number      -> RepeatCount.N.also { runConfig.repeatCount = repeatValue.toInt() }
      else           -> RepeatCount.ONCE
    }

    (cfg["jvmArgs"] as? String)?.let { runConfig.vmParameters = it }
  }

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil
      .findConfigurationType<JUnitConfigurationType>(JUnitConfigurationType::class.java)
      .configurationFactories[0]
}