// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinFacetUtils")

package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.application.runReadAction
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
import org.jetbrains.kotlin.compilerRunner.toArgumentStrings
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.platforms.IdePlatformKindProjectStructure
import org.jetbrains.kotlin.idea.compiler.configuration.*
import org.jetbrains.kotlin.idea.defaultSubstitutors
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import kotlin.reflect.KProperty1

var Module.hasExternalSdkConfiguration: Boolean by NotNullableUserDataProperty(Key.create("HAS_EXTERNAL_SDK_CONFIGURATION"), false)

fun IKotlinFacetSettings.initializeIfNeeded(
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

        val argumentsForPlatform = IdePlatformKindProjectStructure.getInstance(project)
            .getCompilerArguments(targetPlatform.idePlatformKind)

        compilerArguments = targetPlatform.createArguments {
            if (argumentsForPlatform != null) {
                when {
                    argumentsForPlatform is K2JVMCompilerArguments &&
                            this is K2JVMCompilerArguments -> copyK2JVMCompilerArguments(argumentsForPlatform, this)

                    argumentsForPlatform is K2JSCompilerArguments &&
                            this is K2JSCompilerArguments -> copyK2JSCompilerArguments(argumentsForPlatform, this)

                    else -> error("Unsupported copy arguments combination: ${argumentsForPlatform.javaClass.name} and ${javaClass.name}")
                }
            }

            copyCommonCompilerArguments(commonArguments, this)
        }

        this.targetPlatform = targetPlatform
    }

    if (shouldInferLanguageLevel) {
        languageLevel = (if (useProjectSettings) LanguageVersion.fromVersionString(commonArguments.languageVersion) else null)
            ?: getDefaultLanguageLevel(compilerVersion, coerceRuntimeLibraryVersionToReleased = compilerVersion == null)
    }

    if (shouldInferAPILevel) {
        apiLevel = when {
            useProjectSettings -> LanguageVersion.fromVersionString(commonArguments.apiVersion) ?: languageLevel
            else -> languageLevel?.coerceAtMostVersion(getMaximumValueForApiLevel(module, rootModel, compilerVersion))
        }
    }
}

private fun IKotlinFacetSettings.getMaximumValueForApiLevel(
    module: Module,
    rootModel: ModuleRootModel?,
    compilerVersion: IdeKotlinVersion?
) = compilerVersion ?: getKotlinStdlibVersionOrNull(
    module,
    rootModel,
    targetPlatform?.idePlatformKind,
    coerceRuntimeLibraryVersionToReleased = true
) ?: getDefaultVersion(explicitVersion = null, coerceRuntimeLibraryVersionToReleased = true)

private fun getDefaultTargetPlatform(module: Module, rootModel: ModuleRootModel?): TargetPlatform {
    val platformKind = IdePlatformKind.ALL_KINDS.firstOrNull {
        getRuntimeLibraryVersions(module, rootModel, it).any()
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

fun LanguageVersion.coerceAtMostVersion(version: IdeKotlinVersion): LanguageVersion {
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

fun parseCompilerArgumentsToFacetSettings(
    arguments: List<String>,
    kotlinFacetSettings: IKotlinFacetSettings,
    modelsProvider: IdeModifiableModelsProvider?
) {
    val compilerArgumentsClass = kotlinFacetSettings.compilerArguments?.javaClass ?: return
    val currentArgumentsBean = compilerArgumentsClass.getDeclaredConstructor().newInstance()
    val currentArgumentWithDefaults = substituteDefaults(arguments, currentArgumentsBean)
    parseCommandLineArguments(currentArgumentWithDefaults, currentArgumentsBean)
    applyCompilerArgumentsToFacetSettings(currentArgumentsBean, kotlinFacetSettings, null, modelsProvider)
}

fun parseCompilerArgumentsToFacet(
    arguments: List<String>,
    kotlinFacet: KotlinFacet,
    modelsProvider: IdeModifiableModelsProvider?,
) {
    val compilerArgumentsClass = kotlinFacet.configuration.settings.compilerArguments?.javaClass ?: return
    val currentArgumentsBean = compilerArgumentsClass.getDeclaredConstructor().newInstance()
    val currentArgumentWithDefaults = substituteDefaults(arguments, currentArgumentsBean)
    parseCommandLineArguments(currentArgumentWithDefaults, currentArgumentsBean)
    applyCompilerArgumentsToFacetSettings(currentArgumentsBean, kotlinFacet.configuration.settings, kotlinFacet.module, modelsProvider)
}

fun applyCompilerArgumentsToFacetSettings(
    arguments: CommonCompilerArguments,
    kotlinFacetSettings: IKotlinFacetSettings,
    module: Module?,
    modelsProvider: IdeModifiableModelsProvider?
) {
    with(kotlinFacetSettings) {
        updateCompilerArguments {


            val oldPluginOptions = this.pluginOptions
            val emptyArgs = this::class.java.getDeclaredConstructor().newInstance()

            // Ad-hoc work-around for android compilations: middle source sets could be actualized up to
            // Android target, meanwhile compiler arguments are of type K2Metadata
            // TODO(auskov): merge classpath once compiler arguments are removed from KotlinFacetSettings
            if (arguments.javaClass == this.javaClass) {
                copyBeanTo(arguments, this) { property, value -> value != property.get(emptyArgs) }
            }
            this.pluginOptions = joinPluginOptions(oldPluginOptions, arguments.pluginOptions)

            this.convertPathsToSystemIndependent()

            // Retain only fields exposed (and not explicitly ignored) in facet configuration editor.
            // The rest is combined into string and stored in CompilerSettings.additionalArguments

            if (modelsProvider != null && module != null)
                module.configureSdkIfPossible(this, modelsProvider)

            val allFacetFields = this.kotlinFacetFields.allFields

            val ignoredFields = hashSetOf(
                K2JVMCompilerArguments::noJdk.name,
                K2JVMCompilerArguments::jdkHome.name,
            )

            val ignoredAsAdditionalArguments = ignoredFields + hashSetOf(
                CommonCompilerArguments::fragments.name,
                CommonCompilerArguments::fragmentRefines.name,
                CommonCompilerArguments::fragmentSources.name,

                K2JVMCompilerArguments::moduleName.name,
                K2JVMCompilerArguments::noReflect.name,
                K2JVMCompilerArguments::noStdlib.name,
                K2JVMCompilerArguments::allowNoSourceFiles.name,
                K2JVMCompilerArguments::jvmDefault.name,
                K2JVMCompilerArguments::reportPerf.name,

                K2NativeCompilerArguments::enableAssertions.name,
                K2NativeCompilerArguments::debug.name,
                K2NativeCompilerArguments::outputName.name,
                K2NativeCompilerArguments::linkerArguments.name,
                K2NativeCompilerArguments::singleLinkerArguments.name,
                K2NativeCompilerArguments::produce.name,
                K2NativeCompilerArguments::target.name,
                K2NativeCompilerArguments::shortModuleName.name,
                K2NativeCompilerArguments::noendorsedlibs.name,

                K2JSCompilerArguments::outputFile.name,

                K2JSCompilerArguments::outputDir.name,
                K2JSCompilerArguments::moduleName.name,
        )

            fun exposeAsAdditionalArgument(property: KProperty1<CommonCompilerArguments, Any?>) =
                /* Handled by facet directly */
                property.name !in allFacetFields &&
                        /* Explicitly  not shown to users as 'additional arguments' */
                        property.name !in ignoredAsAdditionalArguments &&
                        /* Default value from compiler arguments is used */
                        property.get(this) != property.get(emptyArgs)


            val additionalArgumentsString = with(this::class.java.getDeclaredConstructor().newInstance()) {
                copyFieldsSatisfying(this@updateCompilerArguments, this) { exposeAsAdditionalArgument(it) }
                toArgumentStrings().joinToString(separator = " ") {
                    if (StringUtil.containsWhitespaces(it) || it.startsWith('"')) {
                        StringUtil.wrapWithDoubleQuote(StringUtil.escapeQuotes(it))
                    } else it
                }
            }

            compilerSettings?.additionalArguments = additionalArgumentsString.ifEmpty { CompilerSettings.DEFAULT_ADDITIONAL_ARGUMENTS }

            /* 'Reset' ignored fields and arguments that will be exposed as 'additional arguments' to the user */
            with(this::class.java.getDeclaredConstructor().newInstance()) {
                copyFieldsSatisfying(this, this@updateCompilerArguments) { exposeAsAdditionalArgument(it) || it.name in ignoredFields }
            }

            updateMergedArguments()
        }
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
        return null
    } else if (new == null) {
        return old
    } else if (old == null) {
        return new
    }

    return (old + new).distinct().toTypedArray()
}
