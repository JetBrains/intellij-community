// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModificationService
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirResolveSessionService
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.LLFirResolveSession
import org.jetbrains.kotlin.psi.KtElement

fun Project.invalidateCaches() {
    runWriteAction {
        KotlinGlobalModificationService.getInstance(this).publishGlobalModuleStateModification()
    }
}

@OptIn(LLFirInternals::class)
inline fun <R> resolveWithClearCaches(context: KtElement, action: (LLFirResolveSession) -> R): R {
    val project = context.project
    val module = KotlinProjectStructureProvider.getModule(project, context, useSiteModule = null)
    val resolveSession = LLFirResolveSessionService.getInstance(project).getFirResolveSessionNoCaching(module)
    return action(resolveSession)
}
