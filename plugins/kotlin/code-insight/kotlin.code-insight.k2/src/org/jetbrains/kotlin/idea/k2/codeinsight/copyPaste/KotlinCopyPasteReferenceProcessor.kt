// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.ReferenceCopyPasteProcessor
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.KotlinCopyPasteActionInfo.declarationsSuggestedToBeImported
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.RestoreReferencesDialog
import org.jetbrains.kotlin.idea.base.codeInsight.copyPaste.ReviewAddedImports.reviewAddedImports
import org.jetbrains.kotlin.idea.base.psi.getFqNameAtOffset
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.copyPaste.KotlinCopyPasteCoroutineScopeService
import org.jetbrains.kotlin.idea.refactoring.createTempCopy
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getSourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException
import org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste.KotlinReferenceRestoringHelper as Helper

class KotlinCopyPasteReferenceProcessor : CopyPastePostProcessor<KotlinReferenceTransferableData>(), ReferenceCopyPasteProcessor {
    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<KotlinReferenceTransferableData> {
        if (file !is KtFile || DumbService.getInstance(file.project).isDumb) return listOf()

        if (file is KtCodeFragment) return listOf()

        check(startOffsets.size == endOffsets.size) {
            "startOffsets ${startOffsets.contentToString()} has to have the same size as endOffsets ${endOffsets.contentToString()}"
        }

        val sourceLocation = getFqNameAtOffset(file, startOffsets.min())?.takeIf { it == getFqNameAtOffset(file, endOffsets.max()) }
        val sourceRanges = startOffsets.zip(endOffsets) { startOffset, endOffset ->
            checkRangeIsProper(startOffset, endOffset, file)
            TextRange(startOffset, endOffset)
        }
        val sourceReferenceInfos = Helper.collectSourceReferenceInfos(file, startOffsets, endOffsets)

        // we need to store text of the file at the moment of CUT/COPY because references are resolved during PASTE
        // when the source file might be already changed, e.g., in case of CUT, and not COPY;
        return listOf(KotlinReferenceTransferableData(file.virtualFile.url, file.text, sourceReferenceInfos, sourceRanges, sourceLocation))
    }

    override fun extractTransferableData(content: Transferable): List<KotlinReferenceTransferableData> {
        if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE != CodeInsightSettings.NO) {
            try {
                return listOf(content.getTransferData(KotlinReferenceTransferableData.dataFlavor) as KotlinReferenceTransferableData)
            } catch (_: UnsupportedFlavorException) {
            } catch (_: IOException) {
            }
        }
        return emptyList()
    }

    override fun processTransferableData(
        project: Project,
        editor: Editor,
        bounds: RangeMarker,
        caretOffset: Int,
        indented: Ref<in Boolean>,
        values: List<KotlinReferenceTransferableData>
    ) {
        if (
            DumbService.getInstance(project).isDumb ||
            CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO
        ) return

        val targetFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile ?: return
        val targetOffset = bounds.startOffset

        val (sourceFileUrl, sourceFileText, sourceReferenceInfos, sourceRanges, sourceLocation) = values.single()

        // creating a copy of the source file at the moment of CUT/COPY can lead to project leak, hence it needs to be created during PASTE
        val sourceFileCopy = (getSourceFile(project, sourceFileUrl) ?: targetFile).createTempCopy(sourceFileText)

        if (!isRestoringRequired(sourceFileCopy, sourceLocation, targetFile, targetOffset)) return

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        // Step 1. Find source references in target file and create smart pointers for them.
        // We need to use smart pointers because references are resolved later in a background thread, and at that point the formatting
        // of pasted text is already performed, so it is not possible to rely on text ranges anymore.
        val sourceReferencesInTargetFile =
            Helper.findSourceReferencesInTargetFile(sourceFileCopy, sourceReferenceInfos, targetFile, targetOffset)

        KotlinCopyPasteCoroutineScopeService.getCoroutineScope(project).launch {
            val targetReferencesToRestore = try {
                withBackgroundProgress(project, KotlinBundle.message("copy.paste.resolve.pasted.references"), cancellable = true) {
                    // Step 2. Resolve references in source file.
                    val resolvedSourceReferences = readAction {
                        analyzeCopy(sourceFileCopy, KaDanglingFileResolutionMode.PREFER_SELF) {
                            Helper.getResolvedSourceReferencesThatMightRequireRestoring(sourceFileCopy, sourceReferenceInfos, sourceRanges)
                        }
                    }

                    checkCanceled()

                    // Step 3. Resolve references in target file and collect the ones that require restoring.
                    val targetReferencesToRestore = readAction {
                        analyze(targetFile) {
                            Helper.getTargetReferencesToRestore(sourceReferencesInTargetFile, resolvedSourceReferences)
                        }
                    }

                    targetReferencesToRestore
                }
            } catch (_: CancellationException) {
                emptyList()
            }

            if (isUnitTestMode()) {
                targetFile.declarationsSuggestedToBeImported = targetReferencesToRestore.toSortedStringSet()
            }

            withContext(Dispatchers.EDT + ModalityState.stateForComponent(editor.component).asContextElement()) {
                // Step 4. If necessary, ask user which references should be restored.
                val askBeforeRestoring = CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK
                val selectedTargetReferencesToRestore = if (askBeforeRestoring && targetReferencesToRestore.isNotEmpty()) {
                    showRestoreReferencesDialog(project, targetReferencesToRestore)
                } else targetReferencesToRestore

                // Step 5. Restore references, i.e. add missing imports or qualifiers.
                val restoredTargetReferences = writeIntentReadAction {
                    project.executeCommand(KotlinBundle.message("copy.paste.restore.pasted.references.capitalized")) {
                        buildList {
                            ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(
                                /* title = */ KotlinBundle.message("copy.paste.restore.pasted.references"),
                                /* project = */ project,
                                /* parentComponent = */ null,
                            ) { indicator ->
                                for ((index, referenceToRestore) in selectedTargetReferencesToRestore.withIndex()) {
                                    if (indicator.isCanceled) break

                                    Helper.restoreReference(targetFile, referenceToRestore)

                                    add(referenceToRestore)
                                    indicator.fraction = (index + 1).toDouble() / selectedTargetReferencesToRestore.size
                                }
                            }
                        }
                    }
                }
                reviewAddedImports(project, editor, targetFile, restoredTargetReferences.toSortedStringSet())
            }
        }
    }

    private fun getSourceFile(project: Project, sourceFileUrl: String): KtFile? {
        val sourceFile = VirtualFileManager.getInstance().findFileByUrl(sourceFileUrl) ?: return null
        if (sourceFile.getSourceRoot(project) == null) return null

        return PsiManager.getInstance(project).findFile(sourceFile) as? KtFile
    }

    /**
     * Restoring is not required if:
     * * code is pasted to the same file and non-local declaration which it was copied from, and
     * * imports in the file remained unchanged
     */
    private fun isRestoringRequired(sourceFileCopy: KtFile, sourceLocation: FqName?, targetFile: KtFile, targetCaretOffset: Int): Boolean {
        if (sourceFileCopy.originalFile != targetFile) return true

        if (sourceLocation == null || sourceLocation != getFqNameAtOffset(targetFile, targetCaretOffset)) return true

        val sourceImports = sourceFileCopy.importDirectives.map { it.text }.toSet()
        val targetImports = targetFile.importDirectives.map { it.text }.toSet()

        return sourceImports != targetImports
    }

    /**
     * @return list of [Helper.ReferenceToRestore]s selected by user
     */
    private fun showRestoreReferencesDialog(
        project: Project,
        targetReferencesToRestore: List<Helper.ReferenceToRestore>
    ): List<Helper.ReferenceToRestore> {
        val fqNames = targetReferencesToRestore.toSortedStringSet()
        val dialog = RestoreReferencesDialog(project, fqNames.toTypedArray())

        dialog.show()

        val selectedFqNames = dialog.selectedElements.toSet()
        return targetReferencesToRestore.filter { selectedFqNames.contains(it.fqName.asString()) }
    }
}
