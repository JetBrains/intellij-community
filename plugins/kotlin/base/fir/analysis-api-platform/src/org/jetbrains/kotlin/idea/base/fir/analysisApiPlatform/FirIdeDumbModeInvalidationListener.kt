// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.DumbModeListenerBackgroundable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent

/**
 * When exiting [restricted analysis][org.jetbrains.kotlin.analysis.api.platform.restrictedAnalysis.KotlinRestrictedAnalysisService], the
 * Analysis API platform must publish a global module state modification event. See the linked KDoc for more information on this contract.
 */
internal class FirIdeDumbModeInvalidationListener(private val project: Project) : DumbModeListenerBackgroundable {
    override fun exitDumbMode() {
        runWriteAction {
            project.publishGlobalModuleStateModificationEvent()
        }
    }
}
