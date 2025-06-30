// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * The main purpose of this interface is to selectively notify about scripts configuration changes
 * in regard to compiler plugins setup.
 *
 * This is to avoid use of [KotlinCompilerSettingsListener], so that changes do not have global effect on other modules.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface KotlinCompilerPluginsScriptConfigurationListener {
    fun scriptConfigurationsChanged()

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<KotlinCompilerPluginsScriptConfigurationListener> = Topic(
          KotlinCompilerPluginsScriptConfigurationListener::class.java,
          Topic.BroadcastDirection.TO_CHILDREN,
          /* immediateDelivery = */ true
        )
    }
}