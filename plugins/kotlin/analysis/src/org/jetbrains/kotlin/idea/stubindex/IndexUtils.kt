/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.psi.stubs.IndexSink
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.stubs.KotlinCallableStubBase
import org.jetbrains.kotlin.psi.stubs.KotlinModifierListStub
import org.jetbrains.kotlin.psi.stubs.KotlinStubWithFqName
import org.jetbrains.kotlin.psi.stubs.KotlinTypeAliasStub
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.util.aliasImportMap

fun <TDeclaration : KtCallableDeclaration> indexTopLevelExtension(stub: KotlinCallableStubBase<TDeclaration>, sink: IndexSink) {
    KotlinTopLevelExtensionsByReceiverTypeIndex.INSTANCE.indexExtension(stub, sink)
}

fun <TDeclaration : KtCallableDeclaration> indexExtensionInObject(stub: KotlinCallableStubBase<TDeclaration>, sink: IndexSink) {
    KotlinExtensionsInObjectsByReceiverTypeIndex.INSTANCE.indexExtension(stub, sink)
}

private fun <TDeclaration : KtCallableDeclaration> KotlinExtensionsByReceiverTypeIndex.indexExtension(
    stub: KotlinCallableStubBase<TDeclaration>,
    sink: IndexSink
) {
    if (!stub.isExtension()) return

    val declaration = stub.psi
    val callableName = declaration.name ?: return
    val containingTypeReference = declaration.receiverTypeReference!!
    containingTypeReference.typeElement?.index(declaration, containingTypeReference) { typeName ->
        sink.occurrence(
            key,
            buildKey(typeName, callableName)
        )
    }
}

fun indexTypeAliasExpansion(stub: KotlinTypeAliasStub, sink: IndexSink) {
    val declaration = stub.psi
    val typeReference = declaration.getTypeReference() ?: return
    val typeElement = typeReference.typeElement ?: return
    typeElement.index(declaration, typeReference) { typeName ->
        sink.occurrence(KotlinTypeAliasByExpansionShortNameIndex.KEY, typeName)
    }
}

private fun KtTypeElement.index(
    declaration: KtTypeParameterListOwner,
    containingTypeReference: KtTypeReference,
    occurrence: (String) -> Unit
) {
    fun KtTypeElement.indexWithVisited(
        declaration: KtTypeParameterListOwner,
        containingTypeReference: KtTypeReference,
        visited: MutableSet<KtTypeElement>,
        occurrence: (String) -> Unit
    ) {
        if (this in visited) return

        visited.add(this)

        when (this) {
            is KtUserType -> {
                val referenceName = referencedName ?: return

                val typeParameter = declaration.typeParameters.firstOrNull { it.name == referenceName }
                if (typeParameter != null) {
                    val bound = typeParameter.extendsBound
                    if (bound != null) {
                        bound.typeElement?.indexWithVisited(declaration, containingTypeReference, visited, occurrence)
                    } else {
                        occurrence("Any")
                    }
                    return
                }

                occurrence(referenceName)

                aliasImportMap()[referenceName].forEach { occurrence(it) }
            }

            is KtNullableType -> innerType?.indexWithVisited(declaration, containingTypeReference, visited, occurrence)

            is KtFunctionType -> {
                val arity = parameters.size + (if (receiverTypeReference != null) 1 else 0)
                val suspendPrefix =
                    if (containingTypeReference.modifierList?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true)
                        "Suspend"
                    else
                        ""
                occurrence("${suspendPrefix}Function$arity")
            }

            is KtDynamicType -> occurrence("Any")

            else -> error("Unsupported type: $this")
        }
    }

    indexWithVisited(declaration, containingTypeReference, mutableSetOf(), occurrence)
}

fun indexInternals(stub: KotlinCallableStubBase<*>, sink: IndexSink) {
    val name = stub.name ?: return

    val modifierListStub = stub.modifierList ?: return

    if (!modifierListStub.hasModifier(KtTokens.INTERNAL_KEYWORD)) return

    if (stub.isTopLevel()) return

    if (modifierListStub.hasModifier(KtTokens.OPEN_KEYWORD) || modifierListStub.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
        sink.occurrence(KotlinOverridableInternalMembersShortNameIndex.Instance.key, name)
    }
}

private val KotlinStubWithFqName<*>.modifierList: KotlinModifierListStub?
    get() = findChildStubByType(KtStubElementTypes.MODIFIER_LIST)

fun <TDeclaration : KtCallableDeclaration> KotlinCallableStubBase<TDeclaration>.isDeclaredInObject(): Boolean {
    if (isTopLevel()) return false
    val containingDeclaration = parentStub?.parentStub?.psi

    return containingDeclaration is KtObjectDeclaration && !containingDeclaration.isObjectLiteral()
}