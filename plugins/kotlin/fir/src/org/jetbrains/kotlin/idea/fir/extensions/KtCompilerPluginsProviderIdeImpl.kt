// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.orNull
import com.intellij.util.lang.UrlClassLoader
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import org.jetbrains.kotlin.analysis.project.structure.KtCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.plugins.processCompilerPluginsOptions
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.extensions.ProjectExtensionDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.ideaModule
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.util.Frontend10ApiUsage
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.util.ServiceLoaderLite
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentMap

@OptIn(ExperimentalCompilerApi::class)
internal class KtCompilerPluginsProviderIdeImpl(private val project: Project) : KtCompilerPluginsProvider(), Disposable {
    private val pluginsCacheCachedValue: SynchronizedClearableLazy<PluginsCache?> = SynchronizedClearableLazy { createNewCache() }
    private val pluginsCache: PluginsCache?
        get() = pluginsCacheCachedValue.value

    init {
        val messageBusConnection = project.messageBus.connect(this)
        messageBusConnection.subscribe(WorkspaceModelTopics.CHANGED,
                                       object : WorkspaceModelChangeListener {
                override fun changed(event: VersionedStorageChange) {
                    val hasChanges = event.getChanges(FacetEntity::class.java).any { change ->
                        change.facetTypes.any { it == KotlinFacetType.ID }
                    }
                    if (hasChanges) {
                        pluginsCacheCachedValue.drop()
                    }
                }
            }
        )
        messageBusConnection.subscribe(KotlinCompilerSettingsListener.TOPIC,
            object : KotlinCompilerSettingsListener {
                override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
                    pluginsCacheCachedValue.drop()
                }
            }
        )
    }

    private val EntityChange<FacetEntity>.facetTypes: List<String>
        get() = when (this) {
            is EntityChange.Added -> listOf(entity.facetType)
            is EntityChange.Removed -> listOf(entity.facetType)
            is EntityChange.Replaced -> listOf(oldEntity.facetType, newEntity.facetType)
        }

    private fun createNewCache(): PluginsCache? {
        if (!project.isTrusted()) return null
        val pluginsClassLoader: UrlClassLoader = UrlClassLoader.build().apply {
            parent(KtSourceModule::class.java.classLoader)
            val pluginsClasspath = ModuleManager.getInstance(project).modules
                .flatMap { it.getCompilerArguments().getPluginClassPaths() }
                .distinct()
            files(pluginsClasspath)
        }.get()
        return PluginsCache(
            pluginsClassLoader,
            ContainerUtil.createConcurrentWeakMap<KtSourceModule, Optional<CompilerPluginRegistrar.ExtensionStorage>>()
        )
    }

    private class PluginsCache(
        val pluginsClassLoader: UrlClassLoader,
        val registrarForModule: ConcurrentMap<KtSourceModule, Optional<CompilerPluginRegistrar.ExtensionStorage>>
    )

    override fun <T : Any> getRegisteredExtensions(module: KtSourceModule, extensionType: ProjectExtensionDescriptor<T>): List<T> {
        val registrarForModule = pluginsCache?.registrarForModule ?: return emptyList()
        val extensionStorage = registrarForModule.computeIfAbsent(module) {
            Optional.ofNullable(computeExtensionStorage(module))
        }.orNull() ?: return emptyList()
        val registrars = extensionStorage.registeredExtensions[extensionType] ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return registrars as List<T>
    }

    @OptIn(Frontend10ApiUsage::class)
    private fun computeExtensionStorage(module: KtSourceModule): CompilerPluginRegistrar.ExtensionStorage? {
        val classLoader = pluginsCache?.pluginsClassLoader ?: return null
        val compilerArguments = module.ideaModule.getCompilerArguments()
        val classPaths = compilerArguments.getPluginClassPaths().map { it.toFile() }.takeIf { it.isNotEmpty() } ?: return null

        val logger = logger<KtCompilerPluginsProviderIdeImpl>()

        val pluginRegistrars =
            logger.runAndLogException { ServiceLoaderLite.loadImplementations<CompilerPluginRegistrar>(classPaths, classLoader) }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val commandLineProcessors = logger.runAndLogException {
            ServiceLoaderLite.loadImplementations<CommandLineProcessor>(classPaths, classLoader)
        } ?: return null

        val compilerConfiguration = CompilerConfiguration().apply {
            // Temporary work-around for KTIJ-24320. Calls to 'setupCommonArguments()' and 'setupJvmSpecificArguments()'
            // (or even a platform-agnostic alternative) should be added.
            if (compilerArguments is K2JVMCompilerArguments) {
                val compilerExtension = CompilerModuleExtension.getInstance(module.ideaModule)
                val outputPath = when (module.moduleInfo) {
                    is ModuleTestSourceInfo -> compilerExtension?.compilerOutputPathForTests
                    else -> compilerExtension?.compilerOutputPath
                }

                putIfNotNull(JVMConfigurationKeys.JVM_TARGET, compilerArguments.jvmTarget?.let(JvmTarget::fromString))
                putIfNotNull(JVMConfigurationKeys.OUTPUT_DIRECTORY, outputPath?.toNioPath()?.toFile())
                put(JVMConfigurationKeys.IR, true) // FIR cannot work with the old backend
            }

            processCompilerPluginsOptions(this, compilerArguments.pluginOptions?.toList(), commandLineProcessors)
        }

        val storage = CompilerPluginRegistrar.ExtensionStorage()
        for (pluginRegistrar in pluginRegistrars) {
            with(pluginRegistrar) {
                storage.registerExtensions(compilerConfiguration)
            }
        }
        return storage
    }

    private fun CommonCompilerArguments.getPluginClassPaths(): List<Path> {
        return this
            .pluginClasspaths
            ?.map { Path.of(it).toAbsolutePath() }
            ?.toList()
            .orEmpty()
    }

    private fun Module.getCompilerArguments(): CommonCompilerArguments {
        return KotlinFacet.get(this)?.configuration?.settings?.mergedCompilerArguments
            ?: KotlinCommonCompilerArgumentsHolder.getInstance(project).settings
    }

    override fun dispose() {
        pluginsCacheCachedValue.drop()
    }

    companion object {
        fun getInstance(project: Project): KtCompilerPluginsProviderIdeImpl {
            return project.getService(KtCompilerPluginsProvider::class.java) as KtCompilerPluginsProviderIdeImpl
        }
    }
}
