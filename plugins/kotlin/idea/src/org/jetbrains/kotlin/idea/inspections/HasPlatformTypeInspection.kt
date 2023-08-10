// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
import org.jetbrains.kotlin.idea.intentions.isFlexibleRecursive
import org.jetbrains.kotlin.idea.quickfix.getAddExclExclCallFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.types.TypeUtils

class HasPlatformTypeInspection(
    @JvmField var publicAPIOnly: Boolean = true,
    @JvmField var reportPlatformArguments: Boolean = false
) : AbstractImplicitTypeInspection(
    { element, inspection ->
        with(inspection as HasPlatformTypeInspection) {
            SpecifyTypeExplicitlyIntention.dangerousFlexibleTypeOrNull(
                element,
                this.publicAPIOnly,
                this.reportPlatformArguments
            ) != null
        }
    }
) {

    override val problemText = KotlinBundle.message(
        "declaration.has.type.inferred.from.a.platform.call.which.can.lead.to.unchecked.nullability.issues"
    )

    override fun additionalFixes(element: KtCallableDeclaration): List<LocalQuickFix>? {
        val type = SpecifyTypeExplicitlyIntention.dangerousFlexibleTypeOrNull(
            element, publicAPIOnly, reportPlatformArguments
        ) ?: return null

        if (TypeUtils.isNullableType(type)) {
            val expression = element.node.findChildByType(KtTokens.EQ)?.psi?.getNextSiblingIgnoringWhitespaceAndComments()
            if (expression != null &&
                (!reportPlatformArguments || !TypeUtils.makeNotNullable(type).isFlexibleRecursive())
            ) {
                return listOfNotNull(getAddExclExclCallFix(expression)?.let { IntentionWrapper(it, element.containingFile) })
            }
        }

        return null
    }

  override fun getOptionsPane() = pane(
    checkbox("publicAPIOnly", KotlinBundle.message("apply.only.to.public.or.protected.members")),
    checkbox("reportPlatformArguments", KotlinBundle.message("report.for.types.with.platform.arguments")))
}