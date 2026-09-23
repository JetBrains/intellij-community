// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.utils

import com.intellij.gradle.toolingExtension.modelAction.GradleModelController.GradleModelFetchRequest.GradleExecutionMode
import com.intellij.openapi.util.registry.RegistryManager

/**
 * Defines how the Kotlin model providers fetch the Kotlin Gradle models.
 */
object KotlinGradleModelFetchProperties {

    /**
     * Enables the parallel fetch of the Kotlin Gradle models.
     */
    const val PARALLEL_MODEL_FETCH_KEY: String = "kotlin.gradle.parallelModelFetch.enabled"

    /**
     * Returns the execution mode for a fetch request of a Kotlin model.
     *
     * The mode is parallel only when [PARALLEL_MODEL_FETCH_KEY] is enabled.
     */
    @JvmStatic
    fun getExecutionMode(): GradleExecutionMode =
        when (RegistryManager.getInstance().`is`(PARALLEL_MODEL_FETCH_KEY)) {
            true -> GradleExecutionMode.PARALLEL
            false -> GradleExecutionMode.SEQUENTIAL
        }
}
