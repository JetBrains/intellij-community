// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.loader

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

/**
 * Provides the way to loading and saving script configuration.
 *
 * @see [org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptConfigurationManager] for more details.
 */
interface ScriptConfigurationLoader {
    fun shouldRunInBackground(scriptDefinition: ScriptDefinition): Boolean = false

    fun interceptBackgroundLoading(file: VirtualFile, isFirstLoad: Boolean, doLoad: () -> Unit): Boolean = false

    fun hideInterceptedNotification(file: VirtualFile) = Unit

    /**
     * Implementation should load configuration and call `context.suggestNewConfiguration` or `saveNewConfiguration`.
     *
     * @return true when this loader is applicable.
     */
    fun loadDependencies(
        isFirstLoad: Boolean,
        ktFile: KtFile,
        scriptDefinition: ScriptDefinition,
        context: ScriptConfigurationLoadingContext
    ): Boolean

    companion object {
        val EP_NAME: ProjectExtensionPointName<ScriptConfigurationLoader> =
            ProjectExtensionPointName("org.jetbrains.kotlin.scripting.idea.loader")
    }
}
