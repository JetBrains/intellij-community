// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly

object MoveMemberToCompanionObjectUtils {

    fun KtNamedDeclaration.suggestInstanceName(): String? {
        return containingClass()?.takeIf {
            (this !is KtClass || this.isInner()) && this !is KtProperty
        }?.name?.decapitalizeAsciiOnly()
    }
}