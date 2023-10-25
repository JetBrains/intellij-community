// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.types.Variance
import java.util.*

abstract class TypeInfoBase(val variance: Variance) {
    open fun isEmpty(): Boolean = false
    open fun isOfThis(): Boolean = false
    open val substitutionsAllowed: Boolean = true
    open val staticContextRequired: Boolean = false
}

/**
 * Encapsulates information about a function parameter that is going to be created.
 */
class ParameterInfo(
    val typeInfo: TypeInfoBase,
    val nameSuggestions: List<String>
) {
    constructor(typeInfo: TypeInfoBase, preferredName: String? = null) : this(typeInfo, listOfNotNull(preferredName))
}

enum class CallableKind {
    FUNCTION,
    CLASS_WITH_PRIMARY_CONSTRUCTOR,
    CONSTRUCTOR,
    PROPERTY
}

abstract class CallableInfo(
    val name: String,
    val receiverTypeInfo: TypeInfoBase,
    val returnTypeInfo: TypeInfoBase,
    val possibleContainers: List<KtElement>,
    val typeParameterInfos: List<TypeInfoBase>,
    val isForCompanion: Boolean = false,
    val modifierList: KtModifierList? = null
) {
    abstract val kind: CallableKind
    abstract val parameterInfos: List<ParameterInfo>

    val isAbstract get() = modifierList?.hasModifier(KtTokens.ABSTRACT_KEYWORD) == true

    abstract fun copy(
        receiverTypeInfo: TypeInfoBase = this.receiverTypeInfo,
        possibleContainers: List<KtElement> = this.possibleContainers,
        modifierList: KtModifierList? = this.modifierList
    ): CallableInfo
}

class PropertyInfo(
    name: String,
    receiverTypeInfo: TypeInfoBase,
    returnTypeInfo: TypeInfoBase,
    val writable: Boolean,
    possibleContainers: List<KtElement> = Collections.emptyList(),
    typeParameterInfos: List<TypeInfoBase> = Collections.emptyList(),
    val isLateinitPreferred: Boolean = false,
    val isConst: Boolean = false,
    isForCompanion: Boolean = false,
    val annotations: List<KtAnnotationEntry> = emptyList(),
    modifierList: KtModifierList? = null,
    val initializer: KtExpression? = null
) : CallableInfo(name, receiverTypeInfo, returnTypeInfo, possibleContainers, typeParameterInfos, isForCompanion, modifierList) {
    override val kind: CallableKind get() = CallableKind.PROPERTY
    override val parameterInfos: List<ParameterInfo> get() = Collections.emptyList()

    override fun copy(
        receiverTypeInfo: TypeInfoBase,
        possibleContainers: List<KtElement>,
        modifierList: KtModifierList?
    ) = copyProperty(receiverTypeInfo, possibleContainers, modifierList)

    private fun copyProperty(
        receiverTypeInfo: TypeInfoBase = this.receiverTypeInfo,
        possibleContainers: List<KtElement> = this.possibleContainers,
        modifierList: KtModifierList? = this.modifierList,
        isLateinitPreferred: Boolean = this.isLateinitPreferred
    ) = PropertyInfo(
        name,
        receiverTypeInfo,
        returnTypeInfo,
        writable,
        possibleContainers,
        typeParameterInfos,
        isConst,
        isLateinitPreferred,
        isForCompanion,
        annotations,
        modifierList,
        initializer
    )
}

class FunctionInfo(
    name: String,
    receiverTypeInfo: TypeInfoBase,
    returnTypeInfo: TypeInfoBase,
    possibleContainers: List<KtElement> = Collections.emptyList(),
    override val parameterInfos: List<ParameterInfo> = Collections.emptyList(),
    typeParameterInfos: List<TypeInfoBase> = Collections.emptyList(),
    isForCompanion: Boolean = false,
    modifierList: KtModifierList? = null,
    val preferEmptyBody: Boolean = false
) : CallableInfo(name, receiverTypeInfo, returnTypeInfo, possibleContainers, typeParameterInfos, isForCompanion, modifierList) {
    override val kind: CallableKind get() = CallableKind.FUNCTION

    override fun copy(
        receiverTypeInfo: TypeInfoBase,
        possibleContainers: List<KtElement>,
        modifierList: KtModifierList?
    ) = FunctionInfo(
        name,
        receiverTypeInfo,
        returnTypeInfo,
        possibleContainers,
        parameterInfos,
        typeParameterInfos,
        isForCompanion,
        modifierList
    )
}