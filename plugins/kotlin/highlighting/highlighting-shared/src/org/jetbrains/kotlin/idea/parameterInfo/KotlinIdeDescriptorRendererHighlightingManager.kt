// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo


interface KotlinIdeDescriptorRendererHighlightingManager<TAttributes : KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes> {

    fun StringBuilder.appendHighlighted(value: String, attributes: TAttributes)

    fun StringBuilder.appendCodeSnippetHighlightedByLexer(codeSnippet: String)

    val asError: TAttributes

    val asInfo: TAttributes

    val asDot: TAttributes

    val asComma: TAttributes

    val asColon: TAttributes

    val asDoubleColon: TAttributes

    val asParentheses: TAttributes

    val asArrow: TAttributes

    val asBrackets: TAttributes

    val asBraces: TAttributes

    val asOperationSign: TAttributes

    val asNonNullAssertion: TAttributes

    val asNullityMarker: TAttributes

    val asKeyword: TAttributes

    val asVal: TAttributes

    val asVar: TAttributes

    val asAnnotationName: TAttributes

    val asAnnotationAttributeName: TAttributes

    val asClassName: TAttributes

    val asPackageName: TAttributes

    val asObjectName: TAttributes

    val asInstanceProperty: TAttributes

    val asTypeAlias: TAttributes

    val asParameter: TAttributes

    val asTypeParameterName: TAttributes

    val asLocalVarOrVal: TAttributes

    val asFunDeclaration: TAttributes

    val asFunCall: TAttributes


    companion object {

        interface Attributes

        fun <TAttributes : Attributes> KotlinIdeDescriptorRendererHighlightingManager<TAttributes>.eraseTypeParameter():
                KotlinIdeDescriptorRendererHighlightingManager<Attributes> {
            @Suppress("UNCHECKED_CAST")
            return this as KotlinIdeDescriptorRendererHighlightingManager<Attributes>
        }

        private val EMPTY_ATTRIBUTES = object : Attributes {}

        val NO_HIGHLIGHTING = object : KotlinIdeDescriptorRendererHighlightingManager<Attributes> {
            override fun StringBuilder.appendHighlighted(value: String, attributes: Attributes) {
                append(value)
            }

            override fun StringBuilder.appendCodeSnippetHighlightedByLexer(codeSnippet: String) {
                append(codeSnippet)
            }

            override val asError = EMPTY_ATTRIBUTES
            override val asInfo = EMPTY_ATTRIBUTES
            override val asDot = EMPTY_ATTRIBUTES
            override val asComma = EMPTY_ATTRIBUTES
            override val asColon = EMPTY_ATTRIBUTES
            override val asDoubleColon = EMPTY_ATTRIBUTES
            override val asParentheses = EMPTY_ATTRIBUTES
            override val asArrow = EMPTY_ATTRIBUTES
            override val asBrackets = EMPTY_ATTRIBUTES
            override val asBraces = EMPTY_ATTRIBUTES
            override val asOperationSign = EMPTY_ATTRIBUTES
            override val asNonNullAssertion = EMPTY_ATTRIBUTES
            override val asNullityMarker = EMPTY_ATTRIBUTES
            override val asKeyword = EMPTY_ATTRIBUTES
            override val asVal = EMPTY_ATTRIBUTES
            override val asVar = EMPTY_ATTRIBUTES
            override val asAnnotationName = EMPTY_ATTRIBUTES
            override val asAnnotationAttributeName = EMPTY_ATTRIBUTES
            override val asClassName = EMPTY_ATTRIBUTES
            override val asPackageName = EMPTY_ATTRIBUTES
            override val asObjectName = EMPTY_ATTRIBUTES
            override val asInstanceProperty = EMPTY_ATTRIBUTES
            override val asTypeAlias = EMPTY_ATTRIBUTES
            override val asParameter = EMPTY_ATTRIBUTES
            override val asTypeParameterName = EMPTY_ATTRIBUTES
            override val asLocalVarOrVal = EMPTY_ATTRIBUTES
            override val asFunDeclaration = EMPTY_ATTRIBUTES
            override val asFunCall = EMPTY_ATTRIBUTES
        }
    }
}