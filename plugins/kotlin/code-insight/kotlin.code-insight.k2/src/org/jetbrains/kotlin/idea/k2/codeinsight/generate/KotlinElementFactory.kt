/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import org.jetbrains.java.generate.element.AbstractElement
import org.jetbrains.java.generate.element.ClassElement
import org.jetbrains.java.generate.element.FieldElement
import org.jetbrains.java.generate.element.MethodElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.buildClassTypeWithStarProjections
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.types.Variance

private const val _NO_NAME_PROVIDED_ = "`<no name provided>`"

/**
 * Kotlin factory for creating [FieldElement] or [ClassElement] objects.
 */
object KotlinElementFactory {
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun newClassElement(clazz: KtClassOrObject): ClassElement {
        val ce = ClassElement()

        // name
        val fqName = clazz.getFqName()
        ce.name = clazz.nameIdentifier?.text ?: fqName?.shortName()?.asString() ?: _NO_NAME_PROVIDED_

        ce.qualifiedName = fqName?.asString()
        ce.isEnum = (clazz as? KtClass)?.isEnum() == true
        ce.isAbstract = clazz.hasModifier(KtTokens.ABSTRACT_KEYWORD)
        ce.typeParams = clazz.getTypeParameters().size

        val classSymbol = clazz.symbol as? KaClassSymbol ?: return ce
        val superSymbols = classSymbol.superTypes.mapNotNull { it.symbol as? KaClassSymbol }
        superSymbols.firstOrNull {
            it.classKind != KaClassKind.INTERFACE
        }?.let {
            ce.superName = it.classId?.asFqNameString()
        }

        // interfaces
        ce.implementNames =
            superSymbols.filter { it.classKind == KaClassKind.INTERFACE }.map { it.classId?.asFqNameString() }.toTypedArray()

        ce.isDeprecated = classSymbol.isDeprecated()
        ce.isException = buildClassTypeWithStarProjections(classSymbol)
            .allSupertypes.any { it is KaClassType && it.classId == StandardClassIds.Throwable }

        return ce
    }

    private fun KaAnnotated.isDeprecated(): Boolean = annotations.contains(StandardClassIds.Annotations.Deprecated)

    context(KaSession)
    fun newFieldElement(property: KtProperty): FieldElement {
        val fe = FieldElement()
        fe.name = property.nameIdentifier?.text ?: _NO_NAME_PROVIDED_
        if (property.hasModifier(KtTokens.CONST_KEYWORD)) fe.isConstant = true

        val propertySymbol = property.symbol as? KaPropertySymbol ?: return fe

        val modifiers = property.modifierList
        if (propertySymbol.annotations.any { it.classId == StandardClassIds.Annotations.Volatile }) {
            fe.isModifierVolatile = true
        }

        setElementInfo(fe, property.returnType, modifiers)

        return fe
    }

    context(KaSession)
    fun newFieldElement(parameter: KtParameter): FieldElement {
        val fe = FieldElement()
        fe.name = parameter.nameIdentifier?.text ?: _NO_NAME_PROVIDED_
        setElementInfo(fe, parameter.returnType, parameter.modifierList)
        return fe
    }

    context(KaSession)
    fun newMethodElement(method: KtNamedFunction): MethodElement {
        val me = MethodElement()
        me.name = method.nameIdentifier?.text ?: _NO_NAME_PROVIDED_

        val type = method.returnType
        val modifiers = method.modifierList

        setElementInfo(me, type, modifiers)

        // misc
        me.isDeprecated = method.symbol.isDeprecated()
        me.isReturnTypeVoid = type.isUnitType

        // modifiers
        if (modifiers?.hasModifier(KtTokens.ABSTRACT_KEYWORD) == true) {
            me.isModifierAbstract = true
        }

        return me
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun setElementInfo(element: AbstractElement, type: KaType, modifiersList: KtModifierList?) {
        val typeSymbol = type.symbol
        element.typeName = typeSymbol?.classId?.shortClassName?.asString()
        element.typeQualifiedName = typeSymbol?.classId?.asFqNameString()
        element.type = type.render(position = Variance.INVARIANT)
        element.isNotNull = !type.canBeNull

        element.isArray = type.isArrayOrPrimitiveArray

        element.isNestedArray = type.isNestedArray
        element.isPrimitive = type.isPrimitive
        element.isByte = type.isByteType
        element.isShort = type.isShortType
        element.isInt = type.isIntType
        element.isLong = type.isLongType
        element.isChar = type.isCharType
        element.isFloat = type.isFloatType
        element.isDouble = type.isDoubleType
        element.isBoolean = type.isBooleanType
        element.isVoid = type.isUnitType
        element.isString = type.isStringType
        element.isObject = type.isAnyType
    }
}