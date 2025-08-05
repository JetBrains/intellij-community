// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.util.SlowOperations
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

@ApiStatus.Internal
class ImportQuickFix(
    element: KtElement,
    @IntentionName private val text: String,
    importVariants: List<AutoImportVariant>
) : ImportLikeQuickFix(element, importVariants), HintAction, PriorityAction {
    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun getText(): String = text

    override fun getFamilyName(): String = KotlinBundle.message("fix.import")

    override fun createAutoImportAction(
        editor: Editor,
        file: KtFile,
        filterSuggestions: (Collection<FqName>) -> Collection<FqName>
    ): QuestionAction? {
        val filteredFqNames = filterSuggestions(importVariants.map { it.fqName }).toSet()
        if (filteredFqNames.size != 1) return null

        val singleSuggestion = importVariants.filter { it.fqName in filteredFqNames }.first()
        if ((singleSuggestion as SymbolBasedAutoImportVariant).canNotBeImportedOnTheFly) return null

        return ImportQuestionAction(file.project, editor, file, listOf(singleSuggestion), onTheFly = true)
    }

    override fun showHint(editor: Editor): Boolean {
        val element = element ?: return false
        if (
            ApplicationManager.getApplication().isHeadlessEnvironment
            || HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)
        ) {
            return false
        }

        val file = element.containingKtFile

        val elementRange = element.textRange
        val autoImportHintText = KotlinBundle.message("fix.import.question", importVariants.first().fqName.asString())
        val importAction = createImportAction(editor, file) ?: return false

        HintManager.getInstance().showQuestionHint(
            editor,
            autoImportHintText,
            elementRange.startOffset,
            elementRange.endOffset,
            importAction,
        )

        return true
    }


    override fun fix(importVariant: AutoImportVariant, file: KtFile, project: Project) {
        require(importVariant is SymbolBasedAutoImportVariant)

        StatisticsManager.getInstance().incUseCount(importVariant.statisticsInfo)

        SlowOperations.knownIssue("LLM-15226").use {
            val element = element ?: return

            @OptIn(
                KaAllowAnalysisOnEdt::class,
                KaAllowAnalysisFromWriteAction::class
            )
            val useShortening = allowAnalysisOnEdt {
                allowAnalysisFromWriteAction {
                    analyze(element) {
                        shouldBeImportedWithShortening(element, importVariant)
                    }
                }
            }

            project.executeWriteCommand(QuickFixBundle.message("add.import")) {
                if (useShortening) {
                    (element.mainReference as? KtSimpleNameReference)?.bindToFqName(
                        importVariant.fqName,
                        KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING
                    )
                } else {
                    file.addImport(importVariant.fqName)
                }
            }
        }
    }
    
    context(_: KaSession)
    private fun shouldBeImportedWithShortening(element: KtElement, importVariant: SymbolBasedAutoImportVariant): Boolean {
        if (element !is KtSimpleNameExpression) return false

        // Declarations from the root package cannot be imported by FQN, hence cannot be shortened
        if (importVariant.fqName.isOneSegmentFQN()) return false
        
        val restoredCandidate = importVariant.candidatePointer.restore() ?: return false

        // for class or enum entry we use ShortenReferences because we do not necessarily add an import but may want to
        // insert a partially qualified name
        if (
            restoredCandidate.symbol !is KaClassLikeSymbol &&
            restoredCandidate.symbol !is KaEnumEntrySymbol
        ) {
            return false
        }
        
        // callable references cannot be fully qualified
        if (element.parent is KtCallableReferenceExpression) return false
        
        return true
    }

    override fun isClassDefinitelyPositivelyImportedAlready(containingFile: KtFile, classFqName: FqName): Boolean {
        val importList = containingFile.importList
        if (importList == null) return false
        for (statement in importList.imports) {
            val importRefFqName = statement.importedFqName ?: continue// rely on the optimization: no resolve while getting import statement canonical text
            if (importRefFqName == classFqName) {
                return true
            }
            if (importRefFqName.shortName() == Name.identifier("*") && importRefFqName.parent() == classFqName.parent()) {
                return true
            }
        }
        return false
    }
}