// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ExtensionPropertyWithBackingFieldFixFactories {

    val convertToGetterFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExtensionPropertyWithBackingField ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
        val property = expression.getParentOfType<KtProperty>(true)?.takeIf { it.getter == null } ?: return@ModCommandBased emptyList()
        val elementContext = CallableReturnTypeUpdaterUtils.getTypeInfo(property)

        listOf(
            ConvertPropertyInitializerToGetterFix(
                element = property,
                elementContext = elementContext,
                familyName = KotlinBundle.message("convert.extension.property.initializer.to.getter"),
            )
        )
    }
}
