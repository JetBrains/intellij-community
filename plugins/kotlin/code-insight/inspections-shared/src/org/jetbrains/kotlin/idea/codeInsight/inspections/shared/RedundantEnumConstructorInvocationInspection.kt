// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.diagnostics
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class RedundantEnumConstructorInvocationInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = enumEntryVisitor(fun(enumEntry) {
        val valueArgumentList = enumEntry.valueArgumentListIfEmptyAndHasNoErrors() ?: return
        holder.registerProblem(
            valueArgumentList,
            KotlinBundle.message("redundant.enum.constructor.invocation"),
            RemoveEnumConstructorInvocationFix()
        )
    })
}

private class RemoveEnumConstructorInvocationFix : PsiUpdateModCommandQuickFix() {
    override fun getName(): String = KotlinBundle.message("remove.enum.constructor.invocation.fix.text")

    override fun getFamilyName(): String = name

    override fun applyFix(
        project: Project,
        element: PsiElement,
        updater: ModPsiUpdater
    ) {
        val enumEntries = element.getStrictParentOfType<KtEnumEntry>()?.containingClass()?.body?.getChildrenOfType<KtEnumEntry>() ?: return
        val itemsToDelete = enumEntries.mapNotNull { it.valueArgumentListIfEmptyAndHasNoErrors() }
        itemsToDelete.forEach { it.delete() }
    }
}

private fun KtEnumEntry.valueArgumentListIfEmptyAndHasNoErrors(): KtValueArgumentList? {
    val superTypeCallEntry = initializerList?.initializers?.singleOrNull() as? KtSuperTypeCallEntry ?: return null
    val valueArgumentList = superTypeCallEntry.valueArgumentList ?: return null
    if (valueArgumentList.arguments.isNotEmpty()) return null
    analyze(superTypeCallEntry) {
        if (superTypeCallEntry.hasAnyErrors()) return null
    }
    return valueArgumentList
}

context(_: KaSession)
private fun KtSuperTypeCallEntry.hasAnyErrors(): Boolean {
    val elementsToCheck = listOfNotNull(
        this, // K2 Mode diagnostics
        this.valueArgumentList, // K1 Mode diagnostics
    )

    return elementsToCheck.any { element ->
        @OptIn(KaExperimentalApi::class)
        val diagnostics = element.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        diagnostics.any { it.severity == KaSeverity.ERROR }
    }
}