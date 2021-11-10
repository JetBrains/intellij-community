// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.ArgumentPositionData
import org.jetbrains.kotlin.idea.core.ExpectedInfo
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.calls.callUtil.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

object NamedArgumentCompletion {
    fun isOnlyNamedArgumentExpected(nameExpression: KtSimpleNameExpression, resolutionFacade: ResolutionFacade): Boolean {
        val thisArgument = nameExpression.parent as? KtValueArgument ?: return false

        if (thisArgument.isNamed()) return false
        val resolvedCall = thisArgument.getStrictParentOfType<KtCallElement>()?.resolveToCall(resolutionFacade) ?: return false

        return !thisArgument.canBeUsedWithoutNameInCall(resolvedCall)
    }

    fun complete(collector: LookupElementsCollector, expectedInfos: Collection<ExpectedInfo>, callType: CallType<*>) {
        if (callType != CallType.DEFAULT) return

        val nameToParameterType = HashMap<Name, MutableSet<KotlinType>>()
        for (expectedInfo in expectedInfos) {
            val argumentData = expectedInfo.additionalData as? ArgumentPositionData.Positional ?: continue
            for (parameter in argumentData.namedArgumentCandidates) {
                nameToParameterType.getOrPut(parameter.name) { HashSet() }.add(parameter.type)
            }
        }

        for ((name, types) in nameToParameterType) {
            val typeText = types.singleOrNull()?.let { BasicLookupElementFactory.SHORT_NAMES_RENDERER.renderType(it) } ?: "..."
            val nameString = name.asString()
            val lookupElement = LookupElementBuilder.create("$nameString =")
                .withPresentableText("$nameString =")
                .withTailText(" $typeText")
                .withIcon(KotlinIcons.PARAMETER)
                .withInsertHandler(NamedArgumentInsertHandler(name))
            lookupElement.putUserData(SmartCompletionInBasicWeigher.NAMED_ARGUMENT_KEY, Unit)
            collector.addElement(lookupElement)
        }
    }

    private class NamedArgumentInsertHandler(private val parameterName: Name) : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val editor = context.editor

            val (textAfterCompletionArea, doNeedTrailingSpace) = context.file.findElementAt(context.tailOffset).let { psi ->
                psi?.siblings()?.firstOrNull { it !is PsiWhiteSpace }?.text to (psi !is PsiWhiteSpace)
            }

            var text: String
            var caretOffset: Int
            if (textAfterCompletionArea == "=") {
                // User tries to manually rename existing named argument. We shouldn't add trailing `=` in such case
                text = parameterName.render()
                caretOffset = text.length
            } else {
                // For complicated cases let's try to normalize the document firstly in order to avoid parsing errors due to incomplete code
                editor.document.replaceString(context.startOffset, context.tailOffset, "")
                PsiDocumentManager.getInstance(context.project).commitDocument(editor.document)

                val nextArgument = context.file.findElementAt(context.startOffset)?.siblings()
                    ?.firstOrNull { it !is PsiWhiteSpace }?.parentsWithSelf?.takeWhile { it !is KtValueArgumentList }
                    ?.firstIsInstanceOrNull<KtValueArgument>()

                if (nextArgument?.isNamed() == true) {
                    if (doNeedTrailingSpace) {
                        text = "${parameterName.render()} = , "
                        caretOffset = text.length - 2
                    } else {
                        text = "${parameterName.render()} = ,"
                        caretOffset = text.length - 1
                    }
                } else {
                    text = "${parameterName.render()} = "
                    caretOffset = text.length
                }
            }

            if (context.file.findElementAt(context.startOffset - 1)?.let { it !is PsiWhiteSpace && it.text != "(" } == true) {
                text = " $text"
                caretOffset++
            }

            editor.document.replaceString(context.startOffset, context.tailOffset, text)
            editor.caretModel.moveToOffset(context.startOffset + caretOffset)
        }
    }
}

/**
 * Checks whether argument in the [resolvedCall] can be used without its name (as positional argument).
 */
fun KtValueArgument.canBeUsedWithoutNameInCall(resolvedCall: ResolvedCall<out CallableDescriptor>): Boolean {
    if (resolvedCall.resultingDescriptor.valueParameters.isEmpty()) return true

    val argumentsThatCanBeUsedWithoutName = collectAllArgumentsThatCanBeUsedWithoutName(resolvedCall).map { it.argument }
    if (argumentsThatCanBeUsedWithoutName.isEmpty() || argumentsThatCanBeUsedWithoutName.none { it == this }) return false

    val argumentsBeforeThis = argumentsThatCanBeUsedWithoutName.takeWhile { it != this }
    return if (languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)) {
        argumentsBeforeThis.none { it.isNamed() && !it.placedOnItsOwnPositionInCall(resolvedCall) }
    } else {
        argumentsBeforeThis.none { it.isNamed() }
    }
}

data class ArgumentThatCanBeUsedWithoutName(val argument: KtValueArgument, val parameter: ValueParameterDescriptor)

fun collectAllArgumentsThatCanBeUsedWithoutName(
    resolvedCall: ResolvedCall<out CallableDescriptor>,
): List<ArgumentThatCanBeUsedWithoutName> {
    val arguments = resolvedCall.call.valueArguments.filterIsInstance<KtValueArgument>()
    val argumentAndParameters = arguments.mapNotNull { argument ->
        val parameter = resolvedCall.getParameterForArgument(argument) ?: return@mapNotNull null
        argument to parameter
    }.sortedBy { (_, parameter) -> parameter.index }
    if (arguments.size != argumentAndParameters.size) return emptyList()

    val firstVarargIndex = argumentAndParameters.indexOfFirst { (_, parameter) -> parameter.isVararg }
    return argumentAndParameters.mapIndexedNotNull { index, (argument, parameter) ->
        val parameterIndex = parameter.index
        val isAfterFirstVararg = firstVarargIndex != -1 && index > firstVarargIndex
        val isFirstVararg = index == firstVarargIndex
        if (index != parameterIndex ||
            isAfterFirstVararg ||
            isFirstVararg && argumentAndParameters.drop(index + 1).any { (argument, _) -> !argument.isNamed() }
        ) {
            null
        } else {
            ArgumentThatCanBeUsedWithoutName(argument, parameter)
        }
    }
}

/**
 * Checks whether argument in the [resolvedCall] is on the same position as it listed in the callable definition.
 *
 * It is always true for the positional arguments, but may be untrue for the named arguments.
 *
 * ```
 * fun foo(a: Int, b: Int, c: Int, d: Int) {}
 *
 * foo(
 *     10,      // true
 *     b = 10,  // true, possible since Kotlin 1.4 with `MixedNamedArgumentsInTheirOwnPosition` feature
 *     d = 30,  // false, 3 vs 4
 *     c = 40   // false, 4 vs 3
 * )
 * ```
 */
fun ValueArgument.placedOnItsOwnPositionInCall(resolvedCall: ResolvedCall<out CallableDescriptor>): Boolean {
    return resolvedCall.getParameterForArgument(this)?.index == resolvedCall.call.valueArguments.indexOf(this)
}
