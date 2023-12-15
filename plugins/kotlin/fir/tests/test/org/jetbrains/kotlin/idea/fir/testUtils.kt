// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirResolveSessionService
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.LLFirResolveSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.project.structure.LLFirBuiltinsSessionFactory
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationService
import org.jetbrains.kotlin.psi.KtElement

fun Project.invalidateCaches() {
    runWriteAction {
        KotlinGlobalModificationService.getInstance(this).publishGlobalModuleStateModification()
    }
    service<LLFirBuiltinsSessionFactory>().clearForTheNextTest()
}

inline fun <R> resolveWithClearCaches(context: KtElement, action: (LLFirResolveSession) -> R): R {
    val project = context.project
    val module = ProjectStructureProvider.getModule(project, context, null)
    val resolveSession = LLFirResolveSessionService.getInstance(project).getFirResolveSessionNoCaching(module)
    return action(resolveSession)
}
