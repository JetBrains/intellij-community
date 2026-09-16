// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiver
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.isSubClassOf
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.approximateToDenotableSupertypeOrSelf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.Variance

internal object ReceiverShadowedByContextParameterFactory {
    val addReceiverFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReceiverShadowedByContextParameter ->
        val chain = diagnostic.psi
            .parents(withSelf = true)
            .takeWhile { it is KtCallableReferenceExpression || it is KtNameReferenceExpression || it is KtCallExpression || it is KtQualifiedExpression }
            .toList()
        // Rewriting the enclosing qualified chain
        // would attach the receiver as a dot-qualifier (`f.::bar.invoke()`), which does not
        // parse. For calls, the whole chain is the right unit: `bar().baz()` -> `f.bar().baz()`.
        val expression = (chain.firstOrNull { it is KtCallableReferenceExpression } ?: chain.lastOrNull())
                as? KtExpression
            ?: return@ModCommandBased emptyList()

        buildList {
            val calleeSymbol = diagnostic.calleeSymbol as? KaCallableSymbol
            if (calleeSymbol != null) {
                // Offer the `this` fix only when the shadowed receiver can unambiguous textual form of the shadowed receiver (this or this@label);
                // otherwise fall back to the context-parameter fixes below.
                thisTextForShadowedReceiver(expression, calleeSymbol, diagnostic.isDispatchOfMemberExtension)
                    ?.let { thisText ->
                        add(AddExplicitReceiverFix(expression, diagnostic.isDispatchOfMemberExtension, thisText))
                    }
            }
            for (symbol in diagnostic.contextParameterSymbols) {
                if (symbol is KaContextParameterSymbol) {
                    val nameOrContextOfCall = if (symbol.name.isSpecial) {
                        val approximatedType = symbol.returnType.approximateToDenotableSupertypeOrSelf(allowLocalDenotableTypes = true)
                        val renderedType = approximatedType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
                        "contextOf<$renderedType>()"
                    } else {
                        symbol.name.identifier
                    }
                    add(AddExplicitReceiverFix(expression, diagnostic.isDispatchOfMemberExtension, nameOrContextOfCall))
                }
            }
        }
    }
}

/**
 * Renders the shadowed receiver as a `this` expression: plain `this` when the shadowed receiver
 * is the innermost implicit receiver, `this@label` otherwise, or null when the receiver cannot
 * be named — in which case the `this` fix must not be offered.
 */
context(_: KaSession)
private fun thisTextForShadowedReceiver(
    position: KtExpression,
    callee: KaCallableSymbol,
    isDispatchOfMemberExtension: Boolean,
): String? {
    val requiredClass = if (isDispatchOfMemberExtension) {
        callee.containingDeclaration as? KaClassSymbol                       // dispatch slot of a member extension
    } else {
        (callee.receiverParameter?.returnType as? KaClassType)?.symbol as? KaClassSymbol  // extension slot
            ?: callee.containingDeclaration as? KaClassSymbol                // plain member
    } ?: return null

    val implicitReceivers = position.containingKtFile.scopeContext(position).implicitReceivers
    // innermost first; the shadowed receiver is the closest one the call would have used
    val shadowed = implicitReceivers.firstOrNull { receiver ->
        val receiverClass = (receiver.type as? KaClassType)?.symbol as? KaClassSymbol
        receiverClass != null && (receiverClass == requiredClass || receiverClass.isSubClassOf(requiredClass))
    } ?: return null

    if (shadowed == implicitReceivers.first()) return "this"
    val label = shadowed.thisLabelName(position) ?: return null   // can't name it -> don't offer a broken fix
    return "this@$label"
}

private fun KaImplicitReceiver.thisLabelName(position: KtElement): String? {
    val owner = ownerSymbol
    val ownerPsi = owner.psi ?: return null
    val label = when (owner) {
        is KaClassSymbol -> owner.name?.identifier          // this@Foo
        is KaNamedFunctionSymbol -> owner.name.identifier   // this@test (extension receiver)
        is KaAnonymousFunctionSymbol ->                     // lambda receiver
            (ownerPsi as? KtFunctionLiteral)?.label()       // explicit label@ { } or this@with, this@apply, ...
        else -> null                                        // anonymous objects etc. — no safe label
    } ?: return null

    // `this@label` resolves to the closest declaration with that label; if any function literal
    // between the position and the shadowed receiver's owner carries the same label, our label
    // would bind to the wrong receiver — don't offer the fix.
    val capturedByCloserLambda = position.parents(withSelf = false)
        .takeWhile { it != ownerPsi }
        .filterIsInstance<KtFunctionLiteral>()
        .any { it.label() == label }
    return label.takeUnless { capturedByCloserLambda }
}

private fun KtFunctionLiteral.label(): String? {
    val lambda = parent as? KtLambdaExpression ?: return null
    return (lambda.parent as? KtLabeledExpression)?.getLabelName()
        ?: lambda.implicitCalleeLabel()
}

/** The implicit label of a lambda is the name of the function it is passed to. */
private fun KtLambdaExpression.implicitCalleeLabel(): String? {
    val call = getStrictParentOfType<KtCallExpression>() ?: return null
    val isArgumentOfCall = call.lambdaArguments.any { it.getLambdaExpression() == this } ||
            call.valueArguments.any { it.getArgumentExpression() == this }
    if (!isArgumentOfCall) return null
    return (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
}

/**
 * Rewrites the shadowed usage with an explicit receiver ([receiverText] is `this`, `this@label`,
 * a context parameter name, or a `contextOf<T>()` call):
 * - callable reference: `::bar` -> `<receiver>::bar`
 * - member extension call: `bar()` -> `with(<receiver>) { bar() }`
 *   (the dispatch receiver of a member extension cannot be written explicitly)
 * - other calls: `bar()` -> `<receiver>.bar()`
 *
 * The member-extension case cannot occur for callable references: the compiler rejects them
 * with EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED before shadowing analysis.
 */
private class AddExplicitReceiverFix(
    expression: KtExpression,
    private val isDispatchOfMemberExtension: Boolean,
    private val receiverText: String,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtExpression>(expression) {

    override fun invoke(
        context: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {
        val factory = KtPsiFactory(context.project)
        val newText = when {
            element is KtCallableReferenceExpression -> "$receiverText${element.text}"
            isDispatchOfMemberExtension -> "with($receiverText) { ${element.text} }"
            else -> "$receiverText.${element.text}"
        }
        element.replace(factory.createExpression(newText))
    }

    override fun getActionPresentation(context: ActionContext, element: KtExpression): Presentation {
        return Presentation.of(
            KotlinBundle.message(
                if (isDispatchOfMemberExtension) {
                    "fix.receiver.shadowed.by.context.add.explicit.receiver.surround.with"
                } else {
                    "fix.receiver.shadowed.by.context.add.explicit.receiver.use.receiver"
                },
                receiverText
            )
        )
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.receiver.shadowed.by.context.add.explicit.receiver.family")
}
