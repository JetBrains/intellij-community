// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.low.level.api

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.FirModuleResolveState
import org.jetbrains.kotlin.analysis.low.level.api.fir.createResolveStateForNoCaching
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.FirIdeSession
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.psi.KtElement

internal inline fun <R> resolveWithClearCaches(
    context: KtElement,
    noinline configureSession: FirIdeSession.() -> Unit = {},
    action: (FirModuleResolveState) -> R,
): R {
    val project = context.project
    val resolveState = createResolveStateForNoCaching(context.getKtModule(project), project, configureSession)
    return action(resolveState)
}
