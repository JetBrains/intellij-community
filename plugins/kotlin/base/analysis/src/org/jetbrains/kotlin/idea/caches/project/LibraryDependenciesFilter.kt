// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.analysis.libraries.KlibLibraryDependencyCandidate
import org.jetbrains.kotlin.idea.base.analysis.libraries.LibraryDependencyCandidate
import org.jetbrains.kotlin.idea.base.platforms.isSharedNative
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
import org.jetbrains.kotlin.platform.konan.NativePlatformWithTarget

@ApiStatus.Internal
@K1ModeProjectStructureApi
fun interface LibraryDependenciesFilter {
    operator fun invoke(platform: TargetPlatform, candidates: Collection<LibraryDependencyCandidate>): Set<LibraryDependencyCandidate>
}

/**
 * Returns only candidates that 'support' all platforms aka. where the dependee's platform is a subset of the dependencies platforms.
 * jvm, js -> jvm, js, native {OK} (at least all platforms present)
 * jvm, js, native -> jvm, js {NO} (missing native platform)
 */
@ApiStatus.Internal
@K1ModeProjectStructureApi
object DefaultLibraryDependenciesFilter : LibraryDependenciesFilter {
    override fun invoke(platform: TargetPlatform, candidates: Collection<LibraryDependencyCandidate>): Set<LibraryDependencyCandidate> {
        return candidates.filterTo(LinkedHashSet(candidates.size)) { candidate -> platform representsSubsetOf candidate.platform }
    }
}

/**
 * Similar to [DefaultLibraryDependenciesFilter], but checks for precise platforms match for platform dependencies.
 *
 *    jvm -> jvm { OK }
 *    js -> js { OK }
 *    jvm -> jvm, js { NOT OK } <- difference from [DefaultLibraryDependenciesFilter] !
 *    jvm, js -> jvm, js, native { OK }.
 *
 * It is useful for Gradle Metadata-unaware clients, like Maven, as they might receive
 * dependencies on the common parts of a library along with platform-specific parts,
 * leading to potential clash in resolution. See KTIJ-15758
 */
@ApiStatus.Internal
@K1ModeProjectStructureApi
object StrictEqualityForPlatformSpecificCandidatesFilter : LibraryDependenciesFilter {
    override fun invoke(platform: TargetPlatform, candidates: Collection<LibraryDependencyCandidate>): Set<LibraryDependencyCandidate> {
        return candidates.filterTo(LinkedHashSet(candidates.size)) { candidate ->
            if (platform.size == 1)
                platform == candidate.platform
            else
                platform representsSubsetOf candidate.platform
        }
    }
}

/**
 * Returns only "fallback" libraries for shared native dependee libraries depending on interop libraries.
 * User projects might have less native targets than the libraries it is using. This is OK, however we therefore
 * do not generate the "perfect" commonization for this libraries.
 * We can still analyze this library (with more targets than our project) with the "next best" commonization at hand.
 * The "next best" commonization will give full coverage of all declarations that the library might use and is OK to leak into
 * source module analysis, since it is present there anyways (and by definition is the perfect fit for that source set)
 * see: https://youtrack.jetbrains.com/issue/KT-40814
 */
@ApiStatus.Internal
@K1ModeProjectStructureApi
object SharedNativeLibraryToNativeInteropFallbackDependenciesFilter : LibraryDependenciesFilter {
    override fun invoke(platform: TargetPlatform, candidates: Collection<LibraryDependencyCandidate>): Set<LibraryDependencyCandidate> {
        /* Filter only works on shared native dependee libraries to interop dependency libraries */
        if (!platform.isSharedNative()) return emptySet()

        return candidates.filterIsInstance<KlibLibraryDependencyCandidate>()
            .filter { it.isInterop && it.uniqueName != null }
            .groupBy { it.uniqueName }
            .flatMapTo(mutableSetOf()) { (_, candidates) ->
                val allCandidatePlatforms = candidates.mapTo(mutableSetOf()) { it.platform }
                val allCompatibleCandidatePlatforms = allCandidatePlatforms.filter { platform representsSubsetOf it }
                val allPlatformsCoveredByCandidates = allCompatibleCandidatePlatforms.any { it representsSubsetOf platform }
                if (allPlatformsCoveredByCandidates) return@flatMapTo emptyList()

                /* Not all platforms are covered. Carefully choose next best */
                val fallbackPermittedPlatform = allCandidatePlatforms
                    .filter { candidatePlatform -> candidatePlatform representsSubsetOf candidatePlatform }
                    .sortedWith(TargetPlatformComparator) // Guarantee a stable choice for "maxByOrNull"
                    .maxByOrNull { candidatePlatform -> candidatePlatform.componentPlatforms.size }

                candidates.filter { it.platform == fallbackPermittedPlatform }
            }
    }

    private object TargetPlatformComparator :
        Comparator<TargetPlatform> by compareBy<TargetPlatform>({ platform -> platform.componentPlatforms.size })
            .thenComparing({ platform -> platform.componentPlatforms.sortedWith(SimplePlatformComparator).joinToString() })

    private object SimplePlatformComparator :
        Comparator<SimplePlatform> by compareBy<SimplePlatform>({ it.targetName })
            .thenComparing<String>({ it.platformName })
}


/**
 * @return true if all platforms in [this@isCompatibleTo] have a compatible match in [to]
 *
 * e.g.
 * a, b, c -> a, b, c, d #true (all platforms available in "to")
 * a, b, c -> a, b #false (*not* all platforms available in "to")
 */
private infix fun TargetPlatform.representsSubsetOf(to: TargetPlatform): Boolean {
    return componentPlatforms.all { fromSimplePlatform ->
        to.componentPlatforms.any { toSimplePlatform -> fromSimplePlatform.representsSubsetOf(toSimplePlatform) }
    }
}

private fun SimplePlatform.representsSubsetOf(to: SimplePlatform): Boolean {
    return when {
        this == to -> true
        this is NativePlatformWithTarget && to is NativePlatformUnspecifiedTarget -> true
        else -> false
    }
}

/* Operators on LibraryDependenciesFilter */

@ApiStatus.Internal
@K1ModeProjectStructureApi
infix fun LibraryDependenciesFilter.union(other: LibraryDependenciesFilter): LibraryDependenciesFilter {
    if (this is LibraryDependenciesFilterUnion && other is LibraryDependenciesFilterUnion) {
        return LibraryDependenciesFilterUnion(this.filters + other.filters)
    }
    if (this is LibraryDependenciesFilterUnion) {
        return LibraryDependenciesFilterUnion(filters + other)
    }
    if (other is LibraryDependenciesFilterUnion) {
        return LibraryDependenciesFilterUnion(listOf(this) + other.filters)
    }
    return LibraryDependenciesFilterUnion(listOf(this, other))
}

@ApiStatus.Internal
@K1ModeProjectStructureApi
class LibraryDependenciesFilterUnion(
    val filters: List<LibraryDependenciesFilter>
) : LibraryDependenciesFilter {
    override fun invoke(platform: TargetPlatform, candidates: Collection<LibraryDependencyCandidate>): Set<LibraryDependencyCandidate> {
        return filters
            .map { filter ->
                ProgressManager.checkCanceled()
                filter(platform, candidates)
            }
            .reduce { acc, set -> acc + set }
    }

    init {
        require(filters.isNotEmpty()) {
            "${this::class.java.simpleName} requires at least one filter"
        }
    }
}
