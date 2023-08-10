// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LanguageVersionSettingsProviderUtils")
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analyzer.LanguageSettingsProvider
import org.jetbrains.kotlin.cli.common.arguments.JavaTypeEnhancementStateParser
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.useLibraryToSourceAnalysis
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsTracker
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.util.merge
import java.util.*
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.facet.KotlinFacetModificationTracker

private typealias LanguageFeatureMap = Map<LanguageFeature, LanguageFeature.State>
private typealias AnalysisFlagMap = Map<AnalysisFlag<*>, Any>

private val LANGUAGE_VERSION_SETTINGS = Key.create<LanguageVersionSettings>("LANGUAGE_VERSION_SETTINGS")

@TestOnly
@ApiStatus.Internal
fun <T> Module.withLanguageVersionSettings(value: LanguageVersionSettings, body: () -> T): T = try {
    putUserData(LANGUAGE_VERSION_SETTINGS, value)
    body()
} finally {
    putUserData(LANGUAGE_VERSION_SETTINGS, null)
}

val Module.languageVersionSettings: LanguageVersionSettings
    get() = LanguageVersionSettingsProvider.getInstance(project).get(this)

val Project.languageVersionSettings: LanguageVersionSettings
    get() = LanguageVersionSettingsProvider.getInstance(this).commonSettings

val PsiElement.languageVersionSettings: LanguageVersionSettings
    get() {
        if (project.serviceOrNull<ProjectFileIndex>() == null) {
            return LanguageVersionSettingsImpl.DEFAULT
        }

        return runReadAction {
            project.service<LanguageSettingsProvider>().getLanguageVersionSettings(this.moduleInfo, project)
        }
    }

@Service(Service.Level.PROJECT)
class LanguageVersionSettingsProvider(private val project: Project) : Disposable {
    init {
        project.messageBus.connect(this)
            .subscribe(KotlinCompilerSettingsListener.TOPIC, object : KotlinCompilerSettingsListener {
                override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
                    _commonSettings = null
                    _librarySettings = null
                }
            })
    }

    companion object {
        fun getInstance(project: Project): LanguageVersionSettingsProvider = project.service()
    }

    @Volatile
    private var _commonSettings: LanguageVersionSettings? = null

    @Volatile
    private var _librarySettings: LanguageVersionSettings? = null

    val commonSettings: LanguageVersionSettings
        get() {
            _commonSettings?.let { return it }
            return computeProjectLanguageVersionSettings(useCommonFacetSettings = false).also { _commonSettings = it }
        }

    val librarySettings: LanguageVersionSettings
        get() {
            _librarySettings?.let { return it }
            return computeProjectLanguageVersionSettings(useCommonFacetSettings = true).also { _librarySettings = it }
        }

    fun get(module: Module): LanguageVersionSettings {
        return getSelf(module) ?: commonSettings
    }

    private fun getSelf(module: Module): LanguageVersionSettings? {
        return module.getUserData(LANGUAGE_VERSION_SETTINGS) ?: CachedValuesManager.getManager(project).getCachedValue(module) {
            CachedValueProvider.Result.create(
                computeModuleLanguageVersionSettings(module),
                KotlinCompilerSettingsTracker.getInstance(project),
                ProjectRootModificationTracker.getInstance(project),
                KotlinFacetModificationTracker.getInstance(project),
            )
        }
    }

    private fun computeProjectLanguageVersionSettings(useCommonFacetSettings: Boolean): LanguageVersionSettings {
        val arguments = KotlinCommonCompilerArgumentsHolder.getInstance(project).settings

        val languageVersion = LanguageVersion.fromVersionString(arguments.languageVersion)
            ?: KotlinPluginLayout.standaloneCompilerVersion.languageVersion

        val languageVersionForApiVersion = LanguageVersion.fromVersionString(arguments.apiVersion) ?: languageVersion
        val apiVersion = ApiVersion.createByLanguageVersion(languageVersionForApiVersion)

        val additionalSettings = KotlinCompilerSettings.getInstance(project).settings
        val additionalArguments = JvmPlatforms.defaultJvmPlatform
            .createArguments { parseCommandLineArguments(additionalSettings.additionalArgumentsAsList, this) }

        val commonFacetSettings = if (useCommonFacetSettings) collectCommonFacetSettings() else null

        val analysisFlags = merge(
            arguments.configureAnalysisFlags(MessageCollector.NONE, languageVersion),
            additionalArguments.configureAnalysisFlags(MessageCollector.NONE, languageVersion),
            commonFacetSettings?.analysisFlags,
            getIdeSpecificAnalysisFlags()
        )

        val languageFeatures = merge(
            arguments.configureLanguageFeatures(MessageCollector.NONE),
            additionalArguments.configureLanguageFeatures(MessageCollector.NONE),
            commonFacetSettings?.languageFeatures
        )

        return LanguageVersionSettingsImpl(languageVersion, apiVersion, analysisFlags, languageFeatures)
    }

    private fun computeModuleLanguageVersionSettings(module: Module): LanguageVersionSettings? {
        val facetSettingsProvider = KotlinFacetSettingsProvider.getInstance(project) ?: return null
        if (facetSettingsProvider.getSettings(module) == null) {
            return null
        }

        val facetSettings = facetSettingsProvider.getInitializedSettings(module)
        if (facetSettings.useProjectSettings) {
            return null
        }

        val (languageVersion, apiVersion) = getLanguageApiVersionFromFacet(facetSettings)

        val arguments = facetSettings.mergedCompilerArguments

        val analysisFlags = merge(
           /*
            Set default for 'useIR':
            For common source sets, the common compiler arguments will not configure the 'useIR' flag
            However, there is at least one FE checker 'SuspendInFunInterfaceChecker' which uses this flag.

            Since IR is default and 'not-IR' is not supported anymore, it is 'safe' to set this flag to 'true' by default
            in the IDE for common source sets

            Note, 'arguments?.configureAnalysisFlags' will potentially overwrite the flag (see K2JvmCompilerArguments)
            So for leaf SourceSets or compilations this flag will be configured by taking the actual compiler arguments
            into account.
            */
            mapOf(JvmAnalysisFlags.useIR to true),
            arguments?.configureAnalysisFlags(MessageCollector.NONE, languageVersion),
            getIdeSpecificAnalysisFlags(),
        )

        val languageFeatures = merge(
            arguments?.configureLanguageFeatures(MessageCollector.NONE),
            getMultiPlatformLanguageFeatures(module, facetSettings)
        )

        return LanguageVersionSettingsImpl(languageVersion, apiVersion, analysisFlags, languageFeatures)
    }

    private fun getLanguageApiVersionFromFacet(facetSettings: KotlinFacetSettings): Pair<LanguageVersion, ApiVersion> {
        val languageVersion = facetSettings.languageLevel
        val apiVersion = facetSettings.apiLevel?.let { ApiVersion.createByLanguageVersion(it) }

        if (languageVersion != null && apiVersion != null) {
            return Pair(languageVersion, apiVersion)
        }

        val commonSettings = this.commonSettings
        return Pair(commonSettings.languageVersion, commonSettings.apiVersion)
    }

    private class CommonFacetSettings(val analysisFlags: AnalysisFlagMap, val languageFeatures: LanguageFeatureMap)

    /*
        Due to performance considerations, libraries used in several modules are analyzed once.
        However, analysis result might slightly change depending on compiler arguments of a module from which the library is accessed.
        Unfortunately, we can't either precisely adapt resolution result so each module sees the right declarations.

        Instead, we rely on the fact that in real-world projects compiler is usually configured consistently, so we pick settings from
        some module we liked and use it globally.
        The implementation is far from being ideal, yet it doesn't invalidate the core idea.
     */
    private fun collectCommonFacetSettings(): CommonFacetSettings {
        val analysisFlags = mutableMapOf<AnalysisFlag<*>, Any>()
        val languageFeatures = mutableMapOf<LanguageFeature, LanguageFeature.State>()

        for (module in ModuleManager.getInstance(project).modules) {
            val settings = KotlinFacetSettingsProvider.getInstance(project)?.getSettings(module) ?: continue
            val arguments = settings.mergedCompilerArguments as? K2JVMCompilerArguments ?: continue

            val kotlinVersion = LanguageVersion.fromVersionString(arguments.languageVersion)?.toKotlinVersion()
                ?: settings.languageLevel?.toKotlinVersion()
                ?: KotlinPluginLayout.standaloneCompilerVersion.kotlinVersion

            // TODO definitely wrong implementation, merge state properly
            analysisFlags[JvmAnalysisFlags.javaTypeEnhancementState] =
                JavaTypeEnhancementStateParser(MessageCollector.NONE, kotlinVersion).parse(
                    arguments.jsr305,
                    arguments.supportCompatqualCheckerFrameworkAnnotations,
                    arguments.jspecifyAnnotations,
                    arguments.nullabilityAnnotations
                )

            analysisFlags[AnalysisFlags.skipPrereleaseCheck] = true
            analysisFlags[AnalysisFlags.skipMetadataVersionCheck] = true
            analysisFlags[AnalysisFlags.allowUnstableDependencies] = true

            val supportDefinitelyNotNull = getSelf(module)
                ?.supportsFeature(LanguageFeature.ProhibitUsingNullableTypeParameterAgainstNotNullAnnotated)
                ?: false

            if (supportDefinitelyNotNull) {
                languageFeatures[LanguageFeature.ProhibitUsingNullableTypeParameterAgainstNotNullAnnotated] = LanguageFeature.State.ENABLED
            }
        }

        return CommonFacetSettings(analysisFlags, languageFeatures)
    }

    private fun getMultiPlatformLanguageFeatures(module: Module, facetSettings: KotlinFacetSettings): LanguageFeatureMap {
        if (facetSettings.targetPlatform.isCommon() || ModuleRootManager.getInstance(module).dependencies.any { it.platform.isCommon() }) {
            return Collections.singletonMap(LanguageFeature.MultiPlatformProjects, LanguageFeature.State.ENABLED)
        }

        return emptyMap()
    }

    private fun getIdeSpecificAnalysisFlags(): AnalysisFlagMap {
        return LinkedHashMap<AnalysisFlag<*>, Any>().apply {
            if (project.useLibraryToSourceAnalysis) {
                set(AnalysisFlags.libraryToSourceAnalysis, true)
            }

            set(AnalysisFlags.ideMode, true)
        }
    }

    override fun dispose() {}
}