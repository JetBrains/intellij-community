// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenOptionsForIde
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

context(_: KaSession)
internal fun collectPossibleCompanionReferenceShortenings(
    file: KtFile,
    selection: TextRange,
    shortenOptions: ShortenOptionsForIde,
): List<KtSimpleNameExpression> {
    if (!shortenOptions.removeExplicitCompanionReferences) return emptyList()

    return file.descendantsOfType<KtSimpleNameExpression>()
        .filter { it.textRange.intersects(selection) }
        .filter { it.isCompanionReferenceToShorten() }
        .toList()
}

context(_: KaSession)
internal fun collectPossibleCompanionReferenceShorteningsInElement(
    element: KtElement,
    shortenOptions: ShortenOptionsForIde,
): List<KtSimpleNameExpression> {
    if (!shortenOptions.removeExplicitCompanionReferences) return emptyList()

    return element.descendantsOfType<KtSimpleNameExpression>()
        .filter { it.isCompanionReferenceToShorten() }
        .toList()
}

context(_: KaSession)
private fun KtSimpleNameExpression.isCompanionReferenceToShorten(): Boolean {
    val candidate = this

    if (candidate.getReceiverExpression() == null) {
        // we filter out non-qualified companion references here (like `Companion.foo()`),
        // because regular reference shortener can take care of them on its own
        return false
    }

    return candidate.canBeRedundantCompanionReference() && candidate.isRedundantCompanionReference()
}

/**
 * Checks if [this] reference can **potentially** point to a redundant companion reference.
 *
 * Does not do any actual resolve, for that see [isRedundantCompanionReference].
 */
@ApiStatus.Internal
fun KtSimpleNameExpression.canBeRedundantCompanionReference(): Boolean {
    val element = this

    val parent = element.parent as? KtDotQualifiedExpression ?: return false
    if (parent.getStrictParentOfType<KtImportDirective>() != null) return false

    /**
     * See comment about two levels of parents in [isRedundantCompanionReference]
     */
    val grandParent = parent.parent as? KtElement
    val selectorExpression = parent.selectorExpression
    if (element == selectorExpression && grandParent !is KtDotQualifiedExpression) return false
    return element == selectorExpression || element.text != (selectorExpression as? KtNameReferenceExpression)?.text
}

/**
 * Checks if [this] reference is **actually** a redundant companion reference.
 *
 * For that, it is required to do resolve to ensure that the semantics of the code do not change.
 */
context(_: KaSession)
@ApiStatus.Internal
fun KtSimpleNameExpression.isRedundantCompanionReference(): Boolean {
    val parent = this.parent as? KtDotQualifiedExpression ?: return false

    val referenceName = this.text

    val symbol = this.mainReference.resolveToSymbol()
    val objectDeclaration =
        if (symbol is KaNamedClassSymbol && symbol.classKind == KaClassKind.COMPANION_OBJECT) {
            // Try to get the PSI for the companion object
            symbol.psi as? KtObjectDeclaration
        } else {
            null
        } ?: return false

    if (referenceName != objectDeclaration.name) return false

    /**
     * We need two levels of parents here because we need to reconstruct
     * expressions like `Foo.Companion.bar()` into `Foo.bar()`.
     * [grandParent] is responsible for the whole
     * `Foo.Companion.bar()` expression in this case.
     */
    val grandParent = parent.parent as? KtElement
    val selectorExpression = parent.selectorExpression

    val (oldTargetExpression, simplifiedText) = if (grandParent is KtDotQualifiedExpression && this == selectorExpression) {
        // Case for `Foo.Companion.bar()` -> `Foo.bar()` transformation
        grandParent.selectorExpression to (parent.receiverExpression.text + "." + grandParent.selectorExpression?.text)
    } else {
        // Case for `Companion.bar()` -> `bar()` transformation
        parent.selectorExpression to parent.selectorExpression!!.text
    }

    val oldTarget = oldTargetExpression?.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.symbol?.psi ?: return false
    val fragment = KtPsiFactory(this.project).createExpressionCodeFragment(
        simplifiedText,
        this
    )
    val q = fragment.getContentElement() ?: return false
    return oldTarget == analyze(q) {
        val partiallyAppliedSymbol = q.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
        partiallyAppliedSymbol?.symbol?.psi
    }
}

@ApiStatus.Internal
fun KtSimpleNameExpression.deleteReferenceFromQualifiedExpression() {
    val parent = this.parent as? KtDotQualifiedExpression ?: return
    val selector = parent.selectorExpression ?: return
    val receiver = parent.receiverExpression
    if (this == receiver) parent.replace(selector) else parent.replace(receiver)
}
