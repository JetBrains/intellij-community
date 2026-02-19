// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations

import com.intellij.execution.RunnerAndConfigurationSettings

class RunConfigurationsCheckConfiguration {
    interface RunConfigurationDetail {
        val name: String
        fun value(settings: RunnerAndConfigurationSettings): Any?
    }

    var details = mutableSetOf<RunConfigurationDetail>()

    fun includeDetail(name: String, value: RunnerAndConfigurationSettings.() -> Any?) {
        details += RunConfigurationDetailImpl(name, value)
    }
}

private class RunConfigurationDetailImpl(
    override val name: String,
    private val valueProvider: (RunnerAndConfigurationSettings) -> Any?
) : RunConfigurationsCheckConfiguration.RunConfigurationDetail {
    override fun value(settings: RunnerAndConfigurationSettings): Any? = valueProvider(settings)
}