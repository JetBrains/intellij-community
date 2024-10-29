// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModificationService

internal class InvalidateCachesAfterDumbMode(private val project: Project) : DumbService.DumbModeListener {
    override fun exitDumbMode() {
        runWriteAction {
             KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
        }
    }
}