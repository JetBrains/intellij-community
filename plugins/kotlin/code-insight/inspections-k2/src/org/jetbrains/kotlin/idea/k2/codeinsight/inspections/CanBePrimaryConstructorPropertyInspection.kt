// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.MovePropertyToConstructorIntention
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class CanBePrimaryConstructorPropertyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return propertyVisitor(fun(property) {
            if (property.isLocal) return
            if (property.getter != null || property.setter != null || property.delegate != null) return
            val initializer: KtReferenceExpression = property.initializer as? KtReferenceExpression ?: return
            analyze(initializer) {
                val constructorParamSymbol = initializer.mainReference.resolveToSymbol() as? KtValueParameterSymbol ?: return
                val containingConstructorSymbol = constructorParamSymbol.getContainingSymbol() as? KtConstructorSymbol ?: return
                val containingClassSymbol = containingConstructorSymbol.getContainingSymbol() as? KtNamedClassOrObjectSymbol ?: return
                if (containingClassSymbol.isData) return

                val propertyTypeReference = property.typeReference
                val propertyType = propertyTypeReference?.getKtType()
                if (propertyType != null && propertyType != constructorParamSymbol.returnType) return

                if (property.nameAsName != constructorParamSymbol.name) return

                val constructorParamPsi = constructorParamSymbol.psi as? KtParameter ?: return
                val containingClassPsi = property.containingClass() ?: return
                if (containingClassPsi !== constructorParamPsi.containingClass()) return
                if (containingClassPsi.isInterface()) return
                if (property.hasModifier(KtTokens.OPEN_KEYWORD)
                    && containingClassPsi.isOpen()
                    && constructorParamPsi.isUsedInClassInitializer(containingClassPsi)
                ) return

                holder.registerProblem(
                    property.nameIdentifier ?: return,
                    KotlinBundle.message("property.is.explicitly.assigned.to.parameter.0.can", constructorParamSymbol.name),
                    MovePropertyToConstructorIntention()
                )
            }
        })
    }

    private fun KtClass.isOpen(): Boolean {
        return hasModifier(KtTokens.OPEN_KEYWORD) || hasModifier(KtTokens.ABSTRACT_KEYWORD) || hasModifier(KtTokens.SEALED_KEYWORD)
    }

    private fun KtParameter.isUsedInClassInitializer(containingClass: KtClass): Boolean {
        val classInitializer = containingClass.body?.declarations?.firstIsInstanceOrNull<KtClassInitializer>() ?: return false
        return ReferencesSearch.search(this, LocalSearchScope(classInitializer)).any()
    }
}