// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.idea.util.application.executeInBackgroundWithProgress
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getOutermostParenthesizerOrThis
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.callUtil.getParentResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeSmart

object KotlinNameSuggester {
    fun suggestNamesByExpressionAndType(
        expression: KtExpression,
        type: KotlinType?,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean,
        defaultName: String?
    ): Collection<String> {
        return executeInBackgroundWithProgress(expression.project) {
            LinkedHashSet<String>().apply {
                addNamesByExpression(expression, bindingContext, validator)

                (type ?: bindingContext?.getType(expression))?.let {
                    addNamesByType(it, validator)
                }

                if (isEmpty()) {
                    addName(defaultName, validator)
                }
            }.toList()
        }
    }

    fun suggestNamesByType(type: KotlinType, validator: (String) -> Boolean, defaultName: String? = null): List<String> =
        executeInBackgroundWithProgress(null) {
            ArrayList<String>().apply {
                addNamesByType(type, validator)
                if (isEmpty()) {
                    ProgressManager.checkCanceled()
                    addName(defaultName, validator)
                }
            }
        }

    private fun executeInBackgroundWithProgress(project: Project?, blockToExecute: () -> List<String>): List<String> =
        if (isDispatchThread() && !ApplicationManager.getApplication().isWriteAccessAllowed) {
            executeInBackgroundWithProgress(
                project,
                KotlinIdeaCoreBundle.message("progress.title.calculating.names")
            ) { runReadAction { blockToExecute() } }
        } else {
            blockToExecute()
        }

    fun suggestNamesByExpressionOnly(
        expression: KtExpression,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean, defaultName: String? = null
    ): List<String> {
        val result = ArrayList<String>()

        result.addNamesByExpression(expression, bindingContext, validator)

        if (result.isEmpty()) {
            result.addName(defaultName, validator)
        }

        return result
    }

    fun suggestIterationVariableNames(
        collection: KtExpression,
        elementType: KotlinType,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean, defaultName: String?
    ): Collection<String> {
        val result = LinkedHashSet<String>()

        suggestNamesByExpressionOnly(collection, bindingContext, { true })
            .mapNotNull { name -> StringUtil.unpluralize(name)}
            .filter { name -> !name.isKeyword() }
            .mapTo(result) { suggestNameByName(it, validator) }

        result.addNamesByType(elementType, validator)

        if (result.isEmpty()) {
            result.addName(defaultName, validator)
        }

        return result
    }

    private fun String?.isKeyword() = this in KtTokens.KEYWORDS.types.map { it.toString() }

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

    private fun String.withPrefix(prefix: String): String {
        if (isEmpty()) return prefix
        val c = this[0]
        return (if (c in 'a'..'z') prefix.decapitalizeAsciiOnly()
        else prefix.capitalizeAsciiOnly()) + capitalizeAsciiOnly()
    }

    private val COMMON_TYPE_PARAMETER_NAMES = listOf("T", "U", "V", "W", "X", "Y", "Z")
    private const val MAX_NUMBER_OF_SUGGESTED_NAME_CHECKS = 1000

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
     * Validates name, and slightly improves it by adding number to name in case of conflicts
     * @param name to check it in scope
     * @return name or nameI, where I is number
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

    private fun MutableCollection<String>.addNamesByType(type: KotlinType, validator: (String) -> Boolean) {
        val myType = TypeUtils.makeNotNullable(type) // wipe out '?'
        val builtIns = myType.builtIns
        val typeChecker = KotlinTypeChecker.DEFAULT
        if (ErrorUtils.containsErrorType(myType)) return
        val typeDescriptor = myType.constructor.declarationDescriptor
        when {
            typeChecker.equalTypes(builtIns.booleanType, myType) -> addName("b", validator)
            typeChecker.equalTypes(builtIns.intType, myType) -> addName("i", validator)
            typeChecker.equalTypes(builtIns.byteType, myType) -> addName("byte", validator)
            typeChecker.equalTypes(builtIns.longType, myType) -> addName("l", validator)
            typeChecker.equalTypes(builtIns.floatType, myType) -> addName("fl", validator)
            typeChecker.equalTypes(builtIns.doubleType, myType) -> addName("d", validator)
            typeChecker.equalTypes(builtIns.shortType, myType) -> addName("sh", validator)
            typeChecker.equalTypes(builtIns.charType, myType) -> addName("c", validator)
            typeChecker.equalTypes(builtIns.stringType, myType) -> addName("s", validator)
            myType.isFunctionType -> addName("function", validator)
            KotlinBuiltIns.isArray(myType) || KotlinBuiltIns.isPrimitiveArray(myType) -> {
                addNamesForArray(builtIns, myType, validator, typeChecker)
            }
            typeDescriptor != null && DescriptorUtils.isSubtypeOfClass(typeDescriptor.defaultType, builtIns.iterable.original)
                    && type.arguments.isNotEmpty() ->
                addNameForIterableInheritors(type, validator)
            else -> {
                val name = getTypeName(myType)
                if (name != null) {
                    addCamelNames(name, validator)
                }
                addNamesFromGenericParameters(myType, validator)
            }
        }
    }

    private fun MutableCollection<String>.addNamesForArray(
        builtIns: KotlinBuiltIns,
        myType: KotlinType,
        validator: (String) -> Boolean,
        typeChecker: KotlinTypeChecker
    ) {
        val elementType = builtIns.getArrayElementType(myType)
        val className = getTypeName(elementType)
        if (className != null) {
            addCamelNames(StringUtil.pluralize(className), validator)
            if (!typeChecker.equalTypes(builtIns.booleanType, elementType) &&
                !typeChecker.equalTypes(builtIns.intType, elementType) &&
                !typeChecker.equalTypes(builtIns.byteType, elementType) &&
                !typeChecker.equalTypes(builtIns.longType, elementType) &&
                !typeChecker.equalTypes(builtIns.floatType, elementType) &&
                !typeChecker.equalTypes(builtIns.doubleType, elementType) &&
                !typeChecker.equalTypes(builtIns.shortType, elementType) &&
                !typeChecker.equalTypes(builtIns.charType, elementType) &&
                !typeChecker.equalTypes(builtIns.stringType, elementType)
            ) {
                addName("arrayOf" + StringUtil.capitalize(className) + "s", validator)
            }
        }
    }

    private fun MutableCollection<String>.addNameForIterableInheritors(type: KotlinType, validator: (String) -> Boolean) {
        val typeArgument = type.arguments.singleOrNull()?.type ?: return
        val name = getTypeName(typeArgument)
        if (name != null) {
            addCamelNames(StringUtil.pluralize(name), validator)
            val typeName = getTypeName(type)
            if (typeName != null) {
                addCamelNames(name + typeName, validator)
            }
        }
    }

    private fun MutableCollection<String>.addNamesFromGenericParameters(type: KotlinType, validator: (String) -> Boolean) {
        val typeName = getTypeName(type) ?: return
        val arguments = type.arguments
        val builder = StringBuilder()
        if (arguments.isEmpty()) return
        for (argument in arguments) {
            val name = getTypeName(argument.type)
            if (name != null) {
                builder.append(name)
            }
        }
        addCamelNames(builder.append(typeName).toString(), validator)
    }

    private fun getTypeName(type: KotlinType): String? {
        val descriptor = type.constructor.declarationDescriptor
        if (descriptor != null) {
            val className = descriptor.name
            if (!className.isSpecial) {
                return className.asString()
            }
        }
        return null
    }

    private val ACCESSOR_PREFIXES = arrayOf("get", "is", "set")

    fun getCamelNames(name: String, validator: (String) -> Boolean, startLowerCase: Boolean): List<String> {
        val result = ArrayList<String>()
        result.addCamelNames(name, validator, startLowerCase)
        return result
    }

    private fun MutableCollection<String>.addCamelNames(name: String, validator: (String) -> Boolean, startLowerCase: Boolean = true) {
        if (name === "" || !name.unquote().isIdentifier()) return
        var s = extractIdentifiers(name)

        for (prefix in ACCESSOR_PREFIXES) {
            if (!s.startsWith(prefix)) continue

            val len = prefix.length
            if (len < s.length && Character.isUpperCase(s[len])) {
                s = s.substring(len)
                break
            }
        }

        var upperCaseLetterBefore = false
        for (i in 0 until s.length) {
            val c = s[i]
            val upperCaseLetter = Character.isUpperCase(c)

            if (i == 0) {
                addName(if (startLowerCase) s.decapitalizeSmart() else s, validator)
            } else {
                if (upperCaseLetter && !upperCaseLetterBefore) {
                    val substring = s.substring(i)
                    addName(if (startLowerCase) substring.decapitalizeSmart() else substring, validator)
                }
            }

            upperCaseLetterBefore = upperCaseLetter
        }
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

    private fun MutableCollection<String>.addNamesByExpressionPSI(expression: KtExpression?, validator: (String) -> Boolean) {
        if (expression == null) return
        when (val deparenthesized = KtPsiUtil.safeDeparenthesize(expression)) {
            is KtSimpleNameExpression -> addCamelNames(deparenthesized.getReferencedName(), validator)
            is KtQualifiedExpression -> addNamesByExpressionPSI(deparenthesized.selectorExpression, validator)
            is KtCallExpression -> addNamesByExpressionPSI(deparenthesized.calleeExpression, validator)
            is KtPostfixExpression -> addNamesByExpressionPSI(deparenthesized.baseExpression, validator)
        }
    }

    private fun MutableCollection<String>.addNamesByExpression(
        expression: KtExpression?,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean
    ) {
        if (expression == null) return

        addNamesByValueArgument(expression, bindingContext, validator)
        addNamesByExpressionPSI(expression, validator)
    }

    private fun MutableCollection<String>.addNamesByValueArgument(
        expression: KtExpression,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean
    ) {
        if (bindingContext == null) return
        val argumentExpression = expression.getOutermostParenthesizerOrThis()
        val valueArgument = argumentExpression.parent as? KtValueArgument ?: return
        val resolvedCall = argumentExpression.getParentResolvedCall(bindingContext) ?: return
        val argumentMatch = resolvedCall.getArgumentMapping(valueArgument) as? ArgumentMatch ?: return
        val parameter = argumentMatch.valueParameter
        if (parameter.containingDeclaration.hasStableParameterNames()) {
            addName(parameter.name.asString(), validator)
        }
    }

    private fun MutableCollection<String>.addName(name: String?, validator: (String) -> Boolean) {
        if (name == null) return
        val correctedName = when {
            name.isIdentifier() -> name
            name == "class" -> "clazz"
            else -> return
        }
        add(suggestNameByName(correctedName, validator))
    }
}
