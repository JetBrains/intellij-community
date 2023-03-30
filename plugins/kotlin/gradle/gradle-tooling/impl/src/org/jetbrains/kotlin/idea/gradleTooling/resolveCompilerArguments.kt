// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Task
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompileTaskReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompilerArgumentsResolverReflection

internal fun resolveCompilerArguments(compileTask: Task): List<String>? {
    /* Preferred approach in Kotlin 1.9+ */
    KotlinCompilerArgumentsResolverReflection(compileTask.project, compileTask::class.java.classLoader)?.let { resolver ->
        return resolver.resolveCompilerArguments(compileTask)
    }

    /* Less preferred approach w/o IdeCompilerArgumentsResolver on KGP */
    return KotlinCompileTaskReflection(compileTask).legacyCompilerArguments
}
