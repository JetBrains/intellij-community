// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.extensions

import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Common interface for Run configurations producers that apply to multiplatform modules.
 * This interface must be implemented by every producer that apply to multiplatform modules.
 */
interface KotlinMultiplatformCommonProducersProvider {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinMultiplatformCommonProducersProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.idea.gradleJava.kotlinMultiplatformProducersProvider")
    }

    fun isProducedByCommonProducer(configuration: ConfigurationFromContext): Boolean

}