// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangeListener
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsCache
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper

/**
 * Extension point for overriding default Kotlin scripting support.
 *
 * Implementation should store script configuration internally (in memory and/or fs),
 * and provide it inside [collectConfigurations] using the [ScriptClassRootsCache.LightScriptInfo].
 * Custom data can be attached to [ScriptClassRootsCache.LightScriptInfo] and retrieved
 * by calling [ScriptClassRootsCache.getLightScriptInfo].
 *
 * [ScriptChangeListener] can be used to listen script changes.
 * [CompositeScriptConfigurationManager.updater] should be used to schedule configuration reloading.
 *
 * [isApplicable] should return true for files that is covered by that support.
 *
 * [isConfigurationLoadingInProgress] is used to pause analyzing.
 *
 * [onTrivialUpdate] is used to state that cache updated, but no source roots have chained.
 *
 * [onUpdateException] indicates that update ended with an exceptional result (except PCE).
 *
 * [getConfigurationImmediately] is used to get scripting configuration for a supported file
 * (for which [isApplicable] returns true) immediately. It may be useful for intensively created files
 * if it is expensive to run full update for each file creation and/or update
 *
 * Long read: [idea/idea-gradle/src/org/jetbrains/kotlin/idea/scripting/gradle/README.md].
 *
 * @sample GradleBuildRootsManager
 */
interface ScriptingSupport {
    fun isApplicable(file: VirtualFile): Boolean
    fun isConfigurationLoadingInProgress(file: KtFile): Boolean
    fun collectConfigurations(builder: ScriptClassRootsBuilder)
    fun afterUpdate()
    fun onTrivialUpdate() = Unit
    fun onUpdateException(exception: Exception) = Unit
    fun getConfigurationImmediately(file: VirtualFile): ScriptCompilationConfigurationWrapper? = null

    companion object {
        val EP_NAME: ProjectExtensionPointName<ScriptingSupport> =
            ProjectExtensionPointName("org.jetbrains.kotlin.scripting.idea.scriptingSupport")
    }
}
