// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.Version
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.orDefault

interface KotlinVersionInfoProvider {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinVersionInfoProvider> = ExtensionPointName("org.jetbrains.kotlin.versionInfoProvider")
    }

    fun getCompilerVersion(module: Module): String?
    fun getLibraryVersions(
        module: Module,
        platformKind: IdePlatformKind,
        rootModel: ModuleRootModel?
    ): Collection<String>
}

fun getRuntimeLibraryVersions(
    module: Module,
    rootModel: ModuleRootModel?,
    platformKind: IdePlatformKind
): Collection<String> {
    return KotlinVersionInfoProvider.EP_NAME.extensionList.asSequence()
        .map { it.getLibraryVersions(module, platformKind, rootModel) }
        .firstOrNull { it.isNotEmpty() } ?: emptyList()
}

fun getLibraryLanguageLevel(
    module: Module,
    rootModel: ModuleRootModel?,
    platformKind: IdePlatformKind?,
    coerceRuntimeLibraryVersionToReleased: Boolean = true
) = getLibraryVersion(module, rootModel, platformKind, coerceRuntimeLibraryVersionToReleased).languageVersion

fun getLibraryVersion(
    module: Module,
    rootModel: ModuleRootModel?,
    platformKind: IdePlatformKind?,
    coerceRuntimeLibraryVersionToReleased: Boolean = true
): Version {
    val minVersion = getRuntimeLibraryVersions(module, rootModel, platformKind.orDefault())
        .addReleaseVersionIfNecessary(coerceRuntimeLibraryVersionToReleased)
        .minWithOrNull(VersionComparatorUtil.COMPARATOR)
    return getDefaultVersion(module, minVersion, coerceRuntimeLibraryVersionToReleased)
}

fun getDefaultVersion(
    module: Module,
    explicitVersion: String? = null,
    coerceRuntimeLibraryVersionToReleased: Boolean = true
): Version {
    val libVersion = explicitVersion
        ?: KotlinVersionInfoProvider.EP_NAME.extensions
            .mapNotNull { it.getCompilerVersion(module) }
            .addReleaseVersionIfNecessary(coerceRuntimeLibraryVersionToReleased)
            .minWithOrNull(VersionComparatorUtil.COMPARATOR)
    return Version.parse(libVersion)
}

fun getDefaultLanguageLevel(
    module: Module,
    explicitVersion: String? = null,
    coerceRuntimeLibraryVersionToReleased: Boolean = true
) = getDefaultVersion(module, explicitVersion, coerceRuntimeLibraryVersionToReleased).languageVersion

fun String?.toLanguageVersion(): LanguageVersion = when {
    this == null -> LanguageVersion.LATEST_STABLE
    startsWith("1.7") -> LanguageVersion.KOTLIN_1_7
    startsWith("1.6") -> LanguageVersion.KOTLIN_1_6
    startsWith("1.5") -> LanguageVersion.KOTLIN_1_5
    startsWith("1.4") -> LanguageVersion.KOTLIN_1_4
    startsWith("1.3") -> LanguageVersion.KOTLIN_1_3
    startsWith("1.2") -> LanguageVersion.KOTLIN_1_2
    startsWith("1.1") -> LanguageVersion.KOTLIN_1_1
    startsWith("1.0") -> LanguageVersion.KOTLIN_1_0
    else -> LanguageVersion.LATEST_STABLE
}

fun String?.toApiVersion(): ApiVersion = ApiVersion.createByLanguageVersion(toLanguageVersion())

private fun Iterable<String>.addReleaseVersionIfNecessary(shouldAdd: Boolean): Iterable<String> =
    if (shouldAdd) this + LanguageVersion.LATEST_STABLE.versionString else this

fun getRuntimeLibraryVersion(module: Module): String? {
    val settingsProvider = KotlinFacetSettingsProvider.getInstance(module.project) ?: return null
    val targetPlatform = settingsProvider.getInitializedSettings(module).targetPlatform
    val versions = getRuntimeLibraryVersions(module, null, targetPlatform.orDefault().idePlatformKind)
    return versions.toSet().singleOrNull()
}

fun getCleanRuntimeLibraryVersion(module: Module) = getRuntimeLibraryVersion(module)?.cleanUpVersion()

private fun String.cleanUpVersion(): String {
    return StringBuilder(this)
        .apply {
            val parIndex = indexOf("(")
            if (parIndex >= 0) {
                delete(parIndex, length)
            }
            val releaseIndex = indexOf("-release-")
            if (releaseIndex >= 0) {
                delete(releaseIndex, length)
            }
        }
        .toString()
        .trim()
}
