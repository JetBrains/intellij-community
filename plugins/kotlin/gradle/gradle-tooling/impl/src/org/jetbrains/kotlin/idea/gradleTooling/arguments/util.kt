// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.idea.gradleTooling.AbstractKotlinGradleModelBuilder
import org.jetbrains.kotlin.idea.gradleTooling.getDeclaredMethodOrNull
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper
import java.io.File
import java.lang.reflect.Method

private fun buildDependencyClasspath(compileKotlinTask: Task): List<String> {
    val abstractKotlinCompileClass =
        compileKotlinTask.javaClass.classLoader.loadClass(AbstractKotlinGradleModelBuilder.ABSTRACT_KOTLIN_COMPILE_CLASS)
    val getCompileClasspath =
        abstractKotlinCompileClass.getDeclaredMethodOrNull("getCompileClasspath") ?: abstractKotlinCompileClass.getDeclaredMethodOrNull(
            "getClasspath"
        ) ?: return emptyList()
    @Suppress("UNCHECKED_CAST")
    return (getCompileClasspath(compileKotlinTask) as? Collection<File>)?.map { it.path } ?: emptyList()
}

fun buildCachedArgsInfo(
    compileKotlinTask: Task,
    cacheMapper: CompilerArgumentsCacheMapper,
): CachedExtractedArgsInfo {
    val cachedCurrentArguments = CompilerArgumentsCachingChain.extractAndCacheTask(compileKotlinTask, cacheMapper, defaultsOnly = false)
    val cachedDefaultArguments = CompilerArgumentsCachingChain.extractAndCacheTask(compileKotlinTask, cacheMapper, defaultsOnly = true)
    val dependencyClasspath =
        buildDependencyClasspath(compileKotlinTask).map { KotlinCachedRegularCompilerArgument(cacheMapper.cacheArgument(it)) }
    return CachedExtractedArgsInfo(cacheMapper.cacheOriginIdentifier, cachedCurrentArguments, cachedDefaultArguments, dependencyClasspath)
}

fun buildSerializedArgsInfo(
    compileKotlinTask: Task,
    cacheMapper: CompilerArgumentsCacheMapper,
    logger: Logger
): CachedSerializedArgsInfo {
    val compileTaskClass = compileKotlinTask.javaClass
    val getCurrentArguments = compileTaskClass.getMethodOrNull("getSerializedCompilerArguments")
    val getDefaultArguments = compileTaskClass.getMethodOrNull("getDefaultSerializedCompilerArguments")
    val currentArguments = safelyGetArguments(compileKotlinTask, getCurrentArguments, logger)
        .map { KotlinCachedRegularCompilerArgument(cacheMapper.cacheArgument(it)) }
    val defaultArguments = safelyGetArguments(compileKotlinTask, getDefaultArguments, logger)
        .map { KotlinCachedRegularCompilerArgument(cacheMapper.cacheArgument(it)) }
    val dependencyClasspath = buildDependencyClasspath(compileKotlinTask)
        .map { KotlinCachedRegularCompilerArgument(cacheMapper.cacheArgument(it)) }

    return CachedSerializedArgsInfo(cacheMapper.cacheOriginIdentifier, currentArguments, defaultArguments, dependencyClasspath)
}

@Suppress("UNCHECKED_CAST")
private fun safelyGetArguments(compileKotlinTask: Task, accessor: Method?, logger: Logger) = try {
    accessor?.invoke(compileKotlinTask) as? List<String>
} catch (e: Exception) {
    logger.warn(e.message ?: "Unexpected exception: $e", e)
    null
} ?: emptyList()
