// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.codeinsight.utils.isFinalizeMethod
import org.jetbrains.kotlin.idea.codeinsight.utils.isInheritable
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import java.util.EnumSet

internal class ProtectedInFinalInspection : ProtectedInFinalInspectionBase() {
    override fun isApplicable(parentClass: KtClass, declaration: KtDeclaration): Boolean =
        !parentClass.isInheritable() && !declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) && !parentClass.isEnum() &&
            analyze(declaration) {
                parentClass.symbol.modality !in inheritableModalities // compiler plugins might affect modality, the PSI check is not enough
                        && !declaration.isFinalizeMethod()
            }
}

private val inheritableModalities: Set<KaSymbolModality> = EnumSet.of(
    KaSymbolModality.ABSTRACT,
    KaSymbolModality.OPEN,
    KaSymbolModality.SEALED,
)
