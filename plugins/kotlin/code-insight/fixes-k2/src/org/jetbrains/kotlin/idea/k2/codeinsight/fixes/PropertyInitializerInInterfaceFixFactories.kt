// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.convertPropertyInitializerToGetter
import org.jetbrains.kotlin.psi.KtProperty

internal object PropertyInitializerInInterfaceFixFactories {

    val convertPropertyInitializerToGetterFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.PropertyInitializerInInterface ->
        val element = diagnostic.psi.parent as? KtProperty ?: return@ModCommandBased emptyList()
        val elementContext = CallableReturnTypeUpdaterUtils.getTypeInfo(element)

        listOf(
            ConvertPropertyInitializerToGetterFix(element, elementContext)
        )
    }

    private class ConvertPropertyInitializerToGetterFix(
        element: KtProperty,
        elementContext: CallableReturnTypeUpdaterUtils.TypeInfo,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtProperty, CallableReturnTypeUpdaterUtils.TypeInfo>(element, elementContext) {

        override fun getFamilyName(): String = KotlinBundle.message("convert.property.initializer.to.getter")

        override fun invoke(
            actionContext: ActionContext,
            element: KtProperty,
            elementContext: CallableReturnTypeUpdaterUtils.TypeInfo,
            updater: ModPsiUpdater,
        ) {
            convertPropertyInitializerToGetter(actionContext.project, element, elementContext, updater)
        }
    }
}
