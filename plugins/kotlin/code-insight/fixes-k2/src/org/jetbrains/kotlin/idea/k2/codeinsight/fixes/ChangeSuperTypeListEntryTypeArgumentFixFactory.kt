/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.PropertyTypeMismatchOnOverride
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.ReturnTypeMismatchOnOverride
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry

/**
 * These fixes change the type parameter used to derive from generic class/interface
 * in cases of type mismatch when overriding generic properties/methods from parent class
 *
 * Before the fix:
 *
 * ```kotlin
 *  interface Foo<T> { fun foo(): T }
 *
 *  class FooImpl: Foo<Int> {
 *
 *      override fun foo(): String<caret> = "<3"
 *                          ^^^
 *          **Type mismatch, Int expected**
 *          **Fix: Change type argument to String**
 *  }
 * ```
 *
 * After the fix:
 *
 * ```kotlin
 *  interface Foo<T> { fun foo(): T }
 *
 *  class FooImpl: Foo<String> {
 *                      ^^^
 *           **Updated type parameter**
 *
 *      override fun foo(): String = "<3"
 *  }
 * ```
 */

object ChangeSuperTypeListEntryTypeArgumentFixFactory {
    val changeSuperTypeListEntryTypeArgumentReturnTypeFixFactory = changeSuperTypeListEntry<ReturnTypeMismatchOnOverride>()

    val changeSuperTypeListEntryTypeArgumentPropertyTypeFixFactory = changeSuperTypeListEntry<PropertyTypeMismatchOnOverride>()


    @OptIn(KaExperimentalApi::class)
    private inline fun <reified DIAGNOSTIC : KaDiagnosticWithPsi<KtNamedDeclaration>> changeSuperTypeListEntry() =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: DIAGNOSTIC ->
            val (overrideSymbol, superSymbol) = when (diagnostic) {
                is ReturnTypeMismatchOnOverride -> {
                    diagnostic.function to diagnostic.superFunction
                }

                is PropertyTypeMismatchOnOverride -> {
                    diagnostic.property to diagnostic.superProperty
                }

                else -> null
            } ?: return@ModCommandBased emptyList()


            val actualType = overrideSymbol.returnType.symbol ?: return@ModCommandBased emptyList()
            val shortActualTypeName = actualType.name?.toString() ?: return@ModCommandBased emptyList()
            val fqActualTypeName = actualType.classId?.asFqNameString() ?: return@ModCommandBased emptyList()

            val superSymbolFakeOverrideOriginal = superSymbol.fakeOverrideOriginal
            val superTypeReference = superSymbolFakeOverrideOriginal.returnType
            val superTypeParameterSymbol = (superTypeReference as? KaTypeParameterType)?.symbol ?: return@ModCommandBased emptyList()
            val superClass = (superSymbolFakeOverrideOriginal.containingSymbol as? KaClassLikeSymbol) ?: return@ModCommandBased emptyList()

            val typeParameterIndex = superClass.typeParameters.indexOfFirst { it == superTypeParameterSymbol }
            if (typeParameterIndex < 0) return@ModCommandBased emptyList()

            val derivedClass = (overrideSymbol.containingSymbol as? KaClassSymbol) ?: return@ModCommandBased emptyList()
            val superTypeListEntryIndex = derivedClass.superTypes.indexOfFirst {
                it.symbol == superClass
            }
            if (superTypeListEntryIndex < 0) return@ModCommandBased emptyList()

            val superTypeListEntry = (derivedClass.psi as? KtClass)?.superTypeListEntries?.getOrNull(superTypeListEntryIndex) ?: return@ModCommandBased emptyList()

            listOf(ChangeSuperTypeListEntryTypeArgumentFix(superTypeListEntry, shortActualTypeName, fqActualTypeName, typeParameterIndex))
        }

    private class ChangeSuperTypeListEntryTypeArgumentFix(
        element: KtSuperTypeListEntry,
        private val shortTypeName: String,
        private val fqTypeName: String,
        private val typeArgumentIndex: Int
    ) : PsiUpdateModCommandAction<KtSuperTypeListEntry>(element) {

        override fun getFamilyName() = KotlinBundle.message("fix.change.type.argument", shortTypeName)

        override fun invoke(
            context: ActionContext,
            superTypeListEntry: KtSuperTypeListEntry,
            updater: ModPsiUpdater
        ) {
            val typeArgumentList = superTypeListEntry.typeAsUserType?.typeArgumentList?.arguments?.mapIndexed { index, typeProjection ->
                if (index == typeArgumentIndex) fqTypeName else typeProjection.text
            }?.joinToString(prefix = "<", postfix = ">", separator = ", ") { it } ?: return

            val psiFactory = KtPsiFactory(context.project)
            val newElement = when (superTypeListEntry) {
                is KtSuperTypeEntry -> {
                    val classReference = superTypeListEntry.typeAsUserType?.referenceExpression?.text ?: return
                    psiFactory.createSuperTypeEntry("$classReference$typeArgumentList")
                }

                is KtSuperTypeCallEntry -> {
                    val classReference = superTypeListEntry.calleeExpression.constructorReferenceExpression?.text ?: return
                    val valueArgumentList = superTypeListEntry.valueArgumentList?.text ?: return
                    psiFactory.createSuperTypeCallEntry("$classReference$typeArgumentList$valueArgumentList")
                }

                else -> return
            }

            shortenReferences(superTypeListEntry.replace(newElement) as KtElement)
        }
    }
}
