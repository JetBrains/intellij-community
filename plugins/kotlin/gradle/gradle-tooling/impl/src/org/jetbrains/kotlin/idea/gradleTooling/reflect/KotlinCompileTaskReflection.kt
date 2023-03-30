// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Task

fun KotlinCompileTaskReflection(task: Task): KotlinCompileTaskReflection {
    return KotlinCompileTaskReflectionImpl(task)
}

interface KotlinCompileTaskReflection {
    val task: Task
    val legacyCompilerArguments: List<String>
}

private class KotlinCompileTaskReflectionImpl(override val task: Task) : KotlinCompileTaskReflection {
    override val legacyCompilerArguments: List<String> =
        task.callReflectiveGetter<List<String>>("getSerializedCompilerArgumentsIgnoreClasspathIssues", logger)
            .orEmpty()

    companion object {
        val logger = ReflectionLogger(KotlinCompileTaskReflection::class.java)
    }
}
