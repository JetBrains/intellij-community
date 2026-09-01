// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull

fun KotlinJsSubTargetReflection(binary: Any): KotlinJsSubTargetReflection = KotlinJsSubTargetReflectionImpl(binary)

interface KotlinJsSubTargetReflection {
    val name: String?
    val type: String
    val browserTest: KotlinJsBrowserTestReflection?
    val testRuns: Collection<KotlinTestRunReflection>?
}

private class KotlinJsSubTargetReflectionImpl(private val instance: Any) : KotlinJsSubTargetReflection {
    override val name: String? by lazy {
        instance.callReflectiveGetter("getName", logger)
    }

    override val type: String
        get() = instance.javaClass.simpleName

    override val browserTest: KotlinJsBrowserTestReflection? by lazy {
        if (instance.javaClass.getMethodOrNull("getTest") == null) return@lazy null
        instance.callReflectiveAnyGetter("getTest", logger)?.let(::KotlinJsBrowserTestReflection)
    }

    override val testRuns: Collection<KotlinTestRunReflection>? by lazy {
        if (instance.javaClass.getMethodOrNull("getTestRuns") == null) return@lazy null
        instance.callReflectiveGetter<Iterable<*>>("getTestRuns", logger)
            ?.filterNotNull()
            ?.map(::KotlinTestRunReflection)
    }
    companion object {
        private val logger = ReflectionLogger(KotlinJsSubTargetReflection::class.java)
    }
}

fun KotlinJsBrowserTestReflection(test: Any): KotlinJsBrowserTestReflection = KotlinJsBrowserTestReflectionImpl(test)

interface KotlinJsBrowserTestReflection {
    val allBrowserRunners: Set<KotlinBrowserTestRunnerReflection>
}

private class KotlinJsBrowserTestReflectionImpl(private val instance: Any) : KotlinJsBrowserTestReflection {
    override val allBrowserRunners: Set<KotlinBrowserTestRunnerReflection> by lazy {
        val allBrowserRunners = instance.callReflectiveGetter<Provider<*>>("getAllBrowserRunners", logger)
            ?: return@lazy emptySet()
        val browserRunners = allBrowserRunners.orNull as? Map<*, *> ?: return@lazy emptySet()
        browserRunners.mapNotNull { (name, runner) ->
            if (name !is String || runner == null) return@mapNotNull null
            KotlinBrowserTestRunnerReflection(name, runner)
        }.toSet()
    }

    companion object {
        private val logger = ReflectionLogger(KotlinJsBrowserTestReflection::class.java)
    }
}
