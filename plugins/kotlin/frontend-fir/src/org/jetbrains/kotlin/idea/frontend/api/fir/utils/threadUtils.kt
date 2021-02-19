/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.utils

import org.jetbrains.kotlin.idea.frontend.api.tokens.HackToForceAllowRunningAnalyzeOnEDT
import org.jetbrains.kotlin.idea.frontend.api.tokens.hackyAllowRunningOnEdt
import org.jetbrains.kotlin.idea.util.application.isDispatchThread

@HackToForceAllowRunningAnalyzeOnEDT
internal inline fun <R> runInPossiblyEdtThread(action: () -> R): R = when {
    !isDispatchThread() -> action()
    else -> hackyAllowRunningOnEdt(action)
}