// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter

internal val KaFirDiagnostic.AssignmentTypeMismatch.assignmentExpression: KtBinaryExpression?
    get() = (psi as? KtBinaryExpression)?.takeIf { it.operationToken in KtTokens.ALL_ASSIGNMENTS }

internal val KaFirDiagnostic.AssignmentTypeMismatch.expression: KtExpression
    get() = assignmentExpression?.right ?: psi

internal val KaFirDiagnostic.InitializerTypeMismatch.initializer: KtExpression?
    get() = when (val psi = psi) {
        is KtDeclarationWithInitializer -> psi.initializer
        is KtParameter -> psi.defaultValue
        else -> null
    }
