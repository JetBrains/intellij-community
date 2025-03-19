// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking

import com.intellij.psi.PsiElement

internal data class RunBlockingProblem(val element: PsiElement, val stacTrace: List<TraceElement>)