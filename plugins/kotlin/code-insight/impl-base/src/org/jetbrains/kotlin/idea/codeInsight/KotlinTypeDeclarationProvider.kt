// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.psi.*

internal class KotlinTypeDeclarationProvider : TypeDeclarationProvider {
    override fun getSymbolTypeDeclarations(symbol: PsiElement): Array<PsiElement>? {
        if (symbol.containingFile !is KtFile) return null

        return when (symbol) {
            is PsiWhiteSpace -> {
                // Navigate to type of first parameter in lambda, works with the help of KotlinTargetElementEvaluator for the 'it' case
                val lBraceElement = symbol.containingFile.findElementAt(maxOf(symbol.textOffset - 1, 0))
                if (lBraceElement?.text == "{") {
                    (lBraceElement.parent as? KtFunctionLiteral)?.let { return getFunctionalLiteralTarget(it) }
                }
                PsiElement.EMPTY_ARRAY
            }
            is KtFunctionLiteral -> getFunctionalLiteralTarget(symbol)
            is KtTypeReference -> {
                val declaration = symbol.parent
                if (declaration is KtCallableDeclaration && declaration.receiverTypeReference == symbol) {
                    // Navigate to function receiver type, works with the help of KotlinTargetElementEvaluator for the 'this' in extension declaration
                    declaration.getTypeDeclarationFromCallable { callableSymbol -> callableSymbol.receiverType }
                }
                else PsiElement.EMPTY_ARRAY
            }
            is KtCallableDeclaration -> symbol.getTypeDeclarationFromCallable { callableSymbol -> callableSymbol.returnType }
            is KtClassOrObject -> getClassTypeDeclaration(symbol)
            else -> PsiElement.EMPTY_ARRAY
        }
    }

    private fun getFunctionalLiteralTarget(symbol: KtFunctionLiteral): Array<PsiElement> {
        return symbol.getTypeDeclarationFromCallable { callableSymbol ->
            (callableSymbol as? KtFunctionLikeSymbol)?.valueParameters?.firstOrNull()?.returnType ?: callableSymbol.receiverType
        }
    }

    private fun getClassTypeDeclaration(symbol: KtClassOrObject): Array<PsiElement> {
        analyze(symbol) {
            (symbol.getSymbol() as? KtNamedClassOrObjectSymbol)?.psi?.let { return arrayOf(it) }
        }
        return PsiElement.EMPTY_ARRAY
    }

    private fun KtCallableDeclaration.getTypeDeclarationFromCallable(typeFromSymbol: (KtCallableSymbol) -> KtType?): Array<PsiElement> {
        analyze(this) {
            val symbol = getSymbol() as? KtCallableSymbol ?: return PsiElement.EMPTY_ARRAY
            val type = typeFromSymbol(symbol) ?: return PsiElement.EMPTY_ARRAY
             type.expandedClassSymbol?.psi?.let { return arrayOf(it) }
        }
        return PsiElement.EMPTY_ARRAY
    }
}