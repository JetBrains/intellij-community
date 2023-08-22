// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Task

fun KotlinCompileTaskReflection(task: Task): KotlinCompileTaskReflection {
    return KotlinCompileTaskReflectionImpl(task)
}

interface KotlinCompileTaskReflection {
    val task: Task
    val serializedCompilerArguments: List<String>
}

private class KotlinCompileTaskReflectionImpl(override val task: Task) : KotlinCompileTaskReflection {
    override val serializedCompilerArguments: List<String>
        get() = runCatching {
            task.callReflectiveGetter<List<String>>("getSerializedCompilerArguments", logger)
        }.recoverCatching {
            logger.logIssue("Failed calling 'getSerializedCompilerArguments'", it)
            task.callReflectiveGetter<List<String>>("getSerializedCompilerArgumentsIgnoreClasspathIssues", logger)
        }.onFailure {
            logger.logIssue("Failed getting 'serializedCompilerArguments' from '${task.path}'")
        }.getOrNull().orEmpty()


    companion object {
        val logger = ReflectionLogger(KotlinCompileTaskReflection::class.java)
    }
}
