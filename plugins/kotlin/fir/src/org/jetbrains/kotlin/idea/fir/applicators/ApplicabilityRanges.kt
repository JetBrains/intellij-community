// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.applicators

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.fir.api.applicator.applicabilityTarget
import org.jetbrains.kotlin.psi.KtCallableDeclaration

object ApplicabilityRanges {
    val SELF = applicabilityTarget<PsiElement> { it }

    val CALLABLE_RETURN_TYPE = applicabilityTarget<KtCallableDeclaration> { decalration ->
        decalration.typeReference?.typeElement
    }
}