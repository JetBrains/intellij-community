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

    override val isRestrictedAnalysisAllowed: Boolean
        get() {
            // The flag should not be cached by the service because it can be changed without a restart.
            return Registry.`is`("kotlin.analysis.allowRestrictedAnalysis", false)
        }

    override fun rejectRestrictedAnalysis(): Nothing {
        throw IndexNotReadyException.create()
    }
}

/**
 * Accesses indices with [DumbModeAccessType.RELIABLE_DATA_ONLY] if deemed necessary and applicable by [KotlinRestrictedAnalysisService].
 */
internal inline fun <R> KotlinRestrictedAnalysisService?.withDumbModeHandling(crossinline action: () -> R): R {
    return if (this != null && isAnalysisRestricted && isRestrictedAnalysisAllowed) {
        FileBasedIndex.getInstance().ignoreDumbMode(
            DumbModeAccessType.RELIABLE_DATA_ONLY,
            ThrowableComputable { action() },
        )
    } else {
        action()
    }
}
