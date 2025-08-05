// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractFunctionNameInspection
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction

class FunctionNameInspection : AbstractFunctionNameInspection() {
    override fun KtNamedFunction.isFactoryFunctionByAnalyze(): Boolean = analyze(this) {
        val functionName = name ?: return false
        val returnType = returnType

        return returnType.hasShortName(functionName)
                || returnType.allSupertypes.any { it.hasShortName(functionName) }
    }

    context(_: KaSession)
    private fun KaType.hasShortName(shortName: String): Boolean {
        val typeShortName =
            expandedSymbol
                ?.classId
                ?.relativeClassName
                ?.takeUnless(FqName::isRoot)
                ?.shortName()
                ?.identifierOrNullIfSpecial
                ?: return false

        return shortName == typeShortName
    }
}