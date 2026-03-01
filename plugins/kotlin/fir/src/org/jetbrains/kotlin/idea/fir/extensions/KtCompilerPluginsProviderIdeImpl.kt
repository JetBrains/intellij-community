// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider.CompilerPluginType
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.extensions.ExtensionPointDescriptor
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerPluginsScriptConfigurationListener
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.facet.isKotlinFacet
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity

@OptIn(ExperimentalCompilerApi::class)
@ApiStatus.Internal
internal class KtCompilerPluginsProviderIdeImpl(
    private val project: Project,
    cs: CoroutineScope,
) : KotlinCompilerPluginsProvider, Disposable {

    private val onlyBundledPluginsEnabledRegistryValue: RegistryValue = Registry.get("kotlin.k2.only.bundled.compiler.plugins.enabled")

    private val pluginsCacheCachedValue: SynchronizedClearableLazy<KtCompilerPluginsCache?> = SynchronizedClearableLazy {
        if (TrustedProjects.isProjectTrusted(project)) {
            KtCompilerPluginsCache.new(project, onlyBundledPluginsEnabledRegistryValue.asBoolean())
        } else {
            null
        }
    }

    init {
        cs.launch {
            project.workspaceModel.eventLog.collect { event ->
                val facetChanges = event.getChanges<FacetEntity>() + event.getChanges<KotlinSettingsEntity>()

                val hasChanges = facetChanges.any { change ->
                    val entities = listOfNotNull(change.oldEntity, change.newEntity)
                    entities.any { it.isKotlinFacet() }
                }
                if (hasChanges) {
                    resetPluginsCache()
                }
            }
        }
        val messageBusConnection = project.messageBus.connect(this)
        messageBusConnection.subscribe(
            KotlinCompilerSettingsListener.TOPIC, object : KotlinCompilerSettingsListener {
                override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
                    resetPluginsCache()
                }
            })
        messageBusConnection.subscribe(
            KotlinCompilerPluginsScriptConfigurationListener.TOPIC, object : KotlinCompilerPluginsScriptConfigurationListener {
                override fun scriptConfigurationsChanged() {
                    (pluginsCacheCachedValue.valueIfInitialized ?: return).resetScriptCache()
                }
            })
        onlyBundledPluginsEnabledRegistryValue.addListener(
            object : RegistryValueListener {
                override fun afterValueChanged(value: RegistryValue) {
                    resetPluginsCache()
                }
            }, this
        )
    }

    @OptIn(KaExperimentalApi::class)
    override fun <T : Any> getRegisteredExtensions(module: KaModule, extensionType: ExtensionPointDescriptor<T>): List<T> {
        val pluginsCache = pluginsCacheCachedValue.value ?: return emptyList()
        return pluginsCache.getRegisteredExtensions(
            module = module,
            extensionType = extensionType
        )
    }

    override fun isPluginOfTypeRegistered(module: KaModule, pluginType: CompilerPluginType): Boolean {
        val pluginsCache = pluginsCacheCachedValue.value ?: return false
        return pluginsCache.isPluginOfTypeRegistered(
            module = module,
            pluginType = pluginType
        )
    }

    override fun dispose() {
        resetPluginsCache()
    }

    /**
     * Throws away the cache for all the registered plugins, and executes all the disposables
     * registered in the corresponding [CompilerPluginRegistrar.ExtensionStorage]s.
     *
     * Note: we drop the [pluginsCacheCachedValue] synchronously, so that
     * the [KtCompilerPluginsCache.new] call and all the calls to [KotlinBundledFirCompilerPluginProvider.provideBundledPluginJar] in it
     * either have not yet started, or have already completed.
     *
     * Otherwise, race conditions similar to KTIJ-37664 may occur.
     */
    private fun resetPluginsCache() {
        val cache = pluginsCacheCachedValue.dropSynchronously()
        cache?.dispose()
    }

    companion object {
        fun getInstance(project: Project): KtCompilerPluginsProviderIdeImpl {
            return KotlinCompilerPluginsProvider.getInstance(project) as KtCompilerPluginsProviderIdeImpl
        }
    }
}

