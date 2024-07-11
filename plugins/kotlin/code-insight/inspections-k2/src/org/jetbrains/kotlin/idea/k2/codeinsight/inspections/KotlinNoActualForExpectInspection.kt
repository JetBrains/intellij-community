// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiElementVisitor.EMPTY_VISITOR
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtVisitorVoid

class KotlinNoActualForExpectInspection : AbstractKotlinInspection() {

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
        return analyze(this) {
            symbol.annotations.any { annotation ->
                annotation.classId == StandardClassIds.Annotations.OptionalExpectation
            }
        }
    }

    private val actualsSearchScopeKey = Key.create<GlobalSearchScope>("actualsSearchScope")
    private fun LocalInspectionToolSession.getActualsSearchScope(module: Module): GlobalSearchScope {
        return getOrCreateUserData(actualsSearchScopeKey) {
            GlobalSearchScope.union((module.implementingModules + module).map { it.moduleScope })
        }
    }

    private val moduleLeavesKey = Key.create<Set<Module>>("moduleLeaves")
    private fun LocalInspectionToolSession.getLeaves(module: Module): Set<Module> {
        return getOrCreateUserData(moduleLeavesKey) {
            module.getLeaves()
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession
    ): PsiElementVisitor {
        val module = holder.file.module ?: return EMPTY_VISITOR
        if (!module.isMultiPlatformModule) return EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitModifierList(list: KtModifierList) {
                val module = list.module ?: return
                val expectModifier = list.getModifier(KtTokens.EXPECT_KEYWORD) ?: return
                val parentDeclaration = list.findParentOfType<KtDeclaration>() ?: return
                if (parentDeclaration.hasOptionalExpectationAnnotation()) return

                val leaves = session.getLeaves(module)
                val foundActuals = parentDeclaration.findAllActualForExpect(session.getActualsSearchScope(module))
                    .mapNotNullTo(mutableSetOf()) { it.element?.module }.toSet()

                val missingActuals = leaves.filter { module ->
                    !module.hasActualInParentOrSelf(foundActuals)
                }
                if (missingActuals.isEmpty()) return

                // We only care about the name of the target, not the common submodule of the MPP module
                val missingModulesWithActuals = missingActuals.joinToString { it.name.substringAfterLast('.') }
                holder.registerProblem(
                    expectModifier, KotlinBundle.message("no.actual.for.expect.declaration", missingModulesWithActuals)
                )
            }
        }
    }
}