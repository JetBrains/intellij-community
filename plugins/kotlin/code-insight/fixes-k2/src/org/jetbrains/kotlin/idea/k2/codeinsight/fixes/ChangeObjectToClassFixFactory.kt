// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal object ChangeObjectToClassFixFactory {

    val changeObjectToClassFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ConstructorInObject ->
        val element = diagnostic.psi as? KtConstructor<*> ?: return@ModCommandBased emptyList()
        val containingObject = element.containingClassOrObject as? KtObjectDeclaration ?: return@ModCommandBased emptyList()

        if (containingObject.isCompanion()) return@ModCommandBased emptyList()

        listOf(
            ChangeObjectToClassFix(containingObject)
        )
    }
}
