// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.util.PsiUtil
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.text.NameUtilCore
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.approximateToSuperPublicDenotableOrSelf
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isAnyType
import org.jetbrains.kotlin.analysis.api.components.isBooleanType
import org.jetbrains.kotlin.analysis.api.components.isByteType
import org.jetbrains.kotlin.analysis.api.components.isCharSequenceType
import org.jetbrains.kotlin.analysis.api.components.isCharType
import org.jetbrains.kotlin.analysis.api.components.isDoubleType
import org.jetbrains.kotlin.analysis.api.components.isFloatType
import org.jetbrains.kotlin.analysis.api.components.isFunctionType
import org.jetbrains.kotlin.analysis.api.components.isIntType
import org.jetbrains.kotlin.analysis.api.components.isLongType
import org.jetbrains.kotlin.analysis.api.components.isShortType
import org.jetbrains.kotlin.analysis.api.components.isStringType
import org.jetbrains.kotlin.analysis.api.components.isUByteType
import org.jetbrains.kotlin.analysis.api.components.isUIntType
import org.jetbrains.kotlin.analysis.api.components.isULongType
import org.jetbrains.kotlin.analysis.api.components.isUShortType
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames.FqNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester.Case.CAMEL
import org.jetbrains.kotlin.idea.base.psi.getCallElement
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getOutermostParenthesizerOrThis
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.util.capitalizeDecapitalize.*

@DslMarker
private annotation class NameSuggesterDsl

class KotlinNameSuggester(
    private val case: Case = CAMEL,
    private val escaping: EscapingRules = EscapingRules.DEFAULT,
    private val ignoreCompanionNames: Boolean = true
) {
    class EscapingRules(
        private val escapeKotlinHardKeywords: Boolean = true,
        private val escapeKotlinSoftKeywords: Boolean = false,
        private val escapeJavaHardKeywords: Boolean = false,
        private val escapeJavaSoftKeywords: Boolean = false,
        private val escaper: (String) -> List<String> = DEFAULT_ESCAPER
    ) {
        companion object {
            val DEFAULT_ESCAPER: (String) -> List<String> = { name: String ->
                when (name) {
                    "class" -> listOf("klass", "clazz")
                    "fun" -> listOf("function", "fn", "func", "f")
                    "null" -> listOf("nothing", "nil")
                    "this" -> listOf("self", "me", "owner")
                    "const" -> listOf("constant", "value")
                    "enum" -> listOf("enumeration")
                    "package" -> listOf("pkg")
                    else -> listOf("`$name`")
                }
            }

            val DEFAULT = EscapingRules()

            val NONE = EscapingRules(
                escapeKotlinHardKeywords = false,
                escapeKotlinSoftKeywords = false,
                escapeJavaHardKeywords = false,
                escapeJavaSoftKeywords = false,
                escaper = { listOf(it) }
            )
        }

        fun shouldEscape(name: String): Boolean {
            return (escapeKotlinHardKeywords && name in KOTLIN_HARD_KEYWORDS)
                    || (escapeKotlinSoftKeywords && name in KOTLIN_SOFT_KEYWORDS)
                    || (escapeJavaHardKeywords && PsiUtil.isKeyword(name, LanguageLevel.HIGHEST))
                    || (escapeJavaSoftKeywords && PsiUtil.isSoftKeyword(name, LanguageLevel.HIGHEST))
        }

        fun escape(name: String): List<String> = escaper(name)
    }

    enum class CaseTransformation(val processor: (String) -> String) {
        DEFAULT({ it }),
        UPPERCASE({ it.toUpperCaseAsciiOnly() }),
        LOWERCASE({ it.toLowerCaseAsciiOnly() })
    }

    enum class Case(val case: CaseTransformation, val separator: String?, val capitalizeFirst: Boolean, val capitalizeNext: Boolean) {
        PASCAL(CaseTransformation.DEFAULT, separator = null, capitalizeFirst = true, capitalizeNext = true), // FooBar
        CAMEL(CaseTransformation.DEFAULT, separator = null, capitalizeFirst = false, capitalizeNext = true), // fooBar
        SNAKE(CaseTransformation.LOWERCASE, separator = "_", capitalizeFirst = false, capitalizeNext = false), // foo_bar
        SCREAMING_SNAKE(CaseTransformation.UPPERCASE, separator = "_", capitalizeFirst = false, capitalizeNext = false), // FOO_BAR
        KEBAB(CaseTransformation.LOWERCASE, separator = "-", capitalizeFirst = false, capitalizeNext = false) // foo-bar
    }

    /**
     * Returns names based on a given class id.
     * Example: my/test/app/Foo.Bar.BazBoo -> {Boo, BazBoo, BarBazBoo, FooBarBazBoo}
     */
    fun suggestClassNames(classId: ClassId): Sequence<String> {
        return sequence {
            suspend fun SequenceScope<String>.registerChunks(chunks: List<String>, registerWholeName: Boolean) {
                for (startIndex in chunks.indices.reversed()) {
                    if (startIndex > 0 || registerWholeName) {
                        val slicedChunks = chunks.subList(startIndex, chunks.size)
                        registerCompoundName(slicedChunks)
                    }
                }
            }

            val shortName = classId.shortClassName.asStringStripSpecialMarkers()
            if (StringUtil.isJavaIdentifier(shortName)) {
                val shortNameChunks = NameUtilCore.nameToWords(shortName).asList()
                registerChunks(shortNameChunks, registerWholeName = false)
            }

            val nameChunks = classId.relativeClassName.pathSegments().map { it.asStringStripSpecialMarkers() }
            registerChunks(nameChunks, registerWholeName = true)

        }.ifEmpty {
            sequence {
                registerCompoundName(listOf("value"))
            }
        }
    }

    /**
     * Returns names based on the name of the value parameter, the expression PSI and the expression type,
     * validates them using [validator], and improves them by adding a numeric suffix in case of conflicts.
     * Examples:
     *  - `print(<selection>5</selection>)` -> {message, i, n}
     *  - `print(<selection>intArrayOf(5)</selection>)` -> {message, intArrayOf, ints}
     *  - `print(<selection>listOf(User("Mary"), User("John"))</selection>)` -> {message, listOf, users}
     */
    context(_: KaSession)
    fun suggestExpressionNames(expression: KtExpression, validator: (String) -> Boolean = { true }): Sequence<String> {
        return (suggestNamesByValueArgument(expression, validator) +
                suggestNameBySimpleExpression(expression, validator) +
                suggestNamesByExpressionPSI(expression, validator).filter { name ->
                    name.length >= MIN_LENGTH_OF_NAME_BASED_ON_EXPRESSION_PSI
                } +
                suggestNamesByType(expression, validator)).distinct()
    }

    /**
     * Returns a `Sequence` consisting of the name, based on a simple expression and validated
     * by the [validator], and improved by adding a numeric suffix in case of conflicts.
     * Examples:
     *  - `point.x` -> {x}
     *  - `getX()` -> {x}
     *  - `FooBar()` -> {fooBar}
     */
    private fun suggestNameBySimpleExpression(expression: KtExpression, validator: (String) -> Boolean): Sequence<String> {
        val simpleExpressionName = getSimpleExpressionName(expression) ?: return emptySequence()
        val s = cutAccessorPrefix(simpleExpressionName) ?: return emptySequence()
        return suggestNameByValidIdentifierName(s, validator)?.let { sequenceOf(it) } ?: emptySequence()
    }

    /**
     * Returns names based on the expression type, validates them using [validator], and improves them
     * by adding a numeric suffix in case of conflicts.
     * Examples:
     *  - `5` -> {int, i, n}
     *  - `intArrayOf(5)` -> {ints}
     *  - listOf(User("Mary"), User("John")) -> {users}
     */
    context(_: KaSession)
    private fun suggestNamesByType(expression: KtExpression, validator: (String) -> Boolean): Sequence<String> {
        val type = expression.expressionType ?: return emptySequence()
        return suggestTypeNames(type).map { name -> suggestNameByName(name, validator) }
    }

    /**
     * Returns a `Sequence` consisting of the name, based on the value parameter name and validated
     * by the [validator], and improved by adding a numeric suffix in case of conflicts
     * Examples:
     *  - `print(<selection>5</selection>)` -> {message}
     *  - `listOf(<selection>5</selection>)` -> {element}
     *  - `ints.filter <selection>{ it > 0 }</selection>` -> {predicate}
     */
    context(_: KaSession)
    private fun suggestNamesByValueArgument(expression: KtExpression, validator: (String) -> Boolean): Sequence<String> {
        val argumentExpression = expression.getOutermostParenthesizerOrThis()
        val valueArgument = argumentExpression.parent as? KtValueArgument ?: return emptySequence()
        val callElement = getCallElement(valueArgument) ?: return emptySequence()
        val resolvedCall = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return emptySequence()
        val parameter = resolvedCall.argumentMapping[valueArgument.getArgumentExpression()]?.symbol ?: return emptySequence()
        return suggestNameByValidIdentifierName(parameter.name.asString(), validator)?.let { sequenceOf(it) } ?: emptySequence()
    }

    /**
     * Returns names based on a given type.
     * Examples:
     *  - `Int` -> {int, i, n}
     *  - `IntArray` -> {ints}
     *  - `List<User>` -> {users}
     */
    context(_: KaSession)
    fun suggestTypeNames(type: KaType): Sequence<String> {
        return sequence {
            val presentableType = getPresentableType(type)

            val primitiveType = getPrimitiveType(presentableType)
            if (primitiveType != null) {
                PRIMITIVE_TYPE_NAMES.getValue(primitiveType).forEachIndexed { index, s ->
                    // skip first item for the primitives like `int`
                    if (index > 0) {
                        registerCompoundName(s)
                    }
                }
                return@sequence
            }

            if (presentableType.isCharSequenceType || presentableType.isStringType) {
                registerCompoundName("string")
                registerCompoundName("str")
                registerCompoundName("s")
                registerCompoundName("text")
                return@sequence
            }

            if (presentableType.isFunctionType) {
                registerCompoundName("function")
                registerCompoundName("fn")
                registerCompoundName("f")
                return@sequence
            }

            fun getClassId(type: KaType): ClassId = when (type) {
                is KaClassType -> type.classId
                is KaTypeParameterType -> ClassId(FqName.ROOT, FqName.topLevel(type.name), false)

                else -> ClassId(FqName.ROOT, FqName.topLevel(Name.identifier("Value")), false)
            }

            suspend fun SequenceScope<String>.registerClassNames(type: KaType, preprocessor: (String) -> String = { it }) {
                val classId = getClassId(type)

                KotlinNameSuggester(case, EscapingRules.NONE, ignoreCompanionNames)
                    .suggestClassNames(classId)
                    .map(preprocessor)
                    .forEach { registerCompoundName(it) }
            }

            // when the presentable iterable element type is `Any`, don't suggest `anies`
            val presentableElementType = getIterableElementType(presentableType)?.let { getPresentableType(it) }?.takeUnless { it.isAnyType }

            if (presentableElementType != null) {
                registerClassNames(presentableElementType) { Strings.pluralize(it) }
                return@sequence
            }

            val classId = getClassId(presentableType)
            if (!classId.isLocal && !classId.isNestedClass) {
                val fqName = classId.asSingleFqName().toUnsafe()

                val primitiveElementType = FqNames.arrayClassFqNameToPrimitiveType[fqName]
                if (primitiveElementType != null) {
                    val primitiveName = PRIMITIVE_TYPE_NAMES.getValue(primitiveElementType).first()
                    val chunk = Strings.pluralize(primitiveName)
                    registerCompoundName(chunk)
                    return@sequence
                }

                val specialNames = getSpecialNames(fqName)
                if (specialNames != null) {
                    specialNames.forEach { registerCompoundName(it) }
                    return@sequence
                }
            }

            registerClassNames(presentableType)
        }
    }

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getPresentableType(type: KaType): KaType = type.approximateToSuperPublicDenotableOrSelf(approximateLocalTypes = true)

    /**
     * Suggests type alias name for a given type element.
     * Examples:
     *  - `String` -> StringAlias
     *  - `Int?` -> NullableInt
     *  - `(String) -> Boolean` -> StringPredicate
     */
    context(_: KaSession)
    fun suggestTypeAliasName(type: KtTypeElement): String {
        var isExactMatch = true

        fun MutableList<String>.process(type: KtTypeElement?) {
            when (type) {
                is KtNullableType -> {
                    isExactMatch = false
                    add("nullable")
                    process(type.innerType)
                }
                is KtFunctionType -> {
                    isExactMatch = false
                    if (type.receiverTypeReference != null) {
                        process(type.receiverTypeReference?.typeElement)
                    }
                    type.parameters.forEach { process(it.typeReference?.typeElement) }
                    val returnType = type.returnTypeReference
                    if (returnType != null) {
                        if (returnType.type.isBooleanType) {
                            add("predicate")
                        } else {
                            add("to")
                            process(returnType.typeElement)
                        }
                    } else {
                        add("function")
                    }
                }
                is KtUserType -> {
                    val name = type.referenceExpression?.getReferencedName()
                    if (name != null) {
                        addIfNotNull(name)
                        if (type.typeArguments.isNotEmpty()) {
                            isExactMatch = false
                            add("of")
                            type.typeArguments.forEach { process(it.typeReference?.typeElement) }
                        }
                    } else {
                        isExactMatch = false
                        add("something")
                    }
                }
                else -> {
                    isExactMatch = false
                    add("nothing")
                }
            }
        }

        val chunks = buildList {
            process(type)
            if (isExactMatch) {
                add("alias")
            }
        }

        return chunks.let(::concat)
    }

    private fun getSpecialNames(fqName: FqNameUnsafe): List<String>? {
        return when (fqName) {
            FqNames.kCallable -> listOf("callable", "declaration")
            FqNames.kClass,
                JAVA_LANG_CLASS_FQ_NAME -> listOf("class", "declaration")
            FqNames.kPropertyFqName,
                FqNames.kProperty0,
                FqNames.kProperty1,
                FqNames.kProperty2,
                FqNames.kMutablePropertyFqName,
                FqNames.kMutableProperty0,
                FqNames.kMutableProperty1,
                FqNames.kMutableProperty2 -> listOf("property", "declaration")
            else -> null
        }
    }

    @NameSuggesterDsl
    private suspend fun SequenceScope<String>.registerCompoundName(chunk: String) {
        registerCompoundName(listOf(chunk))
    }

    @NameSuggesterDsl
    private suspend fun SequenceScope<String>.registerCompoundName(chunks: List<String>) {
        val combinedName = concat(chunks).takeIf { it.isNotEmpty() } ?: return
        val processedName = case.case.processor(combinedName)

        if (escaping.shouldEscape(processedName)) {
            for (escapedName in escaping.escape(processedName)) {
                yield(case.case.processor(escapedName))
            }
        } else {
            yield(processedName)
        }
    }

    private fun concat(names: List<String>): String {
        val builder = StringBuilder()

        for (name in names) {
            if ((ignoreCompanionNames && name == "Companion") || !StringUtil.isJavaIdentifier(name)) {
                continue
            }

            val isFirst = builder.isEmpty()

            if (!isFirst && case.separator != null) {
                builder.append(case.separator)
            }

            val processedName = when {
                isFirst && case.capitalizeFirst -> name.capitalizeAsciiOnly()
                !isFirst && case.capitalizeNext -> name.capitalizeAsciiOnly()
                else -> name.decapitalizeAsciiOnly()
            }

            builder.append(processedName)
        }

        return builder.toString()
    }

    companion object {
        fun getCamelNames(name: String, validator: (String) -> Boolean, startLowerCase: Boolean = true): Sequence<String> {
            val s = cutAccessorPrefix(name) ?: return emptySequence()

            var upperCaseLetterBefore = false
            return sequence {
                for (i in s.indices) {
                    val c = s[i]
                    val upperCaseLetter = Character.isUpperCase(c)

                    if (i == 0) {
                        suggestNameByValidIdentifierName(s, validator, startLowerCase)?.let { yield(it) }
                    } else {
                        if (upperCaseLetter && !upperCaseLetterBefore) {
                            val substring = s.substring(i)
                            suggestNameByValidIdentifierName(substring, validator, startLowerCase)?.let { yield(it) }
                        }
                    }

                    upperCaseLetterBefore = upperCaseLetter
                }
            }
        }

        private fun cutAccessorPrefix(name: String): String? {
            if (name === "" || !name.unquoteKotlinIdentifier().isIdentifier()) return null
            val s = extractIdentifiers(name)

            for (prefix in ACCESSOR_PREFIXES) {
                if (!s.startsWith(prefix)) continue

                val len = prefix.length
                if (len < s.length && Character.isUpperCase(s[len])) {
                    return s.substring(len)
                }
            }

            return s
        }

        private fun extractIdentifiers(s: String): String {
            return buildString {
                val lexer = KotlinLexer()
                lexer.start(s)
                while (lexer.tokenType != null) {
                    if (lexer.tokenType == KtTokens.IDENTIFIER) {
                        append(lexer.tokenText)
                    }
                    lexer.advance()
                }
            }
        }

        /**
         * Returns names based on an [expression] PSI, validates them using [validator], and improves them by
         * adding a numeric suffix in case of conflicts.
         * Examples:
         *  - `listOf(42)` -> {list, of}
         *  - `point.x` -> {x}
         *  - `collection.isEmpty()` -> {empty}
         */
        fun suggestNamesByExpressionPSI(expression: KtExpression?, validator: (String) -> Boolean): Sequence<String> {
            val simpleExpressionName = getSimpleExpressionName(expression) ?: return emptySequence()
            return getCamelNames(simpleExpressionName, validator)
        }

        private fun getSimpleExpressionName(expression: KtExpression?): String? {
            if (expression == null) return null
            return when (val deparenthesized = KtPsiUtil.safeDeparenthesize(expression)) {
                is KtSimpleNameExpression -> return deparenthesized.getReferencedName()
                is KtQualifiedExpression -> getSimpleExpressionName(deparenthesized.selectorExpression)
                is KtCallExpression -> getSimpleExpressionName(deparenthesized.calleeExpression)
                is KtPostfixExpression -> getSimpleExpressionName(deparenthesized.baseExpression)
                else -> null
            }
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
         * Decapitalizes the passed [name] if [mustStartWithLowerCase] is `true`, checks whether the result is a valid identifier,
         * validates it using [validator], and improves it by adding a numeric suffix in case of conflicts.
         */
        fun suggestNameByValidIdentifierName(
            name: String?,
            validator: (String) -> Boolean,
            mustStartWithLowerCase: Boolean = true
        ): String? {
            if (name == null) return null
            if (mustStartWithLowerCase) return suggestNameByValidIdentifierName(name.decapitalizeSmart(), validator, false)
            val correctedName = when {
                name.isIdentifier() -> name
                name == "class" -> "clazz"
                else -> return null
            }
            return suggestNameByName(correctedName, validator)
        }

        /**
         * Validates [name] and slightly improves it by adding a numeric suffix in case of conflicts.
         *
         * @param name to check in scope
         * @return [name] or nameI, where I is an integer
         */
        fun suggestNameByName(name: String, validator: (String) -> Boolean): String {
            if (validator(name)) return name
            var i = 1
            while (i <= MAX_NUMBER_OF_SUGGESTED_NAME_CHECKS && !validator(name + i)) {
                ++i
            }

            return name + i
        }

        /**
         * Returns a name sequence from a given name, appending numeric suffixes.
         * Example: foo -> [foo, foo2, foo3, ...]
         */
        fun enumerate(name: String): Sequence<String> {
            return enumerate(listOf(name))
        }

        /**
         * Returns a name sequence from given names, appending numeric suffixes.
         * Example: [foo, bar] -> [foo, bar, foo2, bar2, ...]
         */
        private fun enumerate(names: List<String>): Sequence<String> {
            return sequence {
                yieldAll(names)

                var numberSuffix = 2
                while (true) {
                    for (candidate in names) {
                        yield("$candidate$numberSuffix")
                    }
                    numberSuffix += 1
                }
            }
        }

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

        fun suggestNamesForTypeParameters(count: Int, validator: (String) -> Boolean): List<String> {
            val result = ArrayList<String>()
            for (i in 0 until count) {
                result.add(suggestNameByMultipleNames(TYPE_PARAMETER_NAMES, validator))
            }
            return result
        }

        val TYPE_PARAMETER_NAMES: List<String> = listOf(
            "T", "U", "V", "W", "X", "Y", "Z", "A", "B", "C", "D", "E",
            "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "R", "S"
        )

        private const val MAX_NUMBER_OF_SUGGESTED_NAME_CHECKS = 1000
        private const val MIN_LENGTH_OF_NAME_BASED_ON_EXPRESSION_PSI = 3
        private val ACCESSOR_PREFIXES = arrayOf("get", "is", "set")

        private val KOTLIN_HARD_KEYWORDS = KtTokens.KEYWORDS.types.filterIsInstance<KtKeywordToken>().map { it.value }
        private val KOTLIN_SOFT_KEYWORDS = KtTokens.SOFT_KEYWORDS.types.filterIsInstance<KtKeywordToken>().map { it.value }

        private val JAVA_LANG_CLASS_FQ_NAME = FqNameUnsafe("java.lang.Class")

        private val PRIMITIVE_TYPE_NAMES = mapOf(
            PrimitiveType.BOOLEAN to listOf("boolean", "bool", "b"),
            PrimitiveType.CHAR to listOf("char", "ch", "c"),
            PrimitiveType.BYTE to listOf("byte", "b"),
            PrimitiveType.SHORT to listOf("short", "sh", "s"),
            PrimitiveType.INT to listOf("int", "i", "n"),
            PrimitiveType.LONG to listOf("long", "lng", "l"),
            PrimitiveType.FLOAT to listOf("float", "f"),
            PrimitiveType.DOUBLE to listOf("double", "d")
        )
    }
}

context(_: KaSession)
private fun getPrimitiveType(type: KaType): PrimitiveType? {
    return when {
        type.isBooleanType -> PrimitiveType.BOOLEAN
        type.isCharType -> PrimitiveType.CHAR
        type.isByteType || type.isUByteType -> PrimitiveType.BYTE
        type.isShortType || type.isUShortType -> PrimitiveType.SHORT
        type.isIntType || type.isUIntType -> PrimitiveType.INT
        type.isLongType || type.isULongType -> PrimitiveType.LONG
        type.isFloatType -> PrimitiveType.FLOAT
        type.isDoubleType -> PrimitiveType.DOUBLE
        else -> null
    }
}

private val ITERABLE_LIKE_CLASS_IDS: Collection<ClassId> = hashSetOf(StandardClassIds.Iterable, StandardClassIds.Array)

context(_: KaSession)
private fun getIterableElementType(type: KaType): KaType? {
    if (type is KaClassType && type.classId in ITERABLE_LIKE_CLASS_IDS) {
        return type.typeArguments.singleOrNull()?.type
    }

    for (supertype in type.allSupertypes) {
        if (supertype is KaClassType) {
            if (supertype.classId in ITERABLE_LIKE_CLASS_IDS) {
                return supertype.typeArguments.singleOrNull()?.type
            }
        }
    }

    return null
}