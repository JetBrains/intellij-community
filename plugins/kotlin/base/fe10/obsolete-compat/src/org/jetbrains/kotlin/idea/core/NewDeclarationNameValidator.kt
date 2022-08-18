// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider.ValidatorTarget
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.psi.KtDeclaration

@Deprecated("Use 'Fe10KotlinNewDeclarationNameValidator' instead")
class NewDeclarationNameValidator(
    container: PsiElement,
    anchor: PsiElement?,
    target: Target,
    excludedDeclarations: List<KtDeclaration> = emptyList()
) : Fe10KotlinNewDeclarationNameValidator(container, anchor, target.newTarget, excludedDeclarations) {
    enum class Target(val newTarget: ValidatorTarget) {
        VARIABLES(ValidatorTarget.VARIABLE),
        FUNCTIONS_AND_CLASSES(ValidatorTarget.FUNCTION)
    }
}