// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.highlighter.Fe10QuickFixProvider
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.ReplaceObsoleteLabelSyntaxFix
import org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFixBase
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm

class KotlinCleanupInspection : LocalInspectionTool(), CleanupLocalInspectionTool {
    // required to simplify the inspection registration in tests
    override fun getDisplayName(): String = KotlinBundle.message("usage.of.redundant.or.deprecated.syntax.or.deprecated.symbols")

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<out ProblemDescriptor>? {
        if (isOnTheFly || file !is KtFile || !RootKindFilter.projectSources.matches(file)) {
            return null
        }

        val analysisResult = file.analyzeWithAllCompilerChecks()
        if (analysisResult.isError()) {
            return null
        }

        val diagnostics = analysisResult.bindingContext.diagnostics

        val problemDescriptors = arrayListOf<ProblemDescriptor>()

        val importsToRemove = importDirectivesToBeRemoved(file)
        for (import in importsToRemove) {
            val removeImportFix = RemoveImportFix(import)
            val problemDescriptor = createProblemDescriptor(import, removeImportFix.text, listOf(removeImportFix), manager)
            problemDescriptors.add(problemDescriptor)
        }

        file.forEachDescendantOfType<PsiElement> { element ->
            for (diagnostic in diagnostics.forElement(element)) {
                if (diagnostic.isCleanup()) {
                    val fixes = getCleanupFixes(element.project, diagnostic)
                    if (fixes.isNotEmpty()) {
                        problemDescriptors.add(diagnostic.toProblemDescriptor(fixes, file, manager))
                    }
                }
            }
        }

        return problemDescriptors.toTypedArray()
    }

    private fun importDirectivesToBeRemoved(file: KtFile): List<KtImportDirective> {
        if (file.hasAnnotationToSuppressDeprecation()) return emptyList()
        return file.importDirectives.filter { isImportToBeRemoved(it) }
    }

    private fun KtFile.hasAnnotationToSuppressDeprecation(): Boolean {
        val suppressAnnotationEntry = annotationEntries.firstOrNull {
            it.shortName?.asString() == "Suppress"
                    && it.resolveToCall()?.resultingDescriptor?.containingDeclaration?.fqNameSafe == StandardNames.FqNames.suppress
        } ?: return false
        return suppressAnnotationEntry.valueArguments.any {
            val text = (it.getArgumentExpression() as? KtStringTemplateExpression)?.entries?.singleOrNull()?.text ?: return@any false
            text.equals("DEPRECATION", ignoreCase = true)
        }
    }

    private fun isImportToBeRemoved(import: KtImportDirective): Boolean {
        if (import.isAllUnder) return false

        val targetDescriptors = import.targetDescriptors()
        if (targetDescriptors.isEmpty()) return false

        return targetDescriptors.all {
            DeprecatedSymbolUsageFixBase.fetchReplaceWithPattern(it, import.project, null, false) != null
        }
    }

    private fun Diagnostic.isCleanup() = factory in Holder.cleanupDiagnosticsFactories || isObsoleteLabel()

    private object Holder {
        val cleanupDiagnosticsFactories: Collection<DiagnosticFactory<*>> = setOf(
            Errors.MISSING_CONSTRUCTOR_KEYWORD,
            Errors.UNNECESSARY_NOT_NULL_ASSERTION,
            Errors.UNNECESSARY_SAFE_CALL,
            Errors.USELESS_CAST,
            Errors.USELESS_ELVIS,
            ErrorsJvm.POSITIONED_VALUE_ARGUMENT_FOR_JAVA_ANNOTATION,
            Errors.DEPRECATION,
            Errors.DEPRECATION_ERROR,
            Errors.NON_CONST_VAL_USED_IN_CONSTANT_EXPRESSION,
            Errors.OPERATOR_MODIFIER_REQUIRED,
            Errors.INFIX_MODIFIER_REQUIRED,
            Errors.DEPRECATED_TYPE_PARAMETER_SYNTAX,
            Errors.MISPLACED_TYPE_PARAMETER_CONSTRAINTS,
            Errors.COMMA_IN_WHEN_CONDITION_WITHOUT_ARGUMENT,
            ErrorsJs.WRONG_EXTERNAL_DECLARATION,
            Errors.YIELD_IS_RESERVED,
            Errors.DEPRECATED_MODIFIER_FOR_TARGET,
            Errors.DEPRECATED_MODIFIER,
            ErrorsJvm.VALUE_CLASS_WITHOUT_JVM_INLINE_ANNOTATION
        )
    }

    private fun Diagnostic.isObsoleteLabel(): Boolean {
        val annotationEntry = psiElement.getNonStrictParentOfType<KtAnnotationEntry>() ?: return false
        return ReplaceObsoleteLabelSyntaxFix.looksLikeObsoleteLabel(annotationEntry)
    }

    private fun getCleanupFixes(project: Project, diagnostic: Diagnostic): Collection<CleanupFix> {
        val quickFixes = Fe10QuickFixProvider.getInstance(project).createQuickFixes(listOf(diagnostic))
        return quickFixes[diagnostic].filterIsInstance<CleanupFix>()
    }

    private class Wrapper(val intention: IntentionAction) : IntentionWrapper(intention) {
        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            if (intention.isAvailable(
                    project,
                    editor,
                    file
                )
            ) { // we should check isAvailable here because some elements may get invalidated (or other conditions may change)
                super.invoke(project, editor, file)
            }
        }
    }

    private fun Diagnostic.toProblemDescriptor(fixes: Collection<CleanupFix>, file: KtFile, manager: InspectionManager): ProblemDescriptor {
        // TODO: i18n DefaultErrorMessages.render
        @NlsSafe val message = DefaultErrorMessages.render(this)
        return createProblemDescriptor(psiElement, message, fixes, manager)
    }

    private fun createProblemDescriptor(
        element: PsiElement,
        @Nls message: String,
        fixes: Collection<CleanupFix>,
        manager: InspectionManager
    ): ProblemDescriptor {
        return manager.createProblemDescriptor(
            element,
            message,
            false,
            fixes.map { Wrapper(it) }.toTypedArray(),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )
    }

    private class RemoveImportFix(import: KtImportDirective) : KotlinQuickFixAction<KtImportDirective>(import), CleanupFix {
        override fun getFamilyName() = KotlinBundle.message("remove.deprecated.symbol.import")
        @Nls
        override fun getText() = familyName

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            element?.delete()
        }
    }
}
