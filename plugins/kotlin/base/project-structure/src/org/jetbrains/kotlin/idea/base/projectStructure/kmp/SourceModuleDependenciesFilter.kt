// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.kmp

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.platforms.isSharedNative
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
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
@ApiStatus.Internal
interface SourceModuleDependenciesFilter {
    fun isSupportedDependency(dependency: SourceModuleDependenciesFilterCandidate): Boolean
}

@ApiStatus.Internal
class HmppSourceModuleDependencyFilter(private val dependeePlatform: TargetPlatform) : SourceModuleDependenciesFilter {
    override fun isSupportedDependency(dependency: SourceModuleDependenciesFilterCandidate): Boolean {
        /* Filter only acts on libraries */
        return if (dependency is SourceModuleDependenciesFilterCandidate.LibraryDependency) {
            isSupportedDependency(dependency)
        } else true
    }

    private fun isSupportedDependency(dependency: SourceModuleDependenciesFilterCandidate.LibraryDependency): Boolean {
        val dependencyPlatform: TargetPlatform = dependency.targetPlatform
        val isKlibDependency = dependency is SourceModuleDependenciesFilterCandidate.KlibLibraryDependency

        // HACK: allow depending on stdlib even if platforms do not match
        if (dependeePlatform.isNative() && isKlibDependency && dependency.isNativeStdlib) return true

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
        if (dependeePlatform.isSharedNative() && isKlibDependency && dependencyPlatform.isSharedNative()) return true

        return false
    }
}

@ApiStatus.Internal
class NonHmppSourceModuleDependenciesFilter(private val dependeePlatform: TargetPlatform) : SourceModuleDependenciesFilter {
    override fun isSupportedDependency(dependency: SourceModuleDependenciesFilterCandidate): Boolean {
        /* Filter only acts on libraries */
        return if (dependency is SourceModuleDependenciesFilterCandidate.LibraryDependency) {
            isSupportedDependency(dependency.targetPlatform)
        } else true
    }

    private fun isSupportedDependency(dependencyPlatform: TargetPlatform): Boolean {
        return dependeePlatform.isJvm() && dependencyPlatform.isJvm() ||
                dependeePlatform.isJs() && dependencyPlatform.isJs() ||
                dependeePlatform.isNative() && dependencyPlatform.isNative() ||
                dependeePlatform.isCommon() && dependencyPlatform.isCommon()
    }
}

internal class NegatedModuleDependencyFilter(private val original: SourceModuleDependenciesFilter) : SourceModuleDependenciesFilter {
    override fun isSupportedDependency(dependency: SourceModuleDependenciesFilterCandidate): Boolean {
        return !original.isSupportedDependency(dependency)
    }
}

@ApiStatus.Internal
sealed class SourceModuleDependenciesFilterCandidate {
    abstract val targetPlatform: TargetPlatform

    data class ModuleDependency(
        override val targetPlatform: TargetPlatform,
    ) : SourceModuleDependenciesFilterCandidate()

    sealed class LibraryDependency: SourceModuleDependenciesFilterCandidate()

    data class KlibLibraryDependency(
        override val targetPlatform: TargetPlatform,
        val isNativeStdlib: Boolean,
    ) : LibraryDependency()

    data class NonKlibLibraryDependency(
        override val targetPlatform: TargetPlatform,
    ) : LibraryDependency()
}

