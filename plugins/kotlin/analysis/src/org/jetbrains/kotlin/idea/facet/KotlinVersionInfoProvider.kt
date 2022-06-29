// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModel
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

interface KotlinVersionInfoProvider {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinVersionInfoProvider> = ExtensionPointName("org.jetbrains.kotlin.versionInfoProvider")
    }

    fun getCompilerVersion(module: Module): IdeKotlinVersion?
    fun getLibraryVersions(
        module: Module,
        platformKind: IdePlatformKind,
        rootModel: ModuleRootModel?
    ): Collection<IdeKotlinVersion>
}

fun getRuntimeLibraryVersions(
    module: Module,
    rootModel: ModuleRootModel?,
    platformKind: IdePlatformKind
): Collection<IdeKotlinVersion> {
    return KotlinVersionInfoProvider.EP_NAME.extensionList.asSequence()
        .map { it.getLibraryVersions(module, platformKind, rootModel) }
        .firstOrNull { it.isNotEmpty() } ?: emptyList()
}

fun getLibraryLanguageLevel(
    module: Module,
    rootModel: ModuleRootModel?,
    platformKind: IdePlatformKind?,
    coerceRuntimeLibraryVersionToReleased: Boolean = true
): LanguageVersion = getLibraryVersion(module, rootModel, platformKind, coerceRuntimeLibraryVersionToReleased).languageVersion

fun getLibraryVersion(
    module: Module,
    rootModel: ModuleRootModel?,
    platformKind: IdePlatformKind?,
    coerceRuntimeLibraryVersionToReleased: Boolean = true
): IdeKotlinVersion {
    val minVersion = getRuntimeLibraryVersions(module, rootModel, platformKind ?: JvmPlatforms.defaultJvmPlatform.idePlatformKind)
        .addReleaseVersionIfNecessary(coerceRuntimeLibraryVersionToReleased)
        .minOrNull()
    return getDefaultVersion(module, minVersion, coerceRuntimeLibraryVersionToReleased)
}

fun getDefaultVersion(
    module: Module,
    explicitVersion: IdeKotlinVersion? = null,
    coerceRuntimeLibraryVersionToReleased: Boolean = true
): IdeKotlinVersion {
    if (explicitVersion != null) {
        return explicitVersion
    }

    val libVersion = KotlinVersionInfoProvider.EP_NAME.extensions
        .mapNotNull { it.getCompilerVersion(module) }
        .addReleaseVersionIfNecessary(coerceRuntimeLibraryVersionToReleased)
        .minOrNull()

    return libVersion ?: KotlinPluginLayout.instance.standaloneCompilerVersion
}

fun getDefaultLanguageLevel(
    module: Module,
    explicitVersion: IdeKotlinVersion? = null,
    coerceRuntimeLibraryVersionToReleased: Boolean = true
): LanguageVersion = getDefaultVersion(module, explicitVersion, coerceRuntimeLibraryVersionToReleased).languageVersion

private fun Iterable<IdeKotlinVersion>.addReleaseVersionIfNecessary(shouldAdd: Boolean): Iterable<IdeKotlinVersion> =
    if (shouldAdd) this + KotlinPluginLayout.instance.standaloneCompilerVersion else this

fun getRuntimeLibraryVersion(module: Module): IdeKotlinVersion? {
    val settingsProvider = KotlinFacetSettingsProvider.getInstance(module.project) ?: return null
    val targetPlatform = settingsProvider.getInitializedSettings(module).targetPlatform
    val versions = getRuntimeLibraryVersions(module, null, (targetPlatform ?: JvmPlatforms.defaultJvmPlatform).idePlatformKind)
    return versions.toSet().singleOrNull()
}

fun getRuntimeLibraryVersionOrDefault(module: Module): IdeKotlinVersion {
    return getRuntimeLibraryVersion(module) ?: KotlinPluginLayout.instance.standaloneCompilerVersion
}