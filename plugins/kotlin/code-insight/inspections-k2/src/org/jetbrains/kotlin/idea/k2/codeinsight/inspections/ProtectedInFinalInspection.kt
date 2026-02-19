// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.utils.isFinalizeMethod
import org.jetbrains.kotlin.idea.codeinsight.utils.isInheritable
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration

internal class ProtectedInFinalInspection : ProtectedInFinalInspectionBase() {
    override fun isApplicable(parentClass: KtClass, declaration: KtDeclaration): Boolean =
        !parentClass.isInheritable() && !declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) && !parentClass.isEnum() &&
            analyze(declaration) {
                !declaration.isFinalizeMethod()
            }
}