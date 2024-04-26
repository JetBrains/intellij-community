// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.execution

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.gradleJava.run.KotlinJvmRunTaskData
import org.jetbrains.plugins.gradle.execution.build.GradleInitScriptGenerator
import org.jetbrains.plugins.gradle.execution.build.GradleInitScriptParameters

internal class KotlinMPGradleInitScriptGenerator : GradleInitScriptGenerator {
    override fun isApplicable(module: Module): Boolean {
        return KotlinJvmRunTaskData.findSuitableKotlinJvmRunTask(module) != null
    }

    override fun generateInitScript(params: GradleInitScriptParameters): String? {
        return org.jetbrains.kotlin.idea.gradleJava.execution.generateInitScript(params)
    }
}