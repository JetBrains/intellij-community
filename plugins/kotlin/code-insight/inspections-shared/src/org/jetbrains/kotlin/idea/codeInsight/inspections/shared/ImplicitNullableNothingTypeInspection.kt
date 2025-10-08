// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ImplicitNullableNothingTypeInspection : KotlinApplicableInspectionBase.Simple<KtCallableDeclaration, Unit>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            visitTargetElement(function, holder, isOnTheFly)
        }

        override fun visitProperty(property: KtProperty) {
            visitTargetElement(property, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtCallableDeclaration): Boolean {
        if (element.typeReference != null) return false

        val isVarProperty = (element as? KtProperty)?.isVar == true
        val isOpen = element.hasModifier(KtTokens.OPEN_KEYWORD)
        if (!isVarProperty && !isOpen) return false

        return element.nameIdentifier != null
    }

    override fun getApplicableRanges(element: KtCallableDeclaration): List<TextRange> {
        val name = element.nameIdentifier ?: return emptyList()
        return listOf(name.textRangeIn(element))
    }

    override fun KaSession.prepareContext(element: KtCallableDeclaration): Unit? {
        val symbol = element.symbol as? KaCallableSymbol ?: return null

        val returnType = symbol.returnType
        if (!(returnType.isNothingType && returnType.isMarkedNullable)) return null

        if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            if (symbol.allOverriddenSymbols.any { it.returnType.isNothingType && it.returnType.isMarkedNullable }) return null
        }
        return Unit
    }

    override fun getProblemDescription(element: KtCallableDeclaration, context: Unit): String =
        KotlinBundle.message("inspection.implicit.nullable.nothing.type.display.name")

    override fun createQuickFix(
      element: KtCallableDeclaration,
      context: Unit,
    ): KotlinModCommandQuickFix<KtCallableDeclaration> = object : KotlinModCommandQuickFix<KtCallableDeclaration>() {
        override fun getFamilyName(): String =
            if (element is KtNamedFunction) KotlinBundle.message("specify.return.type.explicitly")
            else KotlinBundle.message("specify.type.explicitly")

        override fun applyFix(project: Project, element: KtCallableDeclaration, updater: ModPsiUpdater) {
            val info: CallableReturnTypeUpdaterUtils.TypeInfo = analyze(element) {
              CallableReturnTypeUpdaterUtils.getTypeInfo(element, useSmartCastType = true)
            }
          CallableReturnTypeUpdaterUtils.updateType(element, info, project, updater)
        }
    }
}