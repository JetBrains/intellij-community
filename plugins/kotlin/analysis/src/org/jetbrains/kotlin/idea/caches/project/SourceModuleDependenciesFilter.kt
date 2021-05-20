// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.caches.project

import org.jetbrains.kotlin.idea.klib.AbstractKlibLibraryInfo
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.konan.isNative

/**
 * Filter for dependencies on source modules.
 * This shall act as last line of defense for catastrophic misconfiguration of order entries.
 * Generally, we do trust Gradle/Import/Users to select only 'reasonable' dependencies for source modules.
 * However: Passing dependencies produced by one Kotlin backend into analysis of another platform might have unpredictable/unwanted
 * consequences and is forbidden under the implementations rules (even if users added those order entries themselves explicitly)
 */
internal interface SourceModuleDependenciesFilter {
    fun isSupportedDependency(dependency: IdeaModuleInfo): Boolean
}

internal class HmppSourceModuleDependencyFilter(
    private val dependeePlatform: TargetPlatform,
) : SourceModuleDependenciesFilter {

    data class KlibLibraryGist(val isStdlib: Boolean)

    private fun klibLibraryGistOrNull(info: IdeaModuleInfo): KlibLibraryGist? {
        return if (info is AbstractKlibLibraryInfo) KlibLibraryGist(isStdlib = info.libraryRoot.endsWith(KONAN_STDLIB_NAME))
        else null
    }

    override fun isSupportedDependency(dependency: IdeaModuleInfo): Boolean {
        /* Filter only acts on LibraryInfo */
        return if (dependency is LibraryInfo) {
            isSupportedDependency(dependency.platform, klibLibraryGistOrNull(dependency))
        } else true
    }

    fun isSupportedDependency(
        dependencyPlatform: TargetPlatform,
        klibLibraryGist: KlibLibraryGist? = null,
    ): Boolean {
        // HACK: allow depending on stdlib even if platforms do not match
        if (dependeePlatform.isNative() && klibLibraryGist != null && klibLibraryGist.isStdlib) return true

        val platformsWhichAreNotContainedInOther = dependeePlatform.componentPlatforms - dependencyPlatform.componentPlatforms
        if (platformsWhichAreNotContainedInOther.isEmpty()) return true

        // unspecifiedNativePlatform is effectively a wildcard for NativePlatform
        if (platformsWhichAreNotContainedInOther.all { it is NativePlatform } &&
            NativePlatforms.unspecifiedNativePlatform.componentPlatforms.single() in dependencyPlatform.componentPlatforms
        ) return true

        // Allow dependencies from any shared native to any other shared native platform.
        //  This will also include dependencies built by the commonizer with one or more missing targets
        //  The Kotlin Gradle Plugin will decide if the dependency is still used in that case.
        //  Since compiling metadata will be possible with this KLIB, the IDE also analyzes the code with it.
        if (dependeePlatform.isSharedNative() && klibLibraryGist != null && dependencyPlatform.isSharedNative()) return true

        return false
    }
}

internal class NonHmppSourceModuleDependenciesFilter(
    private val dependeePlatform: TargetPlatform
) : SourceModuleDependenciesFilter {
    override fun isSupportedDependency(dependency: IdeaModuleInfo): Boolean {
        /* Filter only acts on LibraryInfo */
        return if (dependency is LibraryInfo) {
            isSupportedDependency(dependency.platform)
        } else true
    }

    private fun isSupportedDependency(dependencyPlatform: TargetPlatform): Boolean {
        return dependeePlatform.isJvm() && dependencyPlatform.isJvm() ||
                dependeePlatform.isJs() && dependencyPlatform.isJs() ||
                dependeePlatform.isNative() && dependencyPlatform.isNative() ||
                dependeePlatform.isCommon() && dependencyPlatform.isCommon()
    }
}

