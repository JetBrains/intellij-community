// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.provider

import org.jetbrains.kotlin.analysis.api.projectStructure.KaBuiltinsModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptDependencyModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.analysis.api.projectStructure.allDirectDependencies


/**
 * Chooses a module from a list of candidates using a specific use-site module.
 *
 * The main idea is that when there are multiple candidates, we should choose the one that is visible from our use-site module.
 * In the case of multiple matching candidates, the one earlier in the classpath is chosen, mimicking the behavior of the Kotlin compiler.
 *
 * Can probably be moved to the Analysis API, see KT-74693
 */
internal object ModuleChooser {
    fun chooseModule(
      modules: Sequence<KaModule>,
      useSiteModule: KaModule?
    ): KaModule? {
        if (useSiteModule != null) {
            chooseByPriority(modules, useSiteModule)?.let { return it }
        }
        return modules.firstOrNull()
    }

    private fun chooseByPriority(
      modules: Sequence<KaModule>,
      useSiteModule: KaModule
    ): KaModule? {
        var bestCandidate: KaModule? = null
        var bestPriority: ModulePriority? = null

        for (candidate in modules) {
            val priority = getCandidatePriority(candidate, useSiteModule) ?: continue
            if (priority.isTheHighestPriority) return candidate
            if (bestPriority == null || priority < bestPriority) {
                bestPriority = priority
                bestCandidate = candidate
            }
        }

        return bestCandidate
    }

    private fun getCandidatePriority(candidate: KaModule, useSiteModule: KaModule): ModulePriority? {
        when (useSiteModule) {
            candidate -> return ModulePriority.Self
            is KaDanglingFileModule -> return getCandidatePriority(candidate, useSiteModule.contextModule)
        }
        when (candidate) {
            is KaScriptDependencyModule -> {
                if (useSiteModule !is KaScriptModule && useSiteModule !is KaScriptDependencyModule) {
                    // only scripts can depend on a script dependency
                    return null
                }
            }

            is KaBuiltinsModule -> {
                // every module implicitly depends on builtins
                return ModulePriority.BuiltIns
            }

            is KaNotUnderContentRootModule, is KaDanglingFileModule, is KaScriptModule -> {
                return null
            }
        }
        when (useSiteModule) {
            is KaLibraryModule, is KaLibrarySourceModule -> {
                // libraries have no dependencies
                return null
            }

            is KaNotUnderContentRootModule -> return null
            is KaBuiltinsModule -> return null
        }
        return getCandidatePriorityFromDependencies(useSiteModule, candidate)
    }

    private fun getCandidatePriorityFromDependencies(
      useSiteModule: KaModule,
      candidate: KaModule
    ): ModulePriority.ModuleDependency? {
        val index = useSiteModule.allDirectDependencies().indexOf(candidate)
        if (index < 0) return null
        return ModulePriority.ModuleDependency(index)
    }

    private sealed class ModulePriority : Comparable<ModulePriority> {
        protected abstract val priorityNumber: Int

        val isTheHighestPriority: Boolean
            get() = priorityNumber == 0

        override fun compareTo(other: ModulePriority): Int {
            return priorityNumber.compareTo(other.priorityNumber)
        }

        // element is from the use-site module, always the maximum priority
        object Self : ModulePriority() {
            override val priorityNumber: Int get() = 0
        }

        // element is from the builtins,
        // builtins should be a last resort if no stdlib present
        object BuiltIns : ModulePriority() {
            override val priorityNumber: Int get() = Int.MAX_VALUE
        }

        class ModuleDependency(val indexInClassPath: Int) : ModulePriority() {
            // `+1` as `0` is the use-site module itself*/
            override val priorityNumber: Int get() = indexInClassPath + 1
        }
    }
}