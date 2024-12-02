// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.highlighting.K2UnusedSymbolUtil
import org.jetbrains.kotlin.idea.inspections.describe
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Will work during batch run only. For on-the-fly code, see [org.jetbrains.kotlin.idea.highlighting.KotlinUnusedHighlightingProcessor]
 */
class UnusedSymbolInspection : LocalInspectionTool() {
    // TODO: Having parity between Java and Kotlin might be a good idea once we replace the global Kotlin inspection with a UAST-based one.
    private val javaInspection = UnusedDeclarationInspection()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        if (isOnTheFly) return PsiElementVisitor.EMPTY_VISITOR

        return object: KtVisitorVoid() {
            override fun visitNamedDeclaration(element: KtNamedDeclaration) {
                if (!K2UnusedSymbolUtil.isApplicableByPsi(element) || K2UnusedSymbolUtil.isLocalDeclaration(element)) return
                val message = element.describe()?.let { KotlinBaseHighlightingBundle.message("inspection.message.never.used", it) } ?: return
                val psiToReportProblem = analyze(element) { K2UnusedSymbolUtil.getPsiToReportProblem(element, javaInspection) } ?: return
                holder.registerProblem(psiToReportProblem, message, *K2UnusedSymbolUtil.createQuickFixes(element))
            }
        }
    }
}