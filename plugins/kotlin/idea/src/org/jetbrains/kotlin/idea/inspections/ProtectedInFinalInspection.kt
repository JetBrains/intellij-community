// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.idea.core.implicitVisibility
import org.jetbrains.kotlin.idea.core.isInheritable
import org.jetbrains.kotlin.idea.intentions.isFinalizeMethod
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass

import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase
import org.jetbrains.kotlin.psi.KtDeclaration

class ProtectedInFinalInspection : ProtectedInFinalInspectionBase() {
    override fun isApplicable(parentClass: KtClass, declaration: KtDeclaration) =
        !parentClass.isInheritable() &&
        !parentClass.isEnum() &&
        declaration.implicitVisibility() != KtTokens.PROTECTED_KEYWORD &&
        !declaration.isFinalizeMethod()
}
