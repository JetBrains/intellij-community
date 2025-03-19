// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.compiler

import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnostic
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.idea.base.codeInsight.compiler.KotlinCompilerIdeAllowedErrorFilter

internal class K2KotlinCompilerIdeAllowedErrorFilter : KotlinCompilerIdeAllowedErrorFilter {

    private val allowedErrors: List<String> = listOf(
        FirErrors.INVISIBLE_REFERENCE,
        FirErrors.INVISIBLE_SETTER,
        FirErrors.DEPRECATION_ERROR,
        FirErrors.DIVISION_BY_ZERO,
        FirErrors.OPT_IN_USAGE_ERROR,
        FirErrors.OPT_IN_TO_INHERITANCE_ERROR,
        FirErrors.OPT_IN_OVERRIDE_ERROR,
        FirErrors.UNSAFE_CALL,
        FirErrors.UNSAFE_IMPLICIT_INVOKE_CALL,
        FirErrors.UNSAFE_INFIX_CALL,
        FirErrors.UNSAFE_OPERATOR_CALL,
        FirErrors.ITERATOR_ON_NULLABLE,
        FirErrors.UNEXPECTED_SAFE_CALL,
        FirErrors.DSL_SCOPE_VIOLATION,
    ).map { it.name }

    override fun invoke(diagnostic: KaDiagnostic): Boolean =
        diagnostic.factoryName in allowedErrors
}