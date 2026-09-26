package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupObject
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.UserDataProperty

/**
 * A weigher responsible for strongly preferring named arguments over other suggestions whenever
 * - named arguments are already being used in the current argument list
 * - the argument list spans multiple lines and makes use of named arguments highly likely
 *
 * Further, whenever named arguments are being used, we prefer named arguments with lower index, as these
 * are more likely to be used by the user next.
 */
internal object PreferNamedArgumentCompletionWeigher: KotlinLookupElementWeigher("kotlin.preferNamedArgumentCompletion"), KotlinSectionContextWeigher {

    private sealed class Weight : Comparable<Weight> {
        override fun compareTo(other: Weight): Int {
            if (this is Default && other is PreferNamedArgument) return 1
            if (this is PreferNamedArgument && other is Default) return -1
            if (this is PreferNamedArgument && other is PreferNamedArgument) return argumentIndex.compareTo(other.argumentIndex)
            return 0
        }

        data class PreferNamedArgument(val argumentIndex: Int): Weight()
        data object Default : Weight()
    }

    private var LookupElement.preferNamedArgument: Weight? by UserDataProperty(Key("PREFER_NAMED_ARGUMENT_WEIGHT"))

    override fun weigh(element: LookupElement): Comparable<*> = element.preferNamedArgument ?: Weight.Default

    context(_: KaSession, sectionContext: K2CompletionSectionContext<*>)
    override fun addWeight(lookupElement: LookupElement, symbolWithOrigin: KtSymbolWithOrigin<*>?) {
        val lookupObject = lookupElement.`object` as? NamedArgumentLookupObject ?: return

        val argument = sectionContext.positionContext.position.parent.parent as? KtValueArgument ?: return
        val argumentList = argument.parent as? KtValueArgumentList ?: return

        // We only strongly prefer named arguments if named arguments are already being used
        // or are highly likely to be used in the case of a multi-line argument list.
        if (!argumentList.isMultiLine() && argumentList.arguments.none { it.isNamed() }) return

        lookupElement.preferNamedArgument = Weight.PreferNamedArgument(lookupObject.argumentIndex)
    }
}