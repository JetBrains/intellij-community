// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonListeners
import com.intellij.codeInsight.daemon.impl.SilentChangeVetoer
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.*
import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightWorkspaceSettings
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.resolve.ImportPath

class KotlinUnusedImportInspection : AbstractKotlinInspection() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<out ProblemDescriptor>? {
        if (file !is KtFile) return null
        val data = analyzeImports(file) ?: return null

        val problems = data.unusedImports.map {
            val fixes = arrayListOf<LocalQuickFix>()
            fixes.add(KotlinOptimizeImportsQuickFix(file))
            if (!KotlinCodeInsightWorkspaceSettings.getInstance(file.project).optimizeImportsOnTheFly) {
                fixes.add(EnableOptimizeImportsOnTheFlyFix(file))
            }

            manager.createProblemDescriptor(
                it,
                KotlinBundle.message("unused.import.directive"),
                isOnTheFly,
                fixes.toTypedArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
        }

        if (isOnTheFly && !isUnitTestMode()) {
            scheduleOptimizeImportsOnTheFly(file, data)
        }

        return problems.toTypedArray()
    }
    private fun analyzeImports(file: KtFile): KotlinOptimizeImportsFacility.ImportData? {
        if (file is KtCodeFragment) return null
        if (!RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(file)) return null

        return KotlinOptimizeImportsFacility.getInstance().analyzeImports(file)
    }

    private fun scheduleOptimizeImportsOnTheFly(file: KtFile, data: KotlinOptimizeImportsFacility.ImportData) {
        if (!KotlinCodeInsightWorkspaceSettings.getInstance(file.project).optimizeImportsOnTheFly) return
        val optimizedImports = KotlinOptimizeImportsFacility.getInstance().prepareOptimizedImports(file, data) ?: return // return if already optimized
        val project = file.project
        val modificationTracker = OuterModelsModificationTrackerManager.getTracker(project)
        val modificationCount = modificationTracker.modificationCount

        val extensionsAllowToChangeFileSilently = SilentChangeVetoer.extensionsAllowToChangeFileSilently(project, file.virtualFile)
        scheduleOptimizeOnDaemonFinished(file) {
            val editor = PsiEditorUtil.findEditor(file)
            val currentModificationCount = modificationTracker.modificationCount
            if (editor != null && currentModificationCount == modificationCount && timeToOptimizeImportsOnTheFly(file, editor, extensionsAllowToChangeFileSilently)) {
              optimizeImportsOnTheFly(file, optimizedImports, editor, project)
            }
        }
    }

    private fun scheduleOptimizeOnDaemonFinished(file: KtFile, action: () -> Unit) {
        val daemonDisposable = Disposer.newDisposable()
        // schedule optimise action after all applyInformation() calls
        val myProject = file.project
        myProject.getMessageBus().connect(daemonDisposable)
            .subscribe<DaemonCodeAnalyzer.DaemonListener>(
                DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
                object : DaemonCodeAnalyzer.DaemonListener {
                    override fun daemonCancelEventOccurred(reason: String) {
                        Disposer.dispose(daemonDisposable)
                    }
                    override fun daemonFinished(incomingFileEditors: Collection<FileEditor>) {
                        Disposer.dispose(daemonDisposable)
                        if ((DaemonCodeAnalyzer.getInstance(myProject) as DaemonCodeAnalyzerEx).isErrorAnalyzingFinished(file)) {
                            // later because should invoke when highlighting is finished (OptimizeImportsFix relies on that)
                            AppUIExecutor.onUiThread().later().expireWith(myProject).withDocumentsCommitted(myProject).execute {
                                if (file.isValid() && file.isWritable()) {
                                    action.invoke()
                                }
                            }
                        } else {
                            scheduleOptimizeOnDaemonFinished(file, action)
                        }
                    }
                })
    }

    private fun timeToOptimizeImportsOnTheFly(file: KtFile, editor: Editor, extensionsAllowToChangeFileSilently: ThreeState): Boolean {
        val project = file.project
        if (project.isDisposed || !file.isValid || editor.isDisposed || !file.isWritable) return false

        // do not optimize imports on the fly during undo/redo
        val undoManager = UndoManager.getInstance(project)
        if (undoManager.isUndoInProgress || undoManager.isRedoInProgress) return false

        // if we stand inside import statements, do not optimize
        val importList = file.importList ?: return false
        val leftSpace = importList.siblings(forward = false, withItself = false).firstOrNull() as? PsiWhiteSpace
        val rightSpace = importList.siblings(forward = true, withItself = false).firstOrNull() as? PsiWhiteSpace
        val left = leftSpace ?: importList
        val right = rightSpace ?: importList
        val importsRange = TextRange(left.textRange.startOffset, right.textRange.endOffset)
        if (importsRange.containsOffset(editor.caretModel.offset)) return false

        val codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project)
        if (!codeAnalyzer.isHighlightingAvailable(file)) return false
        if (!codeAnalyzer.isErrorAnalyzingFinished(file)) return false

        val document = editor.document
        var hasErrors = false
        DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, 0, document.textLength) { highlightInfo ->
            if (!importsRange.containsRange(highlightInfo.startOffset, highlightInfo.endOffset)) {
                hasErrors = true
                false
            } else {
                true
            }
        }
        if (hasErrors) return false

        return DaemonListeners.canChangeFileSilently(file, true/* assume inspections are run on files in content only */,
                                                     extensionsAllowToChangeFileSilently)
    }

    private fun optimizeImportsOnTheFly(file: KtFile, optimizedImports: List<ImportPath>, editor: Editor, project: Project) {
        val documentManager = PsiDocumentManager.getInstance(file.project)
        val doc = documentManager.getDocument(file) ?: editor.document
        documentManager.commitDocument(doc)
        DocumentUtil.writeInRunUndoTransparentAction {
            KotlinOptimizeImportsFacility.getInstance().replaceImports(file, optimizedImports)
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(doc)
        }
    }

    private class EnableOptimizeImportsOnTheFlyFix(file: KtFile) : LocalQuickFixOnPsiElement(file), LowPriorityAction {
        override fun getText(): String = QuickFixBundle.message("enable.optimize.imports.on.the.fly")

        override fun getFamilyName() = name

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS)
            KotlinCodeInsightWorkspaceSettings.getInstance(project).optimizeImportsOnTheFly = true
            OptimizeImportsProcessor(
                project,
                file
            ).run() // we optimize imports manually because on-the-fly import optimization won't work while the caret is in imports
        }
    }
}
