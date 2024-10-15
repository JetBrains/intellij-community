// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.SurroundWithLambdaForTypeMismatchFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty

internal object SurroundWithLambdaForTypeMismatchFixFactory {

    val argumentTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val assignmentTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val returnTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType)
        )
    }

    val initializerTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        listOfNotNull(
            createFixIfAvailable((diagnostic.psi as? KtProperty)?.initializer, diagnostic.expectedType, diagnostic.actualType)
        )
    }

    private fun KaSession.createFixIfAvailable(
        element: PsiElement?,
        expectedType: KaType,
        actualType: KaType,
    ): SurroundWithLambdaForTypeMismatchFix? {
        if (element !is KtExpression || expectedType !is KaFunctionType) return null
        if (expectedType.arity > 1) return null
        val lambdaReturnType = expectedType.returnType

        if (!actualType.withNullability(KaTypeNullability.NON_NULLABLE).isSubtypeOf(lambdaReturnType) &&
            !(actualType.isPrimitiveNumberType() && lambdaReturnType.isPrimitiveNumberType())
        ) return null

        return SurroundWithLambdaForTypeMismatchFix(element)
    }
}
