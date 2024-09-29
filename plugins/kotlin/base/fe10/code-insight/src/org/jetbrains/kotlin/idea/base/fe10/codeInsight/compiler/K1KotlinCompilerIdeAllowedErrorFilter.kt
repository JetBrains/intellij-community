// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.compiler

import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.codeInsight.compiler.KotlinCompilerIdeAllowedErrorFilter

class K1KotlinCompilerIdeAllowedErrorFilter : KotlinCompilerIdeAllowedErrorFilter {
    private companion object {
        val ALLOWED_ERRORS = listOf(
            Errors.INVISIBLE_REFERENCE,
            Errors.INVISIBLE_MEMBER,
            Errors.INVISIBLE_SETTER,
            Errors.DEPRECATION_ERROR,
            Errors.DIVISION_BY_ZERO,
            Errors.OPT_IN_USAGE_ERROR,
            Errors.OPT_IN_OVERRIDE_ERROR,
            Errors.OPT_IN_TO_INHERITANCE_ERROR,
            Errors.UNSAFE_CALL,
            Errors.UNSAFE_OPERATOR_CALL,
            Errors.ITERATOR_ON_NULLABLE,
            Errors.UNEXPECTED_SAFE_CALL,
            Errors.DSL_SCOPE_VIOLATION
        ).map { it.name }
    }

    override fun invoke(diagnostic: KaDiagnostic): Boolean {
        return diagnostic.factoryName in ALLOWED_ERRORS
    }
}