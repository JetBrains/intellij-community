// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.analysis.api.platform.restrictedAnalysis.KotlinRestrictedAnalysisService

internal class IdeKotlinRestrictedAnalysisService(private val project: Project) : KotlinRestrictedAnalysisService {
    override val isAnalysisRestricted: Boolean
        get() = DumbService.isDumb(project)

    override val isRestrictedAnalysisAllowed: Boolean = Registry.`is`("kotlin.analysis.allowRestrictedAnalysis", true)

    override fun rejectRestrictedAnalysis(): Nothing {
        throw IndexNotReadyException.create()
    }

    override fun <R> runWithRestrictedDataAccess(action: () -> R): R =
        FileBasedIndex.getInstance().ignoreDumbMode(
            DumbModeAccessType.RELIABLE_DATA_ONLY,
            ThrowableComputable(action),
        )
}
