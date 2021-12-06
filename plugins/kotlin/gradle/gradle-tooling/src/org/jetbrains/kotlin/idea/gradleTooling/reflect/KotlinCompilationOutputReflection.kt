// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("FunctionName")

package org.jetbrains.kotlin.idea.gradleTooling.reflect

import java.io.File

fun KotlinCompilationOutputReflection(compilationOutputs: Any): KotlinCompilationOutputReflection =
    KotlinCompilationOutputReflectionImpl(compilationOutputs)

interface KotlinCompilationOutputReflection {
    val classesDirs: Set<File>?
    val resourcesDir: File?
}

private class KotlinCompilationOutputReflectionImpl(private val instance: Any) : KotlinCompilationOutputReflection {
    override val classesDirs: Set<File>? by lazy {
        instance.callReflective("getClassesDirs", parameters(), returnType<Iterable<File>>(), logger)?.toSet()
    }

    override val resourcesDir: File? by lazy {
        instance.callReflective("getResourcesDir", parameters(), returnType<File>(), logger)
    }

    companion object {
        val logger = ReflectionLogger(KotlinCompilationOutputReflection::class.java)
    }
}
