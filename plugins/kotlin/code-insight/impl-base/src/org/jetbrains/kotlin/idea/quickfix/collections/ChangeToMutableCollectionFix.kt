// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.collections

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.MutableCollectionsConversionUtils
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class ChangeToMutableCollectionFix(
    element: KtCallableDeclaration,
    elementContext: ClassId,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtCallableDeclaration, ClassId>(element, elementContext) {

    override fun isElementApplicable(
        element: KtCallableDeclaration,
        context: ActionContext,
    ): Boolean = super.isElementApplicable(element, context)
            && MutableCollectionsConversionUtils.defaultValue(element) != null

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.change.to.mutable.type.family")

    override fun getPresentation(
        context: ActionContext,
        element: KtCallableDeclaration,
    ): Presentation {
        val className = getElementContext(context, element)
            .shortClassName
        val name = KotlinBundle.message(
            "fix.change.to.mutable.type.text",
            "Mutable${className.asString()}",
        )
        return Presentation.of(name)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallableDeclaration,
        elementContext: ClassId,
        updater: ModPsiUpdater,
    ) {
        MutableCollectionsConversionUtils.convertDeclarationTypeToMutable(
            declaration = element,
            immutableCollectionClassId = elementContext,
        )
        updater.moveCaretTo(element.endOffset)
    }
}