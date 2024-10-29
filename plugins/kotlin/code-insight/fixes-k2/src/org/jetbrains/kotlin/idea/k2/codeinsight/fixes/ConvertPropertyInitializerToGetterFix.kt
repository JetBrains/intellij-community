// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.convertPropertyInitializerToGetter
import org.jetbrains.kotlin.psi.KtProperty

internal class ConvertPropertyInitializerToGetterFix(
    element: KtProperty,
    elementContext: CallableReturnTypeUpdaterUtils.TypeInfo,
    @IntentionFamilyName private val familyName: String = KotlinBundle.message("convert.property.initializer.to.getter"),
) : KotlinPsiUpdateModCommandAction.ElementBased<KtProperty, CallableReturnTypeUpdaterUtils.TypeInfo>(element, elementContext) {

    override fun getFamilyName(): String = familyName

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: CallableReturnTypeUpdaterUtils.TypeInfo,
        updater: ModPsiUpdater,
    ) {
        convertPropertyInitializerToGetter(actionContext.project, element, elementContext, updater)
    }
}
