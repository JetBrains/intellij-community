// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.codeInsight.KotlinReferenceImporterFacility
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixService
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinImportQuickFixAction
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

class K2ReferenceImporterFacility : KotlinReferenceImporterFacility {
    override fun createImportFixesForExpression(expression: KtExpression): Sequence<KotlinImportQuickFixAction<*>> = sequence {
        analyze(expression) {
            val file = expression.containingKtFile
            if (file.hasUnresolvedImportWhichCanImport(expression)) return@sequence

            val quickFixService = KotlinQuickFixService.getInstance()
            val diagnostics = expression
                .getDiagnostics(KtDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)
                .filter { it.severity == Severity.ERROR && expression.textRange in it.psi.textRange }

            for (diagnostic in diagnostics) {
                val importFixes = quickFixService.getImportQuickFixesFor(diagnostic)
                for (importFix in importFixes) {
                    val element = importFix.element ?: continue

                    // obtained quick fix might be intended for an element different from `useSiteElement`, so we need to check again
                    if (!file.hasUnresolvedImportWhichCanImport(element)) {
                        yield(importFix)
                    }
                }
            }
        }
    }
}

context(KtAnalysisSession)
private fun KtFile.hasUnresolvedImportWhichCanImport(element: PsiElement): Boolean {
    if (element !is KtSimpleNameExpression) return false
    val referencedName = element.getReferencedName()

    return importDirectives.any {
        (it.isAllUnder || it.importPath?.importedName?.asString() == referencedName) && !it.isResolved()
    }
}

context(KtAnalysisSession)
private fun KtImportDirective.isResolved(): Boolean {
    val reference = importedReference?.getQualifiedElementSelector()?.mainReference
    return reference?.resolveToSymbol() != null
}