// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.low.level.api

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.LLFirResolveSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.createFirResolveSessionForNoCaching
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.psi.KtElement

internal inline fun <R> resolveWithClearCaches(
    context: KtElement,
    noinline configureSession: LLFirSession.() -> Unit = {},
    action: (LLFirResolveSession) -> R,
): R {
    val project = context.project
    val firResolveSession = createFirResolveSessionForNoCaching(context.getKtModule(project), project, configureSession)
    return action(firResolveSession)
}
