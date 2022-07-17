// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyzeWithReadAction
import org.jetbrains.kotlin.analysis.api.calls.KtCall
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.psi.KtElement

inline fun <R> withResolvedCall(element: KtElement, crossinline block: KtAnalysisSession.(KtCall) -> R): R? {
    return analyzeWithReadAction(element) {
        element.resolveCall()?.calls?.singleOrNull()?.let { block(it) }
    }
}
