// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
import org.jetbrains.kotlin.psi.KtCallableDeclaration

abstract class AbstractImplicitTypeInspection(
    additionalChecker: (KtCallableDeclaration, AbstractImplicitTypeInspection) -> Boolean
) : IntentionBasedInspection<KtCallableDeclaration>(
    SpecifyTypeExplicitlyIntention::class,
    { element, inspection ->
        with(inspection as AbstractImplicitTypeInspection) {
            element.typeReference == null && additionalChecker(element, inspection)
        }
    }
) {
    override fun inspectionTarget(element: KtCallableDeclaration) = element.nameIdentifier
}