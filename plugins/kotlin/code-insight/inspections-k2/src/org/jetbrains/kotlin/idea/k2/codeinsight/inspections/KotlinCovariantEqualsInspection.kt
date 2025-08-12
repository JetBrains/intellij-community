// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isEqualsMethodSymbol
import org.jetbrains.kotlin.idea.codeinsight.utils.isNullableAnyType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.namedFunctionVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.util.OperatorNameConventions

class KotlinCovariantEqualsInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = namedFunctionVisitor(fun(function) {
        if (function.isTopLevel || function.isLocal) return
        if (function.nameAsName != OperatorNameConventions.EQUALS) return
        val nameIdentifier = function.nameIdentifier ?: return
        val classOrObject = function.containingClassOrObject ?: return
        if (classOrObject is KtObjectDeclaration && classOrObject.isCompanion()) return

        val parameter = function.valueParameters.singleOrNull() ?: return

        analyze(function) {
            val parameterSymbol = parameter.symbol
            val parameterType = parameterSymbol.returnType
            if (parameterType.isNullableAnyType()) return

            val hasOverrideEquals = classOrObject.declarations.any { declaration ->
                declaration is KtNamedFunction && 
                (declaration.symbol as? KaNamedFunctionSymbol)?.isEqualsMethodSymbol() == true
            }
            if (hasOverrideEquals) return

            holder.registerProblem(
                nameIdentifier, 
                KotlinBundle.message("equals.should.take.any.as.its.argument")
            )
        }
    })
}