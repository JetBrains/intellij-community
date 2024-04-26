// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.jetbrains.kotlin.utils.addIfNotNull

@Suppress("DEPRECATION")
@Deprecated("Use 'org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester' instead")
abstract class AbstractKotlinNameSuggester {
    fun suggestNamesByFqName(
        fqName: FqName,
        ignoreCompanion: Boolean = true,
        validator: (String) -> Boolean = { true },
        defaultName: () -> String? = { null }
    ): Collection<String> {
        val result = LinkedHashSet<String>()

        var name = ""
        fqName.asString().split('.').asReversed().forEach {
            if (ignoreCompanion && it == "Companion") return@forEach
            name = name.withPrefix(it)
            result.addName(name, validator)
        }

        if (result.isEmpty()) {
            result.addName(defaultName(), validator)
        }

        return result
    }

    /**
     * Validates name, and slightly improves it by adding number to name in case of conflicts
     * @param name to check it in scope
     * @return name or nameI, where I is number
     */
    @Deprecated(
        "Use 'org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.suggestNameByName' instead",
        ReplaceWith(
            "KotlinNameSuggester.suggestNameByName(name, validator)",
            "org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester"
        )
    )
    fun suggestNameByName(name: String, validator: (String) -> Boolean): String =
        KotlinNameSuggester.suggestNameByName(name, validator)

    fun suggestNamesForTypeParameters(count: Int, validator: (String) -> Boolean): List<String> {
        val result = ArrayList<String>()
        for (i in 0 until count) {
            result.add(suggestNameByMultipleNames(COMMON_TYPE_PARAMETER_NAMES, validator))
        }
        return result
    }

    fun suggestTypeAliasNameByPsi(typeElement: KtTypeElement, validator: (String) -> Boolean): String {
        fun KtTypeElement.render(): String {
            return when (this) {
                is KtNullableType -> "Nullable${innerType?.render() ?: ""}"
                is KtFunctionType -> {
                    val arguments = listOfNotNull(receiverTypeReference) + parameters.mapNotNull { it.typeReference }
                    val argText = arguments.joinToString(separator = "") { it.typeElement?.render() ?: "" }
                    val returnText = returnTypeReference?.typeElement?.render() ?: "Unit"
                    "${argText}To$returnText"
                }
                is KtUserType -> {
                    val argText = typeArguments.joinToString(separator = "") { it.typeReference?.typeElement?.render() ?: "" }
                    "$argText${referenceExpression?.text ?: ""}"
                }
                else -> text.capitalizeAsciiOnly()
            }
        }

        return suggestNameByName(typeElement.render(), validator)
    }

    /**
     * Validates name using set of variants which are tried in succession (and extended with suffixes if necessary)
     * For example, when given sequence of a, b, c possible names are tried out in the following order: a, b, c, a1, b1, c1, a2, b2, c2, ...
     * @param names to check it in scope
     * @return name or nameI, where name is one of variants and I is a number
     */
    fun suggestNameByMultipleNames(names: Collection<String>, validator: (String) -> Boolean): String {
        var i = 0
        while (true) {
            for (name in names) {
                val candidate = if (i > 0) name + i else name
                if (validator(candidate)) return candidate
            }
            i++
        }
    }

    protected fun MutableCollection<String>.addCamelNames(
        name: String,
        validator: (String) -> Boolean,
        mustStartWithLowerCase: Boolean = true
    ) {
        addAll(KotlinNameSuggester.getCamelNames(name, validator, mustStartWithLowerCase))
    }

    protected fun MutableCollection<String>.addNamesByExpressionPSI(expression: KtExpression?, validator: (String) -> Boolean) {
        addAll(KotlinNameSuggester.suggestNamesByExpressionPSI(expression, validator))
    }

    protected fun MutableCollection<String>.addName(name: String?, validator: (String) -> Boolean) {
        addIfNotNull(KotlinNameSuggester.suggestNameByValidIdentifierName(name, validator, false))
    }

    private fun String.withPrefix(prefix: String): String {
        if (isEmpty()) return prefix
        val c = this[0]
        return (if (c in 'a'..'z') prefix.decapitalizeAsciiOnly()
        else prefix.capitalizeAsciiOnly()) + capitalizeAsciiOnly()
    }

    companion object {
        private val COMMON_TYPE_PARAMETER_NAMES = listOf("T", "U", "V", "W", "X", "Y", "Z")
    }
}