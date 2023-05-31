// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.low.level.api

import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirResolveSessionService
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.LLFirResolveSession
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.psi.KtElement

internal inline fun <R> resolveWithClearCaches(context: KtElement, action: (LLFirResolveSession) -> R): R {
    val project = context.project
    val module = ProjectStructureProvider.getModule(project, context, null)
    val resolveSession = LLFirResolveSessionService.getInstance(project).getFirResolveSessionNoCaching(module)
    return action(resolveSession)
}
