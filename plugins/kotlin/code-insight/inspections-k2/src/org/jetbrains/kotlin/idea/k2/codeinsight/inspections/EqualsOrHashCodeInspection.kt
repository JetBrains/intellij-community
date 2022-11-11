// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.GenerateFunctionFix
import org.jetbrains.kotlin.idea.core.AbstractKotlinNameSuggester
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

class EqualsOrHashCodeInspection : AbstractKotlinInspection() {
    /**
     * A name suggester object for `hashCode()` result.
     *
     * TODO: Replace [HashCodeResultVariableNameSuggester] with [org.jetbrains.kotlin.idea.core.FirKotlinNameSuggester].
     *  At this moment, it causes a compile error "Class [org.jetbrains.kotlin.idea.core.FirKotlinNameSuggester] is compiled by
     *  a pre-release version of Kotlin and cannot be loaded by this version of the compiler"
     */
    private object HashCodeResultVariableNameSuggester : AbstractKotlinNameSuggester()

    private class Equals(function: String, body: String) : GenerateFunctionFix(function, body) {
        override fun getName() = KotlinBundle.message("equals.text")
    }

    private class HashCode(function: String, body: String) : GenerateFunctionFix(function, body) {
        override fun getName() = KotlinBundle.message("hash.code.text")
    }

    private fun KtAnalysisSession.matchesEqualsMethodSignature(function: KtFunctionSymbol): Boolean {
        if (function.name != EQUALS) return false
        if (function.typeParameters.isNotEmpty()) return false
        val param = function.valueParameters.singleOrNull() ?: return false
        val paramType = param.returnType
        val returnType = function.returnType
        return with(this) {
            paramType.isNullableAnyType() && returnType.isNonNullableBooleanType()
        }
    }

    private fun KtAnalysisSession.matchesHashCodeMethodSignature(function: KtFunctionSymbol): Boolean {
        if (function.name != HASH_CODE) return false
        if (function.typeParameters.isNotEmpty()) return false
        if (function.valueParameters.isNotEmpty()) return false
        val returnType = function.returnType
        return returnType.isInt && !returnType.isMarkedNullable
    }

    /**
     * Finds methods whose name is [methodName] not only from the class [classSymbol] but also its parent classes,
     * and returns method symbols after filtering them using [condition].
     */
    private fun KtAnalysisSession.findMethod(
        classSymbol: KtClassOrObjectSymbol, methodName: Name, condition: (KtCallableSymbol) -> Boolean
    ): KtCallableSymbol? = classSymbol.getMemberScope().getCallableSymbols { name -> name == methodName }.filter(condition).singleOrNull()

    private fun KtAnalysisSession.findEqualsMethodForClass(classSymbol: KtClassOrObjectSymbol): KtCallableSymbol? =
        findMethod(classSymbol, EQUALS) { callableSymbol ->
            (callableSymbol as? KtFunctionSymbol)?.let { matchesEqualsMethodSignature(it) } == true
        }

    private fun KtAnalysisSession.findHashCodeMethodForClass(classSymbol: KtClassOrObjectSymbol): KtCallableSymbol? =
        findMethod(classSymbol, HASH_CODE) { callableSymbol ->
            (callableSymbol as? KtFunctionSymbol)?.let { matchesHashCodeMethodSignature(it) } == true
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

    private fun KtAnalysisSession.getPropertiesToUseInGeneratedMember(classOrObject: KtClassOrObject): List<KtNamedDeclaration> =
        buildList {
            classOrObject.primaryConstructorParameters.filterTo(this) { it.hasValOrVar() }
            classOrObject.declarations.asSequence().filterIsInstance<KtProperty>().filterTo(this) {
                it.getVariableSymbol() is KtPropertySymbol
            }
        }.filter {
            it.name?.quoteIfNeeded().isIdentifier()
        }

    private fun KtElement.canUseArrayContentFunctions() = languageVersionSettings.apiVersion >= ApiVersion.KOTLIN_1_1

    private fun KtAnalysisSession.generateArraysEqualsCall(
        type: KtType, canUseContentFunctions: Boolean, arg1: String, arg2: String
    ): String {
        return if (canUseContentFunctions) {
            val methodName = if (type.isNestedArray()) "contentDeepEquals" else "contentEquals"
            "$arg1.$methodName($arg2)"
        } else {
            val methodName = if (type.isNestedArray()) "deepEquals" else "equals"
            "java.util.Arrays.$methodName($arg1, $arg2)"
        }
    }

    private fun KtAnalysisSession.generateArrayHashCodeCall(
        variableType: KtType?, canUseContentFunctions: Boolean, argument: String
    ): String {
        return if (canUseContentFunctions) {
            val methodName = if (variableType?.isNestedArray() == true) "contentDeepHashCode" else "contentHashCode"
            val dot = if (variableType?.isMarkedNullable == true) "?." else "."
            "$argument$dot$methodName()"
        } else {
            val methodName = if (variableType?.isNestedArray() == true) "deepHashCode" else "hashCode"
            "java.util.Arrays.$methodName($argument)"
        }
    }

    private fun generateEqualsFunctionAndBodyTexts(targetClass: KtClass): Pair<String, String> {
        val hasExpectModifier = targetClass.hasExpectModifier()
        var functionText = buildString {
            append("override fun equals(other: Any?): Boolean")
            if (!hasExpectModifier) {
                append(" {\n    return super.equals(other)\n}")
            }
        }
        var bodyText = ""
        analyze(targetClass) {
            val classSymbol = targetClass.getClassOrObjectSymbol() ?: return@analyze
            val equalsMethod = findEqualsMethodForClass(classSymbol) as? KtFunctionSymbol ?: return@analyze
            val superContainingEqualsMethod = equalsMethod.getContainingSymbol() ?: return@analyze

            val parameterName = equalsMethod.valueParameters.singleOrNull()?.name?.asString() ?: return@analyze

            /**
             * TODO: Correctly emits parameter type and return type when there are user-defined classes "Any" and "Boolean".
             *  The counterpart in FE1.0 uses [org.jetbrains.kotlin.idea.actions.generate.generateFunctionSkeleton] that automatically
             *  generates `equals(other: Any?)` by default and it generates `equals(other: kotlin.Any?)` if the project has a custom class
             *  named "Any". To preserve the same behavior for FIR, we tried
             *  [org.jetbrains.kotlin.analysis.api.components.KtSymbolDeclarationRendererMixIn.render] with
             *  [org.jetbrains.kotlin.analysis.api.components.KtBuiltinTypes.ANY],
             *  `builtinTypes.ANY.expandedClassSymbol.classIdIfNonLocal?.asSingleFqName()?.asString()`, and
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
                if (superContainingEqualsMethod != builtinTypes.ANY.expandedClassSymbol) {
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
                        val variableType = it.getKtType() ?: return@forEach
                        val isNullableType = variableType.isMarkedNullable
                        val isArray = variableType.isArrayOrPrimitiveArray()
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

    private fun generateHashCodeFunctionAndBodyTexts(targetClass: KtClass): Pair<String, String> {
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
                val type = getReturnKtType()
                val isNullable = type.isMarkedNullable

                var text = when {
                    type == builtinTypes.BYTE || type == builtinTypes.SHORT || type == builtinTypes.INT -> ref

                    type.isArrayOrPrimitiveArray() -> {
                        val canUseArrayContentFunctions = targetClass.canUseArrayContentFunctions()
                        val shouldWrapInLet = isNullable && !canUseArrayContentFunctions
                        val hashCodeArg = if (shouldWrapInLet) "it" else ref
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

            val classSymbol = targetClass.getClassOrObjectSymbol() ?: return@analyze
            val hashCodeMethod = findHashCodeMethodForClass(classSymbol) as? KtFunctionSymbol ?: return@analyze
            val superContainingHashCodeMethod = hashCodeMethod.getContainingSymbol() ?: return@analyze

            /**
             * TODO: We have to add a wizard to select members used for hashCode. See how
             *  [org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsAndHashcodeAction.prepareMembersInfo] uses
             *  [org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateEqualsWizard].
             */
            val variablesForHashCode = getPropertiesToUseInGeneratedMember(targetClass)
            val propertyIterator = variablesForHashCode.iterator()

            val initialValue = when {
                superContainingHashCodeMethod != builtinTypes.ANY.expandedClassSymbol -> "super.hashCode()"
                propertyIterator.hasNext() -> propertyIterator.next().genVariableHashCode(false)
                else -> generateClassLiteral(targetClass) + ".hashCode()"
            }

            bodyText =
                if (propertyIterator.hasNext()) { // TODO: Confirm that `variablesForHashCode.map { it.name?.quoteIfNeeded()!! }` is safe here
                    val validator = CollectingNameValidator(variablesForHashCode.map { it.name?.quoteIfNeeded()!! })
                    val resultVarName = HashCodeResultVariableNameSuggester.suggestNameByName("result", validator)
                    buildString {
                        append("var $resultVarName = $initialValue\n")
                        propertyIterator.forEach { append("$resultVarName = 31 * $resultVarName + ${it.genVariableHashCode(true)}\n") }
                        append("return $resultVarName")
                    }
                } else "return $initialValue"
        }
        return Pair(functionText, bodyText)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return classOrObjectVisitor(fun(classOrObject) {
            val nameIdentifier = classOrObject.nameIdentifier ?: return
            if (classOrObject.declarations.none { it.name == "equals" || it.name == "hashCode" }) return
            val (equalsDeclaration, hashCodeDeclaration) = analyze(classOrObject) {
                val classOrObjectMemberDeclarations = classOrObject.declarations
                Pair(
                    classOrObjectMemberDeclarations.singleOrNull {
                        val function = it.getSymbol() as? KtFunctionSymbol ?: return@singleOrNull false
                        if (function.name != EQUALS) return@singleOrNull false
                        matchesEqualsMethodSignature(function)
                    } as? KtNamedFunction,
                    classOrObjectMemberDeclarations.singleOrNull {
                        val function = it.getSymbol() as? KtFunctionSymbol ?: return@singleOrNull false
                        if (function.name != HASH_CODE) return@singleOrNull false
                        matchesHashCodeMethodSignature(function)
                    } as? KtNamedFunction,
                )
            }
            if (equalsDeclaration == null && hashCodeDeclaration == null) return

            when (classOrObject) {
                is KtObjectDeclaration -> {
                    if (classOrObject.superTypeListEntries.isNotEmpty()) return
                    holder.registerProblem(
                        nameIdentifier,
                        KotlinBundle.message("equals.hashcode.in.object.declaration"),
                        DeletePsiElementsFix(listOf(equalsDeclaration, hashCodeDeclaration))
                    )
                }

                is KtClass -> {
                    if (equalsDeclaration != null && hashCodeDeclaration != null) return
                    val description = InspectionsBundle.message(
                        "inspection.equals.hashcode.only.one.defined.problem.descriptor",
                        if (equalsDeclaration != null) "<code>equals()</code>" else "<code>hashCode()</code>",
                        if (equalsDeclaration != null) "<code>hashCode()</code>" else "<code>equals()</code>"
                    )

                    val fix = if (equalsDeclaration != null) {
                        val (function, body) = generateHashCodeFunctionAndBodyTexts(classOrObject)
                        HashCode(function, body)
                    } else {
                        val (function, body) = generateEqualsFunctionAndBodyTexts(classOrObject)
                        Equals(function, body)
                    }

                    holder.registerProblem(
                        nameIdentifier,
                        description,
                        fix,
                    )
                }

                else -> return
            }
        })
    }
}