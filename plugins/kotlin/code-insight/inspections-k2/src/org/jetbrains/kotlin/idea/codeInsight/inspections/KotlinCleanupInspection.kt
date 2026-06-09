// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
import org.jetbrains.kotlin.analysis.api.components.importableFqName
import org.jetbrains.kotlin.analysis.api.components.resolveCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixService
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.k2.codeinsight.fetchReplaceWithPattern
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

@ApiStatus.Internal
class KotlinCleanupInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun getDisplayName(): String = KotlinBundle.message("usage.of.redundant.or.deprecated.syntax.or.deprecated.symbols")

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<out ProblemDescriptor>? {
        if (isOnTheFly || file !is KtFile || !RootKindFilter.projectSources.matches(file)) {
            return null
        }

        val problemDescriptors = analyze(file) {
            buildList {
                for (importDirective in file.importDirectivesToBeRemoved()) {
                    val removeImportFix = RemoveImportFix(importDirective)
                    add(createProblemDescriptor(importDirective, removeImportFix.text, listOf(removeImportFix), manager))
                }

                for (diagnostic in file.collectDiagnostics(ONLY_COMMON_CHECKERS)) {
                    if (!diagnostic.isCleanup()) continue

                    val fixes = getCleanupQuickFix(diagnostic)
                    if (fixes.isNotEmpty()) {
                        add(diagnostic.toProblemDescriptor(fixes, manager))
                    }
                }
            }
        }

        return problemDescriptors.toTypedArray()
    }
}

context(_: KaSession)
private fun KtFile.importDirectivesToBeRemoved(): List<KtImportDirective> {
    if (hasAnnotationToSuppressDeprecation()) return emptyList()
    return importDirectives.filter { it.isImportToBeRemoved() }
}

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun KtFile.hasAnnotationToSuppressDeprecation(): Boolean {
    val suppressAnnotationEntry = annotationEntries.firstOrNull {
        val calleeExpression = it.calleeExpression ?: return@firstOrNull false
        if (it.shortName?.asString() != "Suppress") return@firstOrNull false
        calleeExpression.resolveCall()?.symbol?.importableFqName == StandardNames.FqNames.suppress
    } ?: return false

    return suppressAnnotationEntry.valueArguments.any {
        val text = (it.getArgumentExpression() as? KtStringTemplateExpression)?.entries?.singleOrNull()?.text ?: return@any false
        text.equals("DEPRECATION", ignoreCase = true)
    }
}

context(_: KaSession)
private fun KtImportDirective.isImportToBeRemoved(): Boolean {
    if (isAllUnder) return false

    val symbols = importedReference?.getQualifiedElementSelector()
        ?.mainReference
        ?.resolveToSymbols()
        ?.filterIsInstance<KaDeclarationSymbol>()
        .orEmpty()
    return symbols.isNotEmpty() && symbols.all { fetchReplaceWithPattern(it) != null }
}

private fun KaSession.getCleanupQuickFix(
    diagnostic: KaDiagnosticWithPsi<*>,
): Collection<CleanupFix> = with(KotlinQuickFixService.getInstance()) {
    getQuickFixesFor(diagnostic).filterIsInstance<CleanupFix>()
}

private fun KaDiagnosticWithPsi<*>.toProblemDescriptor(
    fixes: Collection<CleanupFix>,
    manager: InspectionManager,
): ProblemDescriptor {
    @NlsSafe val message = defaultMessage
    return createProblemDescriptor(psi, message, fixes, manager)
}

private class Wrapper(val intention: IntentionAction) : IntentionWrapper(intention) {
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (intention.isAvailable(project, editor, file)) {
            super.invoke(project, editor, file)
        }
    }
}

private fun createProblemDescriptor(
    element: PsiElement,
    @Nls message: String,
    fixes: Collection<CleanupFix>,
    manager: InspectionManager,
): ProblemDescriptor = manager.createProblemDescriptor(
    /* psiElement = */ element,
    /* descriptionTemplate = */ message,
    /* onTheFly = */ false,
    /* fixes = */ fixes.map { Wrapper(it) }.toTypedArray(),
    /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
)

private class RemoveImportFix(import: KtImportDirective) : KotlinQuickFixAction<KtImportDirective>(import), CleanupFix {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("remove.deprecated.symbol.import")

    override fun getText(): @IntentionName String = familyName

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: KtFile,
    ) {
        element?.delete()
    }
}

private fun KaDiagnosticWithPsi<*>.isCleanup(): Boolean = when (this) {
    is KaFirDiagnostic.CommaInWhenConditionWithoutArgument -> true
    is KaFirDiagnostic.Deprecation -> true
    is KaFirDiagnostic.DeprecatedModifier -> true
    is KaFirDiagnostic.DeprecatedModifierForTarget -> true
    is KaFirDiagnostic.DeprecatedTypeParameterSyntax -> true
    is KaFirDiagnostic.DeprecationError -> true
    is KaFirDiagnostic.InfixModifierRequired -> true
    is KaFirDiagnostic.MisplacedTypeParameterConstraints -> true
    is KaFirDiagnostic.MissingConstructorKeyword -> true
    is KaFirDiagnostic.NonConstValUsedInConstantExpression -> true
    is KaFirDiagnostic.OperatorModifierRequired -> true
    is KaFirDiagnostic.PositionedValueArgumentForJavaAnnotation -> true
    is KaFirDiagnostic.UnnecessaryNotNullAssertion -> true
    is KaFirDiagnostic.UnnecessarySafeCall -> true
    is KaFirDiagnostic.UselessCast -> true
    is KaFirDiagnostic.UselessElvis -> true
    is KaFirDiagnostic.ValueClassWithoutJvmInlineAnnotation -> true
    is KaFirDiagnostic.WrongExternalDeclaration -> true
    else -> false
}
