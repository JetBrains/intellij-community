// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

@OptIn(KtAllowAnalysisOnEdt::class)
fun KtCallableDeclaration.setType(typeString: String, classId: ClassId?, shortenReferences: Boolean = true) {
    val typeReference = KtPsiFactory(project).createType(typeString)
    setTypeReference(typeReference)
    if (shortenReferences && classId != null) {
        allowAnalysisOnEdt {
            analyze(this) {
                collectPossibleReferenceShortenings(
                    containingKtFile,
                    getTypeReference()!!.textRange,
                ).invokeShortening()
            }
        }
    }
}
