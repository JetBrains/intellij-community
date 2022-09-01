// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.config.isHmpp
import org.jetbrains.kotlin.idea.facet.KotlinFacetModificationTracker
import org.jetbrains.kotlin.idea.facet.KotlinFacet

object KotlinMultiplatformAnalysisModeComponent {
    private const val resolutionModeOption = "kotlin.multiplatform.analysis.mode"
    private val defaultState = Mode.SEPARATE

    @JvmStatic
    fun setMode(project: Project, mode: Mode) {
        PropertiesComponent.getInstance(project).setValue(resolutionModeOption, mode.name, defaultState.name)
    }

    @JvmStatic
    fun getMode(project: Project): Mode {
        PropertiesComponent.getInstance(project).getValue(resolutionModeOption)?.let { explicitIdeaSetting ->
            return Mode.valueOf(explicitIdeaSetting)
        }

        if (project.containsImportedHmppModules()) return Mode.COMPOSITE

        return defaultState
    }

    private fun Project.containsImportedHmppModules(): Boolean =
        CachedValuesManager.getManager(this).getCachedValue(this) {
            val containsImportedHmppModules = runReadAction {
                ModuleManager.getInstance(this).modules.asSequence()
                    .mapNotNull { KotlinFacet.get(it) }
                    .any { it.configuration.settings.mppVersion.isHmpp }
            }
            CachedValueProvider.Result(
                containsImportedHmppModules,
                KotlinFacetModificationTracker.getInstance(this)
            )
        }!!


    enum class Mode {
        // Analyses each platform in a separate [GlobalFacade]
        SEPARATE,

        // Analyses all platforms in one facade. Uses type refinement and [CompositeResolverForModuleFactory]
        COMPOSITE
    }
}

/**
 * Note that Kotlin Resolution can work in a mode, when some operations are performed in COMPOSITE mode, and some in SEPARATE.
 * This specific property only shows global project-wide setting, so use it with a lot of caution.
 */
val Project.useCompositeAnalysis: Boolean
    get() = KotlinMultiplatformAnalysisModeComponent.getMode(this) == KotlinMultiplatformAnalysisModeComponent.Mode.COMPOSITE