// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinImportQuickFixAction
import org.jetbrains.kotlin.psi.KtExpression

interface KotlinReferenceImporterFacility {
    companion object {
        fun getInstance(): KotlinReferenceImporterFacility = service()
    }

    /**
     * Note that collected import fixes should not be stored and should be applied immediately.
     */
    fun createImportFixesForExpression(expression: KtExpression): Sequence<KotlinImportQuickFixAction<*>>
}