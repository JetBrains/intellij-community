// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.K2MoveToClassDialog
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.TargetClassCandidateParameter
import org.jetbrains.kotlin.idea.k2.refactoring.move.ui.findTargetClassCandidates
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration

internal class MoveFunctionToClassIntention : SelfTargetingRangeIntention<KtNamedFunction>(
    elementType = KtNamedFunction::class.java,
    textGetter = KotlinBundle.messagePointer("move.to.class.ellipsis")
) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("move.to.class.ellipsis")
    override fun startInWriteAction(): Boolean = false

    override fun applyTo(element: KtNamedFunction, editor: Editor?) {
        var candidates: List<TargetClassCandidateParameter>? = null

        runWithModalProgressBlocking(element.project, KotlinBundle.message("collecting.expressions.for.target.class")) {
            readAction {
                candidates = findTargetClassCandidates(element)
            }
        }
        if (candidates == null) return

        K2MoveToClassDialog(
            project = element.project,
            declaration = element,
            candidates = candidates,
        ).show()
    }

    override fun applicabilityRange(element: KtNamedFunction): TextRange? {
        if (!isApplicable(element)) return null
        return element.nameIdentifier?.textRange
    }

    private fun isApplicable(element: KtNamedFunction): Boolean {
        if (!Registry.`is`("kotlin.move.enable.move.to.class.intention")) return false
        if (element.modifierList?.getModifier(inapplicableModifiersTokenSet) != null) return false
        if (element.isExpectDeclaration() || element.isEffectivelyActual()) return false
        if ((element.containingClassOrObject as? KtClass)?.isInterface() == true) return false
        return true
    }

    private val inapplicableModifiersTokenSet = TokenSet.create(
        KtTokens.ABSTRACT_KEYWORD,
        KtTokens.OPEN_KEYWORD,
        KtTokens.OVERRIDE_KEYWORD,
    )
}
