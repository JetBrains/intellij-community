// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration

class KotlinAmbiguousActualsInspection : AbstractKotlinInspection() {

    override fun buildVisitor(
      holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession
    ): PsiElementVisitor {

        val module = holder.file.module ?: return PsiElementVisitor.EMPTY_VISITOR
        if (!module.isMultiPlatformModule) return PsiElementVisitor.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                val nameIdentifier = declaration.nameIdentifier ?: return
                if (!declaration.isExpectDeclaration()) return

                val foundActuals = declaration.findAllActualForExpect(compatibleOnly = false).mapNotNull { it.element?.module }.toSet()

                val ambiguous = foundActuals.flatMap { act ->
                    val declaredModules = act.implementedModules.filter { it in foundActuals }
                    if (declaredModules.isEmpty()) emptyList() else buildList {
                        add(act)
                        addAll(declaredModules)
                    }
                }
                if (ambiguous.isNotEmpty()) {
                    holder.registerProblem(nameIdentifier, KotlinBundle.message("ambiguous.actuals.for.0", declaration.name.toString(), ambiguous.joinToString(", ") { it.name }))
                }
            }
        }
    }
}