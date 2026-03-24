// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.orNull
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.areCompilerPluginsSupported
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.plugins.processCompilerPluginsOptions
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.extensions.ExtensionPointDescriptor
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.openapiModule
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayoutService
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.fir.extensions.KtCompilerPluginsCache.Companion.substitutePluginJar
import org.jetbrains.kotlin.idea.util.getOriginalOrDelegateFileOrSelf
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.makeScriptCompilerArguments
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.util.ServiceLoaderLite
import java.io.File
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentMap
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.compilerOptions

@OptIn(ExperimentalCompilerApi::class)
@ApiStatus.Internal
class KtCompilerPluginsCache private constructor(
    private val project: Project,
    private val pluginsClassLoader: UrlClassLoader,
    private val registrarForSourceModule: ConcurrentMap<KaSourceModule, Optional<CompilerPluginRegistrar.ExtensionStorage>>,
    /**
     * As scripts might be associated with injections,
     * it's better to have a more stable anchor such as a top-level file.
     */
    private val registrarForScriptModule: SynchronizedClearableLazy<ConcurrentMap<VirtualFile, Optional<CompilerPluginRegistrar.ExtensionStorage>>>,
    private val onlyBundledPluginsEnabled: Boolean
) {

    fun isPluginOfTypeRegistered(
        module: KaModule,
        pluginType: KotlinCompilerPluginsProvider.CompilerPluginType,
    ): Boolean {
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        val extension = when (pluginType) {
            KotlinCompilerPluginsProvider.CompilerPluginType.ASSIGNMENT -> FirAssignExpressionAltererExtension::class
            else -> return false
        }
        return getRegisteredExtensions(
            module = module,
            extensionType = FirExtensionRegistrarAdapter.Companion
        )
            .map { (it as FirExtensionRegistrar).configure() }
            .any { it.extensions[extension]?.isNotEmpty() == true }
    }

    @OptIn(KaExperimentalApi::class)
    fun <T : Any> getRegisteredExtensions(
        module: KaModule,
        extensionType: ExtensionPointDescriptor<T>
    ): List<T> {
        if (!module.areCompilerPluginsSupported()) return emptyList()
        val classLoader = pluginsClassLoader
        return when (module) {
            is KaSourceModule -> {
                val registrarForModule = registrarForSourceModule
                module.getExtensionsForModule(classLoader, registrarForModule, module, extensionType)
            }

            is KaScriptModule -> {
                val registrarForModule = registrarForScriptModule.value
                val cacheKey = module.file.virtualFile.getOriginalOrDelegateFileOrSelf()

                module.getExtensionsForModule(classLoader, registrarForModule, cacheKey, extensionType)
            }

            else -> emptyList()
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun <T : Any, K : Any> KaModule.getExtensionsForModule(
        classLoader: ClassLoader,
        registrarForModule: ConcurrentMap<K, Optional<CompilerPluginRegistrar.ExtensionStorage>>,
        cacheKey: K,
        extensionType: ExtensionPointDescriptor<T>
    ): List<T> {
        val extensionStorage = registrarForModule.computeIfAbsent(cacheKey) {
            Optional.ofNullable(computeExtensionStorage(classLoader, this))
        }.orNull() ?: return emptyList()
        val registrars = extensionStorage.registeredExtensions[extensionType] ?: return emptyList()
        @Suppress("UNCHECKED_CAST") return registrars as List<T>
    }

    @OptIn(KaExperimentalApi::class, LLFirInternals::class)
    private fun computeExtensionStorage(
        classLoader: ClassLoader,
        module: KaModule
    ): CompilerPluginRegistrar.ExtensionStorage? {
        val compilerArguments = when (module) {
            is KaSourceModule -> module.openapiModule.getCompilerArguments()
            is KaScriptModule -> {
                val scriptDefinition = module.file.findScriptDefinition() ?: return null
                val scriptConfiguration = scriptDefinition.compilationConfiguration

                val providedOptions = scriptConfiguration[ScriptCompilationConfiguration.compilerOptions] ?: return null
                makeScriptCompilerArguments(providedOptions)
            }

            else -> {
                return null
            }
        }
        val pluginClasspaths = collectSubstitutedPluginClasspaths(project, onlyBundledPluginsEnabled, listOf(compilerArguments)).map { it.toFile() }
        if (pluginClasspaths.isEmpty()) return null

        val logger = logger<KtCompilerPluginsProviderIdeImpl>()

        ProgressManager.checkCanceled()

        val pluginRegistrars =
            logger.runAndLogException { ServiceLoaderLite.loadImplementations<CompilerPluginRegistrar>(pluginClasspaths, classLoader) }
                ?.takeIf { it.isNotEmpty() } ?: return null

        ProgressManager.checkCanceled()

        val commandLineProcessors = logger.runAndLogException {
            ServiceLoaderLite.loadImplementations<CommandLineProcessor>(pluginClasspaths, classLoader)
        } ?: return null

        ProgressManager.checkCanceled()

        val compilerConfiguration =
            CompilerConfiguration.create().apply {
                @OptIn(ExperimentalCompilerApi::class)
                // Temporary work-around for KTIJ-24320. Calls to 'setupCommonArguments()' and 'setupJvmSpecificArguments()'
                // (or even a platform-agnostic alternative) should be added.
                if (compilerArguments is K2JVMCompilerArguments && module is KaSourceModule) {
                    val compilerExtension = CompilerModuleExtension.getInstance(module.openapiModule)
                    val outputUrl = when (module.sourceModuleKind) {
                        KaSourceModuleKind.TEST -> compilerExtension?.compilerOutputUrlForTests
                        KaSourceModuleKind.PRODUCTION -> compilerExtension?.compilerOutputUrl
                    }

                    putIfNotNull(JVMConfigurationKeys.JVM_TARGET, compilerArguments.jvmTarget?.let(JvmTarget::fromString))
                    putIfNotNull(JVMConfigurationKeys.OUTPUT_DIRECTORY, outputUrl?.let { File(it) })
                }

                processCompilerPluginsOptions(this, compilerArguments.pluginOptions?.toList(), commandLineProcessors)
            }

        val storage = CompilerPluginRegistrar.ExtensionStorage()
        for (pluginRegistrar in pluginRegistrars) {
            ProgressManager.checkCanceled()

            with(pluginRegistrar) {
                try {
                    val configuration = KotlinFirCompilerPluginConfigurationForIdeProvider.getCompilerConfigurationWithCustomOptions(
                        pluginRegistrar, compilerConfiguration
                    ) ?: compilerConfiguration
                    storage.registerExtensions(configuration)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Throwable) {
                    LOG.error(e)
                }
            }
        }
        return storage
    }

    /**
     * Throws away only part of the cache related to the scripts, leaving other storage intact.
     */
    fun resetScriptCache() {
        val scriptsCache = this.registrarForScriptModule.drop() ?: return

        scriptsCache.values.mapNotNull { it.orNull() }.disposeAll()
    }

    fun dispose() {
        registrarForSourceModule.values.mapNotNull { it.orNull() }.disposeAll()
        resetScriptCache()
    }

    companion object {

        private val LOG = logger<KtCompilerPluginsCache>()

        fun new(project: Project, onlyBundledPluginsEnabled: Boolean): KtCompilerPluginsCache {
            val pluginsClassLoader: UrlClassLoader = UrlClassLoader.build().apply {
                parent(KaModule::class.java.classLoader)
                val compilerArguments = ModuleManager.getInstance(project).modules.map { it.getCompilerArguments() }
                val pluginClasspaths = collectSubstitutedPluginClasspaths(project, onlyBundledPluginsEnabled, compilerArguments)
                files(pluginClasspaths)
            }.get()
            return KtCompilerPluginsCache(
                pluginsClassLoader = pluginsClassLoader,
                registrarForSourceModule = ContainerUtil.createConcurrentWeakMap(),
                registrarForScriptModule = SynchronizedClearableLazy { ContainerUtil.createConcurrentWeakMap() },
                onlyBundledPluginsEnabled = onlyBundledPluginsEnabled,
                project = project
            )
        }

        private fun Collection<CompilerPluginRegistrar.ExtensionStorage>.disposeAll() {
            for (storage in this) {
                for (disposable in storage.disposables) {
                    LOG.runAndLogException {
                        disposable.dispose()
                    }
                }
            }
        }

        /**
         * Returns the paths defined in [org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments.pluginClasspaths]
         * in the absolute form with the expansion of the present path macros
         * (like 'KOTLIN_BUNDLED').
         */
        private fun CommonCompilerArguments.getOriginalPluginClasspaths(project: Project): List<Path> {
            val pluginClassPaths = this.pluginClasspaths

            if (pluginClassPaths.isNullOrEmpty()) return emptyList()

            val layoutService = KotlinPluginLayoutService.getInstance(project)

            val pathMacroManager = PathMacroManager.getInstance(project)
            val expandedPluginClassPaths = pluginClassPaths.map { pathMacroManager.expandPath(it) }

            return expandedPluginClassPaths.mapNotNull {
                runCatching {
                    layoutService.resolveRelativeToRemoteKotlinc(Path.of(it))
                }.getOrLogException(LOG)
            }
        }

        /**
         * We have the following logic for plugins' substitution:
         * 1. Always replace our own plugins (like "allopen", "noarg", etc.) with bundled ones to avoid binary incompatibility.
         * 2. Allow using other compiler plugins only if [onlyBundledPluginsEnabled] is set to false; otherwise, filter them.
         */
        private fun substitutePluginJar(project: Project, onlyBundledPluginsEnabled: Boolean, userSuppliedPluginJar: Path): Path? {
            ProgressManager.checkCanceled()

            val bundledPlugin = KotlinBundledFirCompilerPluginProvider.provideBundledPluginJar(project, userSuppliedPluginJar)
            if (bundledPlugin != null) return bundledPlugin

            return userSuppliedPluginJar.takeUnless { onlyBundledPluginsEnabled }
        }

        /**
         * Collects the substituted plugin classpaths for the given [compilerArguments] list.
         *
         * We process the whole [compilerArguments] list at once, because it gives us opportunity
         * to not call [substitutePluginJar] multiple times if [compilerArguments] use the same
         * compiler plugins jars.
         */
        private fun collectSubstitutedPluginClasspaths(
            project: Project,
            onlyBundledPluginsEnabled: Boolean,
            compilerArguments: List<CommonCompilerArguments>
        ): List<Path> {
            val combinedOriginalClasspaths = compilerArguments.asSequence().flatMap { it.getOriginalPluginClasspaths(project) }.distinct()

            val substitutedClasspaths = combinedOriginalClasspaths.mapNotNull { userSuppliedPluginJar ->
                substitutePluginJar(project, onlyBundledPluginsEnabled, userSuppliedPluginJar)
            }.distinct()

            return substitutedClasspaths.toList()
        }

        @Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
        private fun Module.getCompilerArguments(): CommonCompilerArguments {
            return KotlinFacet.get(this)?.configuration?.settings?.mergedCompilerArguments
                ?: KotlinCommonCompilerArgumentsHolder.getInstance(project).settings
        }
    }
}
