// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.isPrimitive
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.DefaultMemberFilters
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.KotlinEqualsHashCodeGeneratorExtension
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinEqualsHashCodeToStringSymbolUtils.findEqualsMethodForClass
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinEqualsHashCodeToStringSymbolUtils.findHashCodeMethodForClass
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinEqualsHashCodeToStringSymbolUtils.findToStringMethodForClass
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinEqualsHashCodeToStringSymbolUtils.getPropertiesToUseInGeneratedMember
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectDeclarationIfAny
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclarationWithReturnType
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

object GenerateEqualsAndHashCodeUtils {
    const val BASE_PARAM_NAME: String = "baseParamName"
    const val SUPER_HAS_EQUALS: String = "superHasEquals"
    const val SUPER_HAS_HASHCODE: String = "superHasHashCode"
    const val SUPER_PARAM_NAME: String = "superParamName"
    const val CHECK_PARAMETER_WITH_INSTANCEOF: String = "checkParameterWithInstanceof"

    /**
     * @param tryToFindEqualsMethodForClass Pass `true` to attempt to find an existing `equals()` implementation in
     * the class or its superclass.
     *
     * Pass `false` to bypass this check and generate an `equals()` implementation regardless of any existing method.
     * This is especially useful when generating the method for the quick fix for the
     * ```
     * [ABSTRACT_SUPER_CALL] Abstract member cannot be accessed directly
     * ```
     * error because the method where this error appears must be ignored.
     */
    context(_: KaSession)
    fun generateEquals(info: Info, tryToFindEqualsMethodForClass: Boolean = true): KtNamedFunction? {
        if (info.equalsInClass != null) return null

        val klass = info.klass

        val contextMap = mutableMapOf<String, Any?>()


        val equalsFunction = if (tryToFindEqualsMethodForClass) contextOf<KaSession>().findEqualsMethodForClass(klass.symbol as KaClassSymbol) else null

        contextMap[BASE_PARAM_NAME] = "other"
        if (equalsFunction != null) {
            (equalsFunction as? KaFunctionSymbol)?.valueParameters?.firstOrNull()?.let {
                val paramName = it.name.asString()
                contextMap[BASE_PARAM_NAME] = paramName
                contextMap[SUPER_PARAM_NAME] = paramName
            }
        }

        contextMap[SUPER_HAS_EQUALS] = equalsFunction != null && (equalsFunction.containingSymbol as? KaClassSymbol)?.classId != StandardClassIds.Any
        contextMap[CHECK_PARAMETER_WITH_INSTANCEOF] = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER

        collectEqualsContextFromExtensions(contextMap, info)

        val sortedVariables = info.variablesForEquals.sortedWithPrimitiveFirst()
        val methodText = VelocityGeneratorHelper
            .velocityGenerateCode(
                klass, sortedVariables, contextMap,
                KotlinEqualsHashCodeTemplatesManager.getInstance().defaultEqualsTemplate.template, false
            ) ?: return null

        val function = KtPsiFactory.contextual(klass).createFunction(methodText)
        setupExpectActualFunction(klass, function)
        return function
    }

    /**
     * @param tryToFindHashCodeMethodForClass Pass `true` to attempt to find an existing `hashCode()` implementation in
     * the class or its superclass.
     *
     * Pass `false` to bypass this check and generate an `hashCode()` implementation regardless of any existing method.
     * This is especially useful when generating the method for the quick fix for the
     * ```
     * [ABSTRACT_SUPER_CALL] Abstract member cannot be accessed directly
     * ```
     * error because the method where this error appears must be ignored.
     */
    context(_: KaSession)
    fun generateHashCode(info: Info, tryToFindHashCodeMethodForClass: Boolean = true): KtNamedFunction? {
        if (info.hashCodeInClass != null) return null

        val klass = info.klass

        val contextMap = mutableMapOf<String, Any?>()
        val hashCodeFunction = if (tryToFindHashCodeMethodForClass) contextOf<KaSession>().findHashCodeMethodForClass(klass.symbol as KaClassSymbol) else null
        contextMap[SUPER_HAS_HASHCODE] = hashCodeFunction != null && (hashCodeFunction.containingSymbol as? KaClassSymbol)?.classId != StandardClassIds.Any

        // Sort variables in `hashCode()` to preserve the same order as in `equals()`
        val sortedVariables = info.variablesForHashCode.sortedWithPrimitiveFirst()

        collectHashCodeContextFromExtensions(contextMap, info)

        val methodText = VelocityGeneratorHelper
            .velocityGenerateCode(klass, sortedVariables,
                contextMap, KotlinEqualsHashCodeTemplatesManager.getInstance().defaultHashcodeTemplate.template, false)
            ?: return null

        val function = KtPsiFactory.contextual(klass).createFunction(methodText)
        setupExpectActualFunction(klass, function)
        return function
    }

    context(_: KaSession)
    private fun collectEqualsContextFromExtensions(contextMap: MutableMap<String, Any?>, info: Info) {
        KotlinEqualsHashCodeGeneratorExtension.getSingleApplicableFor(info.klass)?.let { ext ->
            contextMap.putAll(ext.extraEqualsContext(info.klass))
        }
    }

    context(_: KaSession)
    private fun collectHashCodeContextFromExtensions(contextMap: MutableMap<String, Any?>, info: Info) {
        KotlinEqualsHashCodeGeneratorExtension.getSingleApplicableFor(info.klass)?.let { ext ->
            contextMap.putAll(ext.extraHashCodeContext(info.klass))
        }
    }

    context(_: KaSession)
    fun generateToString(klass: KtClassOrObject, declarations: List<KtNamedDeclaration>, template: String): KtNamedFunction? {

        val contextMap = mutableMapOf<String, Any?>()

        val toStringFunction = contextOf<KaSession>().findToStringMethodForClass(klass.symbol as KaClassSymbol)

        contextMap["generateSuper"] = toStringFunction != null && (toStringFunction.containingSymbol as? KaClassSymbol)?.classId != StandardClassIds.Any

        val methodText = VelocityGeneratorHelper
            .velocityGenerateCode(
                klass, declarations, contextMap,
                template, false) ?: return null


        val function = KtPsiFactory.contextual(klass).createFunction(methodText)

        setupExpectActualFunction(klass, function)

        return function
    }

    context(_: KaSession)
    private fun setupExpectActualFunction(klass: KtClassOrObject, function: KtNamedFunction) {
        if (klass.hasExpectModifier()) {
            (function.bodyExpression ?: function.bodyBlockExpression)?.delete()
        }

        if (klass.hasActualModifier()) {
            val expectClass = klass.expectDeclarationIfAny() as? KtClassOrObject ?: return
            if (expectClass.declarations.any { declaration ->
                declaration is KtNamedFunction &&
                        declaration.name == function.name &&
                        declaration.valueParameters.size == function.valueParameters.size
            }) {
                function.addModifier(KtTokens.ACTUAL_KEYWORD)
            }
        }
    }

    fun generateHashCode(klass: KtClass): String {
        return analyze(klass) {
            val unfilteredVariables = getPropertiesToUseInGeneratedMember(klass, searchInSuper = true)
            val filters = KotlinEqualsHashCodeGeneratorExtension.getSingleApplicableFor(klass)?.memberFilters ?: DefaultMemberFilters
            val variablesForHashCode = unfilteredVariables.filter { filters.isApplicableForHashCodeInClass(it, klass) }
            val hashCode = KotlinEqualsHashCodeTemplatesManager.getInstance().runWithExtensionTemplatesFor(klass) {
                generateHashCode(Info(klass, emptyList(), variablesForHashCode, null, null))!!
            }
            hashCode.text
        }
    }

    fun generateEquals(klass: KtClass): String {
        return analyze(klass) {
            val unfilteredVariables = getPropertiesToUseInGeneratedMember(klass, searchInSuper = true)
            val filters = KotlinEqualsHashCodeGeneratorExtension.getSingleApplicableFor(klass)?.memberFilters ?: DefaultMemberFilters
            val variablesForEquals = unfilteredVariables.filter { filters.isApplicableForEqualsInClass(it, klass) }
            val equals = KotlinEqualsHashCodeTemplatesManager.getInstance().runWithExtensionTemplatesFor(klass) {
                generateEquals(Info(klass, variablesForEquals, emptyList(), null, null))!!
            }
            equals.text
        }
    }

    fun confirmMemberRewrite(
        targetClass: KtClassOrObject, @Nls title: String, vararg declarations: KtNamedDeclaration
    ): Boolean {
        if (isUnitTestMode()) return true

        val functionsText =
            declarations.joinToString { it.name ?: "" }
        val message = KotlinBundle.message("action.generate.functions.already.defined", functionsText, targetClass.name.toString())
        return Messages.showYesNoDialog(
            targetClass.project, message,
            title,
            Messages.getQuestionIcon()
        ) == Messages.YES
    }

}

context(_: KaSession)
private fun List<KtNamedDeclaration>.sortedWithPrimitiveFirst(): List<KtNamedDeclaration> = sortedWith(object : Comparator<KtNamedDeclaration> {
    override fun compare(o1: KtNamedDeclaration, o2: KtNamedDeclaration): Int {
        val isBacking1 = o1.propertyHasBackingField()
        val isBacking2 = o2.propertyHasBackingField()
        val fieldCompare = -isBacking1.compareTo(isBacking2)
        if (fieldCompare != 0) return fieldCompare
        check (o1 is KtDeclarationWithReturnType && o2 is KtDeclarationWithReturnType)
        return -o1.returnType.isPrimitive.compareTo(o2.returnType.isPrimitive)
    }
})

context(_: KaSession)
private fun KtNamedDeclaration.propertyHasBackingField(): Boolean {
    return when (val symbol = symbol) {
        is KaPropertySymbol -> symbol.hasBackingField
        is KaValueParameterSymbol -> {
            symbol.generatedPrimaryConstructorProperty?.hasBackingField == true
        }
        else -> false
    }
}