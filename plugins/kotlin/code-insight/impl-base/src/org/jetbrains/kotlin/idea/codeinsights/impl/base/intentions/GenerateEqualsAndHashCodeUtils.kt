// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.codeinsight.utils.isNonNullableBooleanType
import org.jetbrains.kotlin.idea.codeinsight.utils.isNullableAnyType
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.util.OperatorNameConventions.EQUALS
import org.jetbrains.kotlin.util.OperatorNameConventions.HASH_CODE

object GenerateEqualsAndHashCodeUtils {
    context(KaSession)
    fun matchesEqualsMethodSignature(function: KaNamedFunctionSymbol): Boolean {
        if (function.modality == KaSymbolModality.ABSTRACT) return false
        if (function.name != EQUALS) return false
        if (function.typeParameters.isNotEmpty()) return false
        val param = function.valueParameters.singleOrNull() ?: return false
        val paramType = param.returnType
        val returnType = function.returnType
        return paramType.isNullableAnyType() && returnType.isNonNullableBooleanType()
    }

    context(KaSession)
    fun matchesHashCodeMethodSignature(function: KaNamedFunctionSymbol): Boolean {
        if (function.modality == KaSymbolModality.ABSTRACT) return false
        if (function.name != HASH_CODE) return false
        if (function.typeParameters.isNotEmpty()) return false
        if (function.valueParameters.isNotEmpty()) return false
        val returnType = function.returnType
        return returnType.isIntType && !returnType.isMarkedNullable
    }

    fun generateEqualsHeaderAndBodyTexts(targetClass: KtClass): Pair<String, String> {
        val hasExpectModifier = targetClass.hasExpectModifier()
        var functionText = buildString {
            append("override fun equals(other: Any?): Boolean")
            if (!hasExpectModifier) {
                append(" {\n    return super.equals(other)\n}")
            }
        }
        var bodyText = ""
        analyze(targetClass) {
            val classSymbol = targetClass.classSymbol ?: return@analyze
            val equalsMethod = findEqualsMethodForClass(classSymbol) as? KaNamedFunctionSymbol ?: return@analyze
            val superContainingEqualsMethod = equalsMethod.containingDeclaration ?: return@analyze

            val parameterName = equalsMethod.valueParameters.singleOrNull()?.name?.asString() ?: return@analyze

            /**
             * TODO: Correctly emits parameter type and return type when there are user-defined classes "Any" and "Boolean".
             *  The counterpart in FE1.0 uses [org.jetbrains.kotlin.idea.actions.generate.generateFunctionSkeleton] that automatically
             *  generates `equals(other: Any?)` by default and it generates `equals(other: kotlin.Any?)` if the project has a custom class
             *  named "Any". To preserve the same behavior for FIR, we tried
             *  [org.jetbrains.kotlin.analysis.api.components.KtSymbolDeclarationRendererMixIn.render] with
             *  [org.jetbrains.kotlin.analysis.api.components.KaBuiltinTypes.any],
             *  `builtinTypes.ANY.expandedClassSymbol.classId?.asSingleFqName()?.asString()`, and
             *  `org.jetbrains.kotlin.builtins.StandardNames.FqNames.any.render()`, but they all have the same rendering result
             *  regardless of the user-defined "Any" class. We specify the parameter and the return type as `kotlin.Any?` and
             *  `kotlin.Boolean` and rely on [org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences], but it always
             *  shortens the name regardless of the existence of the user-defined classes.
             */
            functionText = buildString {
                append("override fun equals(${parameterName}: kotlin.Any?): kotlin.Boolean")
                if (!hasExpectModifier) {
                    append(" {\n    return super.equals(${parameterName})\n}")
                }
            }

            var typeForCast = targetClass.classIdIfNonLocal?.asSingleFqName()?.asString()
            val typeParams = targetClass.typeParameters
            if (typeParams.isNotEmpty()) {
                typeForCast += typeParams.joinToString(prefix = "<", postfix = ">") { "*" }
            }

            val useIsCheck = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER
            val isNotInstanceCondition = if (useIsCheck) {
                "$parameterName !is $typeForCast"
            } else {
                generateClassLiteralsNotEqual(parameterName, targetClass)
            }

            bodyText = buildString {
                append("if (this === $parameterName) return true\n")
                append("if ($isNotInstanceCondition) return false\n")
                if (superContainingEqualsMethod != builtinTypes.any.expandedSymbol) {
                    append("if (!super.equals($parameterName)) return false\n")
                }

                /**
                 * TODO: We have to add a wizard to select members used for equals. See how
                 *  [org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsAndHashcodeAction.prepareMembersInfo] uses
                 *  [org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsWizard].
                 */
                val variablesForEquals = getPropertiesToUseInGeneratedMember(targetClass)
                if (variablesForEquals.isNotEmpty()) {
                    if (!useIsCheck) {
                        append("\n$parameterName as $typeForCast\n")
                    }

                    append('\n')

                    variablesForEquals.forEach {
                        val variableType = it.returnType
                        val isNullableType = variableType.isMarkedNullable
                        val isArray = variableType.isArrayOrPrimitiveArray
                        val canUseArrayContentFunctions = targetClass.canUseArrayContentFunctions()
                        val propName = it.name ?: return@forEach
                        val notEquals = when {
                            isArray -> {
                                "!${
                                    generateArraysEqualsCall(
                                        variableType, canUseArrayContentFunctions, propName, "$parameterName.$propName"
                                    )
                                }"
                            }

                            else -> {
                                "$propName != $parameterName.$propName"
                            }
                        }
                        val equalsCheck = "if ($notEquals) return false\n"
                        if (isArray && isNullableType && canUseArrayContentFunctions) {
                            append("if ($propName != null) {\n")
                            append("if ($parameterName.$propName == null) return false\n")
                            append(equalsCheck)
                            append("} else if ($parameterName.$propName != null) return false\n")
                        } else {
                            append(equalsCheck)
                        }
                    }

                    append('\n')
                }

                append("return true")
            }
        }
        return Pair(functionText, bodyText)
    }

    fun generateHashCodeHeaderAndBodyTexts(targetClass: KtClass): Pair<String, String> {
        val hasExpectModifier = targetClass.hasExpectModifier()
        val functionText = buildString {
            append("override fun hashCode(): kotlin.Int")
            if (!hasExpectModifier) {
                append(" {\n    return super.hashCode()\n}")
            }
        }
        var bodyText = ""
        analyze(targetClass) {
            fun KtNamedDeclaration.genVariableHashCode(parenthesesNeeded: Boolean): String {
                val ref = name ?: return "0"
                val type = returnType
                val isNullable = type.isMarkedNullable

                var text = when {
                    type.semanticallyEquals(builtinTypes.byte) || type.semanticallyEquals(builtinTypes.short)
                            || type.semanticallyEquals(builtinTypes.int) -> ref

                    type.isArrayOrPrimitiveArray -> {
                        val canUseArrayContentFunctions = targetClass.canUseArrayContentFunctions()
                        val shouldWrapInLet = isNullable && !canUseArrayContentFunctions
                        val hashCodeArg = if (shouldWrapInLet) StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier else ref
                        val hashCodeCall = generateArrayHashCodeCall(type, canUseArrayContentFunctions, hashCodeArg)
                        if (shouldWrapInLet) "$ref?.let { $hashCodeCall }" else hashCodeCall
                    }

                    else -> if (isNullable) "$ref?.hashCode()" else "$ref.hashCode()"
                }
                if (isNullable) {
                    text += " ?: 0"
                    if (parenthesesNeeded) {
                        text = "($text)"
                    }
                }

                return text
            }

            val classSymbol = targetClass.classSymbol ?: return@analyze
            val hashCodeMethod = findHashCodeMethodForClass(classSymbol) as? KaNamedFunctionSymbol ?: return@analyze
            val superContainingHashCodeMethod = hashCodeMethod.containingDeclaration ?: return@analyze

            /**
             * TODO: We have to add a wizard to select members used for hashCode. See how
             *  [org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsAndHashcodeAction.prepareMembersInfo] uses
             *  [org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsWizard].
             */
            val variablesForHashCode = getPropertiesToUseInGeneratedMember(targetClass)
            val propertyIterator = variablesForHashCode.iterator()

            val initialValue = when {
                superContainingHashCodeMethod != builtinTypes.any.expandedSymbol -> "super.hashCode()"
                propertyIterator.hasNext() -> propertyIterator.next().genVariableHashCode(false)
                else -> generateClassLiteral(targetClass) + ".hashCode()"
            }

            bodyText =
                if (propertyIterator.hasNext()) { // TODO: Confirm that `variablesForHashCode.map { it.name?.quoteIfNeeded()!! }` is safe here
                    val validator = CollectingNameValidator(variablesForHashCode.map { it.name?.quoteIfNeeded()!! })
                    val resultVarName = KotlinNameSuggester.suggestNameByName("result", validator)
                    buildString {
                        append("var $resultVarName = $initialValue\n")
                        propertyIterator.forEach { append("$resultVarName = 31 * $resultVarName + ${it.genVariableHashCode(true)}\n") }
                        append("return $resultVarName")
                    }
                } else "return $initialValue"
        }
        return Pair(functionText, bodyText)
    }

    context(KaSession)
    fun findEqualsMethodForClass(classSymbol: KaClassSymbol): KaCallableSymbol? =
        findNonGeneratedMethodInSelfOrSuperclass(classSymbol, EQUALS) { matchesEqualsMethodSignature(it) }

    context(KaSession)
    fun findHashCodeMethodForClass(classSymbol: KaClassSymbol): KaCallableSymbol? =
        findNonGeneratedMethodInSelfOrSuperclass(classSymbol, HASH_CODE) { matchesHashCodeMethodSignature(it) }

    /**
     * Searches for a callable member symbol with the given [methodName] that matches the [signatureFilter].
     * If the found symbol is generated, the search is done for one more time in the superclass' scope
     */
    context(KaSession)
    private fun findNonGeneratedMethodInSelfOrSuperclass(
        classSymbol: KaClassSymbol,
        methodName: Name,
        signatureFilter: (KaNamedFunctionSymbol) -> Boolean
    ): KaCallableSymbol? {
        val methodSymbol = findMethod(classSymbol, methodName, signatureFilter)

        // We are not interested in synthetic members of data classes here
        // They won't be generated by the compiler after the explicit members are created (see the Kotlin Specification, 4.1.2)
        if (methodSymbol?.origin != KaSymbolOrigin.SOURCE_MEMBER_GENERATED) return methodSymbol

        // Instead, if a generated member was found, we search for a member again in its parent class' scope to find a relevant member
        val directSuperclassSymbol = findExplicitSuperclassOrAny(classSymbol) ?: return null

        return findMethod(directSuperclassSymbol, methodName, signatureFilter)
    }

    /**
     * Finds methods whose name is [methodName] not only from the class [classSymbol] but also its parent classes,
     * and returns method symbols after filtering them using [condition].
     */
    context(KaSession)
    private fun findMethod(
        classSymbol: KaClassSymbol, methodName: Name, condition: (KaNamedFunctionSymbol) -> Boolean
    ): KaCallableSymbol? = classSymbol.memberScope.callables(methodName).filter {
        it is KaNamedFunctionSymbol && condition(it)
    }.singleOrNull()

    /**
     * Searches for the direct superclass symbol of this [classSymbol] (ignoring interfaces).
     * Because currently `kotlin.Any` is not listed in symbol's supertypes when all declared supertypes are interfaces,
     * this case is handled separately.
     */
    context(KaSession)
    private fun findExplicitSuperclassOrAny(classSymbol: KaClassSymbol): KaClassSymbol? {
        val supertypes = classSymbol.superTypes
        return supertypes.map { it.symbol }.filterIsInstance<KaClassSymbol>().singleOrNull { it.classKind == KaClassKind.CLASS }
                ?: supertypes.first().allSupertypes.singleOrNull { it.isAnyType }?.symbol as? KaClassSymbol
    }

    /**
     * A function to generate the "not equals" comparison between the class of `this` and the class of the parameter.
     */
    private fun generateClassLiteralsNotEqual(paramName: String, targetClass: KtClassOrObject): String {
        val defaultExpression = "javaClass != $paramName?.javaClass"
        if (!targetClass.languageVersionSettings.supportsFeature(LanguageFeature.BoundCallableReferences)) return defaultExpression
        return when {
            targetClass.platform.isCommon() -> "other == null || this::class != $paramName::class"
            targetClass.platform.isJs() -> "other == null || this::class.js != $paramName::class.js"
            else -> defaultExpression
        }
    }

    /**
     * A function to generate the class i.e., `javaClass` or `this::class`.
     */
    private fun generateClassLiteral(targetClass: KtClassOrObject): String {
        val defaultExpression = "javaClass"
        if (!targetClass.languageVersionSettings.supportsFeature(LanguageFeature.BoundCallableReferences)) return defaultExpression
        return when {
            targetClass.platform.isCommon() -> "this::class"
            targetClass.platform.isJs() -> "this::class.js"
            else -> defaultExpression
        }
    }

    context(KaSession)
    fun getPropertiesToUseInGeneratedMember(classOrObject: KtClassOrObject): List<KtNamedDeclaration> =
        buildList<KtNamedDeclaration> {
            classOrObject.primaryConstructorParameters.filterTo(this) { it.hasValOrVar() }
            classOrObject.declarations.asSequence().filterIsInstance<KtProperty>().filterTo(this) {
                it.symbol is KaPropertySymbol
            }
        }.filter {
            it.name?.quoteIfNeeded().isIdentifier()
        }

    private fun KtElement.canUseArrayContentFunctions() = languageVersionSettings.apiVersion >= ApiVersion.KOTLIN_1_1

    context(KaSession)
    private fun generateArraysEqualsCall(
        type: KaType, canUseContentFunctions: Boolean, arg1: String, arg2: String
    ): String {
        return if (canUseContentFunctions) {
            val methodName = if (type.isNestedArray) "contentDeepEquals" else "contentEquals"
            "$arg1.$methodName($arg2)"
        } else {
            val methodName = if (type.isNestedArray) "deepEquals" else "equals"
            "java.util.Arrays.$methodName($arg1, $arg2)"
        }
    }

    context(KaSession)
    private fun generateArrayHashCodeCall(
        variableType: KaType?, canUseContentFunctions: Boolean, argument: String
    ): String {
        return if (canUseContentFunctions) {
            val methodName = if (variableType?.isNestedArray == true) "contentDeepHashCode" else "contentHashCode"
            val dot = if (variableType?.isMarkedNullable == true) "?." else "."
            "$argument$dot$methodName()"
        } else {
            val methodName = if (variableType?.isNestedArray == true) "deepHashCode" else "hashCode"
            "java.util.Arrays.$methodName($argument)"
        }
    }
}
