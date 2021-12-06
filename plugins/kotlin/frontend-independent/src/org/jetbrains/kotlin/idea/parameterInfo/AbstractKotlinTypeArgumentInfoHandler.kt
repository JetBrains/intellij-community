/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset

abstract class AbstractKotlinTypeArgumentInfoHandler<TParameterOwner : Any, TParameter : Any, UpperBound : Any> :
    ParameterInfoHandlerWithTabActionSupport<KtTypeArgumentList, TParameterOwner, KtTypeProjection> {

    protected abstract fun fetchTypeParameters(descriptor: TParameterOwner): List<TParameter>
    protected abstract fun findParameterOwners(argumentList: KtTypeArgumentList): Collection<TParameterOwner>?

    override fun getActualParameterDelimiterType(): KtSingleValueToken = KtTokens.COMMA
    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.GT

    override fun getArgumentListClass() = KtTypeArgumentList::class.java

    override fun getActualParameters(o: KtTypeArgumentList) = o.arguments.toTypedArray()

    override fun getArgListStopSearchClasses() =
        setOf(KtNamedFunction::class.java, KtVariableDeclaration::class.java, KtClassOrObject::class.java)

    override fun showParameterInfo(element: KtTypeArgumentList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): KtTypeArgumentList? {
        val element = context.file.findElementAt(context.offset) ?: return null
        val argumentList = element.getParentOfType<KtTypeArgumentList>(true) ?: return null
        val argument = element.parents.takeWhile { it != argumentList }.lastOrNull() as? KtTypeProjection
        if (argument != null) {
            val arguments = getActualParameters(argumentList)
            val index = arguments.indexOf(element)
            context.setCurrentParameter(index)
            context.highlightedParameter = element
        }
        return argumentList
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): KtTypeArgumentList? {
        val file = context.file as? KtFile ?: return null

        val token = file.findElementAt(context.offset) ?: return null
        val argumentList = token.getParentOfType<KtTypeArgumentList>(true) ?: return null

        val parameterOwners = findParameterOwners(argumentList) ?: return null

        context.itemsToShow = parameterOwners.toTypedArray<Any>()
        return argumentList
    }

    override fun updateParameterInfo(argumentList: KtTypeArgumentList, context: UpdateParameterInfoContext) {
        if (context.parameterOwner !== argumentList) {
            context.removeHint()
        }

        val offset = context.offset
        val parameterIndex = argumentList.allChildren
            .takeWhile { it.startOffset < offset }
            .count { it.node.elementType == KtTokens.COMMA }
        context.setCurrentParameter(parameterIndex)
    }

    override fun updateUI(itemToShow: TParameterOwner, context: ParameterInfoUIContext) {
        if (!updateUIOrFail(itemToShow, context)) {
            context.isUIComponentEnabled = false
            return
        }
    }

    private fun updateUIOrFail(itemToShow: TParameterOwner, context: ParameterInfoUIContext): Boolean {
        val currentIndex = context.currentParameterIndex
        if (currentIndex < 0) return false // by some strange reason we are invoked with currentParameterIndex == -1 during initialization

        val parameters = fetchTypeParameters(itemToShow)

        val (text, currentParameterStart, currentParameterEnd) = buildPresentation(parameters, currentIndex)

        context.setupUIComponentPresentation(
            text, currentParameterStart, currentParameterEnd,
            false/*isDisabled*/, false/*strikeout*/, false/*isDisabledBeforeHighlight*/,
            context.defaultParameterColor
        )

        return true
    }

    abstract fun TParameter.isReified(): Boolean
    abstract fun TParameter.variance(): String
    abstract fun TParameter.name(): String
    abstract fun TParameter.upperBounds(): List<UpperBound>
    abstract fun UpperBound.isNullableAnyOrFlexibleAny(): Boolean
    abstract fun UpperBound.renderType(): String

    protected fun buildPresentation(
        parameters: List<TParameter>,
        currentIndex: Int
    ): Triple<String, Int, Int> {
        var currentParameterStart = -1
        var currentParameterEnd = -1

        val text = buildString {
            var needWhere = false
            for ((index, parameter) in parameters.withIndex()) {
                if (index > 0) append(", ")

                if (index == currentIndex) {
                    currentParameterStart = length
                }

                if (parameter.isReified()) {
                    append("reified ")
                }

                parameter.variance().let { if (it.isNotEmpty()) append("$it ") }

                append(parameter.name())

                val upperBounds = parameter.upperBounds()
                if (upperBounds.size == 1) {
                    val upperBound = upperBounds.single()
                    if (!upperBound.isNullableAnyOrFlexibleAny()) { // skip Any? or Any!
                        append(" : ").append(upperBound.renderType())
                    }
                } else if (upperBounds.size > 1) {
                    needWhere = true
                }

                if (index == currentIndex) {
                    currentParameterEnd = length
                }
            }

            if (needWhere) {
                append(" where ")

                var needComma = false
                for (parameter in parameters) {
                    val upperBounds = parameter.upperBounds()
                    if (upperBounds.size > 1) {
                        for (upperBound in upperBounds) {
                            if (needComma) append(", ")
                            needComma = true
                            append(parameter.name())
                            append(" : ")
                            append(upperBound.renderType())
                        }
                    }
                }
            }
        }
        return Triple(text, currentParameterStart, currentParameterEnd)
    }
}
