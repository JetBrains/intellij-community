// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.isNonNullableBooleanType
import org.jetbrains.kotlin.idea.codeinsight.utils.isNullableAnyType
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateEqualsAndHashcodeAction.Companion.BASE_PARAM_NAME
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateEqualsAndHashcodeAction.Companion.CHECK_PARAMETER_WITH_INSTANCEOF
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateEqualsAndHashcodeAction.Companion.SUPER_HAS_EQUALS
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateEqualsAndHashcodeAction.Companion.SUPER_HAS_HASHCODE
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateEqualsAndHashcodeAction.Companion.SUPER_PARAM_NAME
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectedDeclarationIfAny
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.util.OperatorNameConventions

object GenerateEqualsAndHashCodeUtils {
    context(KaSession)
    fun matchesEqualsMethodSignature(function: KaNamedFunctionSymbol): Boolean {
        if (function.modality == KaSymbolModality.ABSTRACT) return false
        if (function.name != OperatorNameConventions.EQUALS) return false
        if (function.typeParameters.isNotEmpty()) return false
        val param = function.valueParameters.singleOrNull() ?: return false
        val paramType = param.returnType
        val returnType = function.returnType
        return paramType.isNullableAnyType() && returnType.isNonNullableBooleanType()
    }

    context(KaSession)
    fun matchesHashCodeMethodSignature(function: KaNamedFunctionSymbol): Boolean {
        if (function.modality == KaSymbolModality.ABSTRACT) return false
        if (function.name != OperatorNameConventions.HASH_CODE) return false
        if (function.typeParameters.isNotEmpty()) return false
        if (function.valueParameters.isNotEmpty()) return false
        val returnType = function.returnType
        return returnType.isIntType && !returnType.isMarkedNullable
    }

    context(KaSession)
    fun matchesToStringMethodSignature(function: KaNamedFunctionSymbol): Boolean {
        if (function.modality == KaSymbolModality.ABSTRACT) return false
        if (function.name != OperatorNameConventions.TO_STRING) return false
        if (function.typeParameters.isNotEmpty()) return false
        if (function.valueParameters.isNotEmpty()) return false
        val returnType = function.returnType
        return returnType.isStringType && !returnType.isMarkedNullable
    }

    context(KaSession)
    fun findEqualsMethodForClass(classSymbol: KaClassSymbol): KaCallableSymbol? =
        findNonGeneratedMethodInSelfOrSuperclass(classSymbol, OperatorNameConventions.EQUALS) { matchesEqualsMethodSignature(it) }

    context(KaSession)
    fun findToStringMethodForClass(classSymbol: KaClassSymbol): KaCallableSymbol? =
        findNonGeneratedMethodInSelfOrSuperclass(classSymbol, OperatorNameConventions.TO_STRING) { matchesToStringMethodSignature(it) }

    context(KaSession)
    fun findHashCodeMethodForClass(classSymbol: KaClassSymbol): KaCallableSymbol? =
        findNonGeneratedMethodInSelfOrSuperclass(classSymbol, OperatorNameConventions.HASH_CODE) { matchesHashCodeMethodSignature(it) }

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

    context(KaSession)
    fun generateEquals(info: Info): KtNamedFunction? {
        if (info.equalsInClass != null) return null

        val klass = info.klass

        val contextMap = mutableMapOf<String, Any?>()


        val equalsFunction = findEqualsMethodForClass(klass.symbol as KaClassSymbol)

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

        val sortedVariables = info.variablesForEquals.sortedWithPrimitiveFirst()
        val methodText = VelocityGeneratorHelper
            .velocityGenerateCode(klass, sortedVariables, contextMap,
                                  KotlinEqualsHashCodeTemplatesManager.getInstance().defaultEqualsTemplate.template, false) ?: return null


        val function = KtPsiFactory.contextual(klass).createFunction(methodText)

        setupExpectActualFunction(klass, function)
        return function
    }

    context(KaSession)
    fun generateHashCode(info: Info): KtNamedFunction? {
        if (info.hashCodeInClass != null) return null

        val klass = info.klass

        val contextMap = mutableMapOf<String, Any?>()
        val hashCodeFunction = findHashCodeMethodForClass(klass.symbol as KaClassSymbol)
        contextMap[SUPER_HAS_HASHCODE] = hashCodeFunction != null && (hashCodeFunction.containingSymbol as? KaClassSymbol)?.classId != StandardClassIds.Any

        // Sort variables in `hashCode()` to preserve the same order as in `equals()`
        val sortedVariables = info.variablesForHashCode.sortedWithPrimitiveFirst()
        val methodText = VelocityGeneratorHelper
            .velocityGenerateCode(klass, sortedVariables,
                                  contextMap, KotlinEqualsHashCodeTemplatesManager.getInstance().defaultHashcodeTemplate.template, false) ?: return null


        val function = KtPsiFactory.contextual(klass).createFunction(methodText)
        setupExpectActualFunction(klass, function)
        return function
    }

    context(KaSession)
    fun generateToString(klass: KtClassOrObject, declarations: List<KtNamedDeclaration>, template: String): KtNamedFunction? {

        val contextMap = mutableMapOf<String, Any?>()

        val toStringFunction = findToStringMethodForClass(klass.symbol as KaClassSymbol)

        contextMap["generateSuper"] = toStringFunction != null && (toStringFunction.containingSymbol as? KaClassSymbol)?.classId != StandardClassIds.Any

        val methodText = VelocityGeneratorHelper
            .velocityGenerateCode(
                klass, declarations, contextMap,
                template, false) ?: return null


        val function = KtPsiFactory.contextual(klass).createFunction(methodText)

        setupExpectActualFunction(klass, function)

        return function
    }

    context(KaSession)
    private fun setupExpectActualFunction(klass: KtClassOrObject, function: KtNamedFunction) {
        if (klass.hasExpectModifier()) {
            (function.bodyExpression ?: function.bodyBlockExpression)?.delete()
        }

        if (klass.hasActualModifier()) {
            val expectClass = klass.expectedDeclarationIfAny() as? KtClassOrObject ?: return
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
            val variablesForEquals = getPropertiesToUseInGeneratedMember(klass)
            val hashCode = generateHashCode(Info(klass, variablesForEquals, variablesForEquals, null, null))!!
            hashCode.text
        }
    }

    fun generateEquals(klass: KtClass): String {
        return analyze(klass) {
            val variablesForEquals = getPropertiesToUseInGeneratedMember(klass)
            val equals = generateEquals(Info(klass, variablesForEquals, variablesForEquals, null, null))!!
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

context(KaSession)
private fun List<KtNamedDeclaration>.sortedWithPrimitiveFirst(): List<KtNamedDeclaration> = sortedWith(object : Comparator<KtNamedDeclaration> {
    override fun compare(o1: KtNamedDeclaration, o2: KtNamedDeclaration): Int {
        val isBacking1 = o1.propertyHasBackingField()
        val isBacking2 = o2.propertyHasBackingField()
        val fieldCompare = -isBacking1.compareTo(isBacking2)
        if (fieldCompare != 0) return fieldCompare
        return -o1.returnType.isPrimitive.compareTo(o2.returnType.isPrimitive)
    }
})

context(KaSession)
private fun KtNamedDeclaration.propertyHasBackingField(): Boolean {
    val symbol = symbol
    return when (symbol) {
        is KaPropertySymbol -> symbol.hasBackingField
        is KaValueParameterSymbol -> {
            symbol.generatedPrimaryConstructorProperty?.hasBackingField == true
        }
        else -> false
    }
}