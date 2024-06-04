// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtVisitorVoid

class NoActualForExpectInspection : AbstractKotlinInspection() {

    private fun Module.getLeaves(): Set<Module> {
        val implementingModules = implementingModules
        if (implementingModules.isEmpty()) return setOf(this)
        return implementingModules.flatMapTo(mutableSetOf()) { it.getLeaves() }
    }

    private fun Module.hasActualInParentOrSelf(allModulesWithActual: Set<Module>): Boolean {
        return this in allModulesWithActual || implementedModules.any { it in allModulesWithActual }
    }

    private fun KtDeclaration.hasOptionalExpectationAnnotation(): Boolean {
        if (annotationEntries.isEmpty()) return false
        analyze(this) {
            for (entry in annotationEntries) {
                val constructorCall = entry.resolveCall()?.singleConstructorCallOrNull() ?: continue
                val classId = constructorCall.symbol.containingClassId ?: continue
                if (classId == StandardClassIds.Annotations.OptionalExpectation) {
                    return true
                }
            }
        }
        return false
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitModifierList(list: KtModifierList) {
                val module = list.module ?: return
                val expectModifier = list.getModifier(KtTokens.EXPECT_KEYWORD) ?: return
                val parentDeclaration = list.findParentOfType<KtDeclaration>() ?: return
                val leaves = module.getLeaves()

                val foundActuals = parentDeclaration.findAllActualForExpect()
                    .mapNotNullTo(mutableSetOf()) { it.element?.module }.toSet()

                val missingActuals = leaves.filter { module ->
                    !module.hasActualInParentOrSelf(foundActuals)
                }

                if (missingActuals.isEmpty() || parentDeclaration.hasOptionalExpectationAnnotation()) return

                val missingModulesWithActuals = missingActuals.joinToString { it.name.substringAfterLast('.') }
                holder.registerProblem(
                    expectModifier,
                    KotlinBundle.message("no.actual.for.expect.declaration", missingModulesWithActuals)
                )
            }
        }
    }
}