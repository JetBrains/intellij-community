// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallResolutionAttempt
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleOrMultiCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolution.KtResolvable
import org.jetbrains.kotlin.resolution.KtResolvableCall

@KaExperimentalApi
@OptIn(KaExperimentalApi::class)
context(session: KaSession)
fun KtExpression.resolveSuccessfulExpressionCall(): KaSimpleOrMultiCall? =
    (this as? KtResolvableCall)?.resolveSuccessfulCall()

@KaExperimentalApi
@OptIn(KaExperimentalApi::class)
context(session: KaSession)
fun KtExpression.tryResolveExpressionCall(): KaCallResolutionAttempt? =
    (this as? KtResolvableCall)?.tryResolveCall()

////// resolve symbol

@KaExperimentalApi
@OptIn(KaExperimentalApi::class)
context(session: KaSession)
fun KtExpression.resolveSuccessfulExpressionSymbol(): KaSymbol? =
    (this as? KtResolvable)?.resolveSuccessfulSymbol()