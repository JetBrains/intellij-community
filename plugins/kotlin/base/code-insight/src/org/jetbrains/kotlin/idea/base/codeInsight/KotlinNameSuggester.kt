// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.text.NameUtilCore
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames.FqNames
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly

@DslMarker
private annotation class NameSuggesterDsl

class KotlinNameSuggester(
    private val case: Case,
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
                    || (escapeJavaHardKeywords && JavaLexer.isKeyword(name, LanguageLevel.HIGHEST))
                    || (escapeJavaSoftKeywords && JavaLexer.isSoftKeyword(name, LanguageLevel.HIGHEST))
        }

        fun escape(name: String): List<String> = escaper(name)
    }

    enum class CaseTransformation(val processor: (String) -> String) {
        DEFAULT({ it }),
        UPPERCASE({ it.toUpperCaseAsciiOnly() }),
        LOWERCASE({ it.toLowerCaseAsciiOnly() })
    }

    enum class Case(val case: CaseTransformation, val separator: String?, val capitalizeFirst: Boolean, val capitalizeNext: Boolean) {
        PASCAL(CaseTransformation.DEFAULT, separator = null, capitalizeFirst = true, capitalizeNext = true, ), // FooBar
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
     * Returns names based on the expression type.
     * Examples:
     *  - `5` -> {int, i, n}
     *  - `intArrayOf(5)` -> {ints}
     *  - listOf(User("Mary"), User("John")) -> {users}
     */
    fun KtAnalysisSession.suggestExpressionNames(expression: KtExpression): Sequence<String> {
        val type = expression.getKtType() ?: return emptySequence()
        return suggestTypeNames(type)
    }

    /**
     * Returns names based on a given type.
     * Examples:
     *  - `Int` -> {int, i, n}
     *  - `IntArray` -> {ints}
     *  - `List<User>` -> {users}
     */
    fun KtAnalysisSession.suggestTypeNames(type: KtType): Sequence<String> {
        return sequence {
            val primitiveType = getPrimitiveType(type)
            if (primitiveType != null) {
                PRIMITIVE_TYPE_NAMES.getValue(primitiveType).forEach { registerCompoundName(it) }
                return@sequence
            }

            if (type.isCharSequence || type.isString) {
                registerCompoundName("string")
                registerCompoundName("str")
                registerCompoundName("s")
                registerCompoundName("text")
                return@sequence
            }

            if (type.isFunctionType) {
                registerCompoundName("function")
                registerCompoundName("fn")
                registerCompoundName("f")
                return@sequence
            }

            tailrec fun getClassId(type: KtType): ClassId? = when (type) {
                is KtDefinitelyNotNullType -> getClassId(type.original)
                is KtFlexibleType -> getClassId(type.lowerBound)
                is KtTypeParameterType -> {
                    val bound = type.symbol.upperBounds.firstOrNull()
                    if (bound != null) getClassId(bound) else null
                }
                is KtNonErrorClassType -> type.classId
                else -> ClassId(FqName.ROOT, FqName.topLevel(Name.identifier("Value")), false)
            }

            suspend fun SequenceScope<String>.registerClassNames(type: KtType, preprocessor: (String) -> String = { it }) {
                val classId = getClassId(type) ?: return

                KotlinNameSuggester(case, EscapingRules.NONE, ignoreCompanionNames)
                    .suggestClassNames(classId)
                    .map(preprocessor)
                    .forEach { registerCompoundName(it) }
            }

            if (type is KtNonErrorClassType) {
                val elementType = getIterableElementType(type)

                if (elementType != null) {
                    registerClassNames(elementType) { Strings.pluralize(it) }
                    return@sequence
                }

                val classId = type.classId
                if (!classId.isLocal && !classId.isNestedClass) {
                    val fqName = classId.asSingleFqName().toUnsafe()

                    val primitiveElementType = FqNames.arrayClassFqNameToPrimitiveType[fqName]
                    if (primitiveElementType != null) {
                        val primitiveName = PRIMITIVE_TYPE_NAMES.getValue(primitiveElementType).first()
                        registerCompoundName(Strings.pluralize(primitiveName))
                        return@sequence
                    }

                    val specialNames = getSpecialNames(fqName)
                    if (specialNames != null) {
                        specialNames.forEach { registerCompoundName(it) }
                        return@sequence
                    }
                }

                registerClassNames(type)
            }
        }
    }

    /**
     * Suggests type alias name for a given type element.
     * Examples:
     *  - `String` -> StringAlias
     *  - `Int?` -> NullableInt
     *  - `(String) -> Boolean` -> StringPredicate
     */
    fun KtAnalysisSession.suggestTypeAliasName(type: KtTypeElement): String {
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
                        if (returnType.getKtType().isBoolean) {
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

        val TYPE_PARAMETER_NAMES = listOf(
            "T", "U", "V", "W", "X", "Y", "Z", "A", "B", "C", "D", "E",
            "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "R", "S"
        )

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

private fun KtAnalysisSession.getPrimitiveType(type: KtType): PrimitiveType? {
    return when {
        type.isBoolean -> PrimitiveType.BOOLEAN
        type.isChar -> PrimitiveType.CHAR
        type.isByte || type.isUByte -> PrimitiveType.BYTE
        type.isShort || type.isUShort -> PrimitiveType.SHORT
        type.isInt || type.isUInt -> PrimitiveType.INT
        type.isLong || type.isULong -> PrimitiveType.LONG
        type.isFloat -> PrimitiveType.FLOAT
        type.isDouble -> PrimitiveType.DOUBLE
        else -> null
    }
}

private val ITERABLE_LIKE_CLASS_IDS =
    listOf(FqNames.iterable, FqNames.array.toSafe())
        .map { ClassId.topLevel(it) }

private fun KtAnalysisSession.getIterableElementType(type: KtType): KtType? {
    if (type is KtNonErrorClassType && type.classId in ITERABLE_LIKE_CLASS_IDS) {
        return type.typeArguments.singleOrNull()?.type
    }

    for (supertype in type.getAllSuperTypes()) {
        if (supertype is KtNonErrorClassType) {
            if (supertype.classId in ITERABLE_LIKE_CLASS_IDS) {
                return supertype.typeArguments.singleOrNull()?.type
            }
        }
    }

    return null
}