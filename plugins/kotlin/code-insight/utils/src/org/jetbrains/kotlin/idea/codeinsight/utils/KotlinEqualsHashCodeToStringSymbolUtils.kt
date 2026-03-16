// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.isAnyType
import org.jetbrains.kotlin.analysis.api.components.isIntType
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.isStringType
import org.jetbrains.kotlin.analysis.api.components.memberScope
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.util.OperatorNameConventions

@OptIn(KaContextParameterApi::class)
object KotlinEqualsHashCodeToStringSymbolUtils {
    fun KaSession.findEqualsMethodForClass(classSymbol: KaClassSymbol, searchInSuper: Boolean = true): KaCallableSymbol? =
        findMethodForClass(classSymbol, OperatorNameConventions.EQUALS, searchInSuper) { matchesEqualsMethodSignature(it) }

    fun KaSession.findHashCodeMethodForClass(classSymbol: KaClassSymbol, searchInSuper: Boolean = true): KaCallableSymbol? =
        findMethodForClass(classSymbol, OperatorNameConventions.HASH_CODE, searchInSuper) { matchesHashCodeMethodSignature(it) }

    fun KaSession.findToStringMethodForClass(classSymbol: KaClassSymbol, searchInSuper: Boolean = true): KaCallableSymbol? =
        findMethodForClass(classSymbol, OperatorNameConventions.TO_STRING, searchInSuper) { matchesToStringMethodSignature(it) }

    context(_: KaSession)
    fun findMethodForClass(
        classSymbol: KaClassSymbol, methodName: Name, searchInSuper: Boolean, signatureFilter: (KaNamedFunctionSymbol) -> Boolean
    ): KaCallableSymbol? =
        if (searchInSuper) {
            findNonGeneratedMethodInSelfOrSuperclass(classSymbol, methodName, signatureFilter)
        } else {
            findNonGeneratedMethodInSelf(classSymbol, methodName, signatureFilter)
        }

    context(_: KaSession)
    fun getPropertiesToUseInGeneratedMember(classOrObject: KtClassOrObject, searchInSuper: Boolean = false): List<KtNamedDeclaration> {
        return buildList {
            collectOwnDeclaredProperties(classOrObject)
            if (searchInSuper) {
                collectPropertiesFromSuperclasses(classOrObject)
            }
        }.filter {
            it.name?.quoteIfNeeded().isIdentifier()
        }
    }

    context(_: KaSession)
    private fun MutableList<KtNamedDeclaration>.collectOwnDeclaredProperties(classOrObject: KtClassOrObject) {
        classOrObject.primaryConstructorParameters.filterTo(this) { it.hasValOrVar() }
        classOrObject.declarations.asSequence().filterIsInstance<KtProperty>().filterTo(this) {
            it.symbol is KaPropertySymbol
        }
    }

    context(_: KaSession)
    private fun MutableList<KtNamedDeclaration>.collectPropertiesFromSuperclasses(classOrObject: KtClassOrObject) {
        val symbol = classOrObject.symbol
        if (symbol !is KaClassSymbol) return

        for (supertype in symbol.defaultType.allSupertypes) {
            val classSymbol = supertype.symbol as? KaClassSymbol ?: continue
            for (member in classSymbol.declaredMemberScope.declarations) {
                val propertySymbol = member as? KaPropertySymbol ?: continue
                addIfNotNull(propertySymbol.psi as? KtNamedDeclaration)
            }
        }
    }

    /**
     * Searches for a callable member symbol with the given [methodName] that matches the [signatureFilter].
     * If the found symbol is generated, the search is done for one more time in the superclass' scope
     */
    context(_: KaSession)
    private fun findNonGeneratedMethodInSelfOrSuperclass(
        classSymbol: KaClassSymbol,
        methodName: Name,
        signatureFilter: (KaNamedFunctionSymbol) -> Boolean
    ): KaCallableSymbol? {
        val methodSymbol = findNonGeneratedMethodInSelf(classSymbol, methodName, signatureFilter)
        if (methodSymbol != null) return methodSymbol

        // Instead, if a generated member was found, we search for a member again in its parent class' scope to find a relevant member
        val directSuperclassSymbol = findExplicitSuperclassOrAny(classSymbol) ?: return null

        return findMethod(directSuperclassSymbol, methodName, signatureFilter)
    }

    /**
     * Searches for a callable member symbol with the given [methodName] that matches the [signatureFilter] in declared members.
     * Generated data class members are skipped.
     */
    context(_: KaSession)
    private fun findNonGeneratedMethodInSelf(
        classSymbol: KaClassSymbol,
        methodName: Name,
        signatureFilter: (KaNamedFunctionSymbol) -> Boolean
    ): KaCallableSymbol? {
        return findMethod(classSymbol, methodName, signatureFilter)?.takeIf { methodSymbol ->
            // We are not interested in synthetic members of data classes here
            // They won't be generated by the compiler after the explicit members are created (see the Kotlin Specification, 4.1.2)
            methodSymbol.origin != KaSymbolOrigin.SOURCE_MEMBER_GENERATED
        }
    }

    /**
     * Finds methods whose name is [methodName] not only from the class [classSymbol] but also its parent classes,
     * and returns method symbols after filtering them using [condition].
     */
    context(_: KaSession)
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
    context(_: KaSession)
    private fun findExplicitSuperclassOrAny(classSymbol: KaClassSymbol): KaClassSymbol? {
        val supertypes = classSymbol.superTypes
        return supertypes.map { it.symbol }.filterIsInstance<KaClassSymbol>().singleOrNull { it.classKind == KaClassKind.CLASS }
            ?: supertypes.first().allSupertypes.singleOrNull { it.isAnyType }?.symbol as? KaClassSymbol
    }

    context(_: KaSession)
    fun matchesEqualsMethodSignature(function: KaNamedFunctionSymbol): Boolean {
        if (function.modality == KaSymbolModality.ABSTRACT) return false
        if (function.name != OperatorNameConventions.EQUALS) return false
        if (function.typeParameters.isNotEmpty()) return false
        val param = function.valueParameters.singleOrNull() ?: return false
        val paramType = param.returnType
        val returnType = function.returnType
        if (!returnType.isNonNullableBooleanType()) return false
        if (paramType.isNullableAnyType()) return true
        // check for CustomEqualsInValueClasses
        if (function.psi?.languageVersionSettings?.supportsFeature(LanguageFeature.CustomEqualsInValueClasses) != true) return false
        val classSymbol = function.containingDeclaration as? KaNamedClassSymbol ?: return false
        if (!classSymbol.isInline) return false
        return paramType.semanticallyEquals(classSymbol.defaultType)
    }

    context(_: KaSession)
    fun matchesHashCodeMethodSignature(function: KaNamedFunctionSymbol): Boolean {
        if (function.modality == KaSymbolModality.ABSTRACT) return false
        if (function.name != OperatorNameConventions.HASH_CODE) return false
        if (function.typeParameters.isNotEmpty()) return false
        if (function.valueParameters.isNotEmpty()) return false
        val returnType = function.returnType
        return returnType.isIntType && !returnType.isMarkedNullable
    }

    context(_: KaSession)
    fun matchesToStringMethodSignature(function: KaNamedFunctionSymbol): Boolean {
        if (function.modality == KaSymbolModality.ABSTRACT) return false
        if (function.name != OperatorNameConventions.TO_STRING) return false
        if (function.typeParameters.isNotEmpty()) return false
        if (function.valueParameters.isNotEmpty()) return false
        val returnType = function.returnType
        return returnType.isStringType && !returnType.isMarkedNullable
    }
}
