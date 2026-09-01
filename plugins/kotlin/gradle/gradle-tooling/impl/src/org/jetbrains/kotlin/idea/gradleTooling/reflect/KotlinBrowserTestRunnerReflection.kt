// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull

class KotlinBrowserTestRunnerReflection(
    val name: String,
    private val instance: Any,
) {
    val isChromiumTestRunnerDsl: Boolean by lazy {
        val chromiumTestRunnerDslClass = instance.javaClass.classLoader
                .loadClassOrNull("org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBrowserTestDsl\$ChromiumTestRunnerDsl")
        chromiumTestRunnerDslClass?.isInstance(instance) == true
    }

    val testTask: Task? by lazy {
        if (instance.javaClass.getMethodOrNull("getTestTaskProvider") == null) return@lazy null
        instance.callReflectiveGetter<Provider<*>>("getTestTaskProvider", logger)?.orNull as? Task
    }

    companion object {
        private val logger = ReflectionLogger(KotlinBrowserTestRunnerReflection::class.java)
    }
}
