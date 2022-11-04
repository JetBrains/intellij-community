// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinFacetUtils")
package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.compilerRunner.ArgumentUtils
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.platforms.IdePlatformKindProjectStructure
import org.jetbrains.kotlin.idea.compiler.configuration.*
import org.jetbrains.kotlin.idea.defaultSubstitutors
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import kotlin.reflect.KProperty1

var Module.hasExternalSdkConfiguration: Boolean by NotNullableUserDataProperty(Key.create("HAS_EXTERNAL_SDK_CONFIGURATION"), false)

fun KotlinFacetSettings.initializeIfNeeded(
    module: Module,
    rootModel: ModuleRootModel?,
    platform: TargetPlatform? = null, // if null, detect by module dependencies
    compilerVersion: IdeKotlinVersion? = null
) {
    val project = module.project

    val shouldInferLanguageLevel = languageLevel == null
    val shouldInferAPILevel = apiLevel == null

    if (compilerSettings == null) {
        compilerSettings = KotlinCompilerSettings.getInstance(project).settings
    }

    val commonArguments = KotlinCommonCompilerArgumentsHolder.getInstance(module.project).settings

    if (compilerArguments == null) {
        val targetPlatform = platform ?: getDefaultTargetPlatform(module, rootModel)

        compilerArguments = targetPlatform.createArguments {
            val argumentsForPlatform = IdePlatformKindProjectStructure.getInstance(project)
                .getCompilerArguments(targetPlatform.idePlatformKind)

            if (argumentsForPlatform != null) {
                mergeBeans(argumentsForPlatform, this)
            }

            mergeBeans(commonArguments, this)
        }

        this.targetPlatform = targetPlatform
    }

    if (shouldInferLanguageLevel) {
        languageLevel = (if (useProjectSettings) LanguageVersion.fromVersionString(commonArguments.languageVersion) else null)
            ?: getDefaultLanguageLevel(module, compilerVersion, coerceRuntimeLibraryVersionToReleased = compilerVersion == null)
    }

    if (shouldInferAPILevel) {
        apiLevel = if (useProjectSettings) {
            LanguageVersion.fromVersionString(commonArguments.apiVersion) ?: languageLevel
        } else {
            val maximumValue = getLibraryVersion(
                module,
                rootModel,
                this.targetPlatform?.idePlatformKind,
                coerceRuntimeLibraryVersionToReleased = compilerVersion == null
            )
            languageLevel?.coerceAtMostVersion(maximumValue)
        }
    }
}

private fun getDefaultTargetPlatform(module: Module, rootModel: ModuleRootModel?): TargetPlatform {
    val platformKind = IdePlatformKind.ALL_KINDS.firstOrNull {
        getRuntimeLibraryVersions(module, rootModel, it).isNotEmpty()
    } ?: JvmPlatforms.defaultJvmPlatform.idePlatformKind
    if (platformKind == JvmIdePlatformKind) {
        var jvmTarget = Kotlin2JvmCompilerArgumentsHolder.getInstance(module.project).settings.jvmTarget?.let { JvmTarget.fromString(it) }
        if (jvmTarget == null) {
            val sdk = ((rootModel ?: ModuleRootManager.getInstance(module))).sdk
            val sdkVersion = (sdk?.sdkType as? JavaSdk)?.getVersion(sdk)
            if (sdkVersion == null || sdkVersion >= JavaSdkVersion.JDK_1_8) {
                jvmTarget = JvmTarget.JVM_1_8
            }
        }
        return if (jvmTarget != null) JvmPlatforms.jvmPlatformByTargetVersion(jvmTarget) else JvmPlatforms.defaultJvmPlatform
    }
    return platformKind.defaultPlatform
}

private fun LanguageVersion.coerceAtMostVersion(version: IdeKotlinVersion): LanguageVersion {
    fun isUpToNextMinor(major: Int, minor: Int, patch: Int): Boolean {
        return version.kotlinVersion.isAtLeast(major, minor, patch) && !version.kotlinVersion.isAtLeast(major, minor + 1)
    }

    // 1.4.30+ and 1.5.30+ have full support of next language version
    val languageVersion = when {
        isUpToNextMinor(1, 4, 30) -> LanguageVersion.KOTLIN_1_5
        isUpToNextMinor(1, 5, 30) -> LanguageVersion.KOTLIN_1_6
        else -> version.languageVersion
    }

    return this.coerceAtMost(languageVersion)
}

fun parseCompilerArgumentsToFacet(
    arguments: List<String>,
    defaultArguments: List<String>,
    kotlinFacet: KotlinFacet,
    modelsProvider: IdeModifiableModelsProvider?
) {
    val compilerArgumentsClass = kotlinFacet.configuration.settings.compilerArguments?.javaClass ?: return
    val currentArgumentsBean = compilerArgumentsClass.newInstance()
    val defaultArgumentsBean = compilerArgumentsClass.newInstance()
    val defaultArgumentWithDefaults = substituteDefaults(defaultArguments, defaultArgumentsBean)
    val currentArgumentWithDefaults = substituteDefaults(arguments, currentArgumentsBean)
    parseCommandLineArguments(defaultArgumentWithDefaults, defaultArgumentsBean)
    parseCommandLineArguments(currentArgumentWithDefaults, currentArgumentsBean)
    applyCompilerArgumentsToFacet(currentArgumentsBean, defaultArgumentsBean, kotlinFacet, modelsProvider)
}

fun applyCompilerArgumentsToFacet(
    arguments: CommonCompilerArguments,
    defaultArguments: CommonCompilerArguments?,
    kotlinFacet: KotlinFacet,
    modelsProvider: IdeModifiableModelsProvider?
) {
    with(kotlinFacet.configuration.settings) {
        val compilerArguments = this.compilerArguments ?: return

        val defaultCompilerArguments = defaultArguments?.let { copyBean(it) } ?: compilerArguments::class.java.newInstance()
        defaultCompilerArguments.convertPathsToSystemIndependent()

        val oldPluginOptions = compilerArguments.pluginOptions

        val emptyArgs = compilerArguments::class.java.newInstance()

        // Ad-hoc work-around for android compilations: middle source sets could be actualized up to
        // Android target, meanwhile compiler arguments are of type K2Metadata
        // TODO(auskov): merge classpath once compiler arguments are removed from KotlinFacetSettings
        if (arguments.javaClass == compilerArguments.javaClass) {
            copyBeanTo(arguments, compilerArguments) { property, value -> value != property.get(emptyArgs) }
        }
        compilerArguments.pluginOptions = joinPluginOptions(oldPluginOptions, arguments.pluginOptions)

        compilerArguments.convertPathsToSystemIndependent()

        // Retain only fields exposed (and not explicitly ignored) in facet configuration editor.
        // The rest is combined into string and stored in CompilerSettings.additionalArguments

        if (modelsProvider != null)
            kotlinFacet.module.configureSdkIfPossible(compilerArguments, modelsProvider)

        val primaryFields = compilerArguments.primaryFields
        val ignoredFields = compilerArguments.ignoredFields

        fun exposeAsAdditionalArgument(property: KProperty1<CommonCompilerArguments, Any?>) =
            property.name !in primaryFields && property.get(compilerArguments) != property.get(defaultCompilerArguments)

        val additionalArgumentsString = with(compilerArguments::class.java.newInstance()) {
            copyFieldsSatisfying(compilerArguments, this) { exposeAsAdditionalArgument(it) && it.name !in ignoredFields }
            ArgumentUtils.convertArgumentsToStringListNoDefaults(this).joinToString(separator = " ") {
                if (StringUtil.containsWhitespaces(it) || it.startsWith('"')) {
                    StringUtil.wrapWithDoubleQuote(StringUtil.escapeQuotes(it))
                } else it
            }
        }
        compilerSettings?.additionalArguments =
            if (additionalArgumentsString.isNotEmpty()) additionalArgumentsString else CompilerSettings.DEFAULT_ADDITIONAL_ARGUMENTS

        with(compilerArguments::class.java.newInstance()) {
            copyFieldsSatisfying(this, compilerArguments) { exposeAsAdditionalArgument(it) || it.name in ignoredFields }
        }

        val languageLevel = languageLevel
        val apiLevel = apiLevel
        if (languageLevel != null && apiLevel != null && apiLevel > languageLevel) {
            this.apiLevel = languageLevel
        }

        updateMergedArguments()
    }
}

private fun Module.configureSdkIfPossible(compilerArguments: CommonCompilerArguments, modelsProvider: IdeModifiableModelsProvider) {
    // SDK for Android module is already configured by Android plugin at this point
    if (isAndroidModule(modelsProvider) || hasNonOverriddenExternalSdkConfiguration(compilerArguments)) return

    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    KotlinSdkType.setUpIfNeeded()
    val allSdks = runReadAction { ProjectJdkTable.getInstance() }.allJdks
    val sdk = if (compilerArguments is K2JVMCompilerArguments) {
        val jdkHome = compilerArguments.jdkHome
        when {
            jdkHome != null -> allSdks.firstOrNull { it.sdkType is JavaSdk && FileUtil.comparePaths(it.homePath, jdkHome) == 0 }
            projectSdk != null && projectSdk.sdkType is JavaSdk -> projectSdk
            else -> allSdks.firstOrNull { it.sdkType is JavaSdk }
        }
    } else {
        allSdks.firstOrNull { it.sdkType is KotlinSdkType }
            ?: modelsProvider
                .modifiableModuleModel
                .modules
                .asSequence()
                .mapNotNull { modelsProvider.getModifiableRootModel(it).sdk }
                .firstOrNull { it.sdkType is KotlinSdkType }
    }

    val rootModel = modelsProvider.getModifiableRootModel(this)
    if (sdk == null || sdk == projectSdk) {
        rootModel.inheritSdk()
    } else {
        rootModel.sdk = sdk
    }
}

private fun Module.hasNonOverriddenExternalSdkConfiguration(compilerArguments: CommonCompilerArguments): Boolean =
    hasExternalSdkConfiguration && (compilerArguments !is K2JVMCompilerArguments || compilerArguments.jdkHome == null)

private fun substituteDefaults(args: List<String>, compilerArguments: CommonCompilerArguments): List<String> {
    val substitutedCompilerArguments = defaultSubstitutors[compilerArguments::class]
        ?.filter { it.isSubstitutable(args) }
        ?.flatMap { it.oldSubstitution }
    return args + substitutedCompilerArguments.orEmpty()
}

private fun joinPluginOptions(old: Array<String>?, new: Array<String>?): Array<String>? {
    if (old == null && new == null) {
        return old
    } else if (new == null) {
        return old
    } else if (old == null) {
        return new
    }

    return (old + new).distinct().toTypedArray()
}