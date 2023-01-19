// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtPrefixExpression

@ApiStatus.Internal
interface KotlinInspectionFacade {
    val simplifyNegatedBinaryExpression: AbstractApplicabilityBasedInspection<KtPrefixExpression>
    val sortModifiers: AbstractApplicabilityBasedInspection<KtModifierList>
    val removeEmptyClassBody: AbstractApplicabilityBasedInspection<KtClassBody>

    companion object {
        val instance: KotlinInspectionFacade
            get() = service()
    }
}