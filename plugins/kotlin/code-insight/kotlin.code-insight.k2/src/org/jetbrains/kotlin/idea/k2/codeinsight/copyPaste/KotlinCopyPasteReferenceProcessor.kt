// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.project.structure.DanglingFileResolutionMode
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.getFqNameAtOffset
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.copyPaste.KotlinCopyPasteCoroutineScopeService
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException
import org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste.KotlinReferenceRestoringHelper as Helper

class KotlinCopyPasteReferenceProcessor : CopyPastePostProcessor<KotlinReferenceTransferableData>() {
    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<KotlinReferenceTransferableData> {
        if (file !is KtFile || DumbService.getInstance(file.project).isDumb) return listOf()

        check(startOffsets.size == endOffsets.size) {
            "startOffsets ${startOffsets.contentToString()} has to have the same size as endOffsets ${endOffsets.contentToString()}"
        }

        val sourceLocation = getFqNameAtOffset(file, startOffsets.min())?.takeIf { it == getFqNameAtOffset(file, endOffsets.max()) }

        // we need to use copy of the file because references are resolved during the paste phase when the source file might be already
        // changed, e.g., in case of CUT, and not COPY;
        val sourceFileCopy = file.copied()
        val sourceDeclarations = Helper.collectSourceDeclarations(sourceFileCopy, startOffsets, endOffsets)
        val sourceReferences = Helper.collectSourceReferences(sourceFileCopy, startOffsets, endOffsets)

        return listOf(KotlinReferenceTransferableData(sourceReferences, sourceDeclarations.toSet(), sourceFileCopy, sourceLocation))
    }

    override fun extractTransferableData(content: Transferable): List<KotlinReferenceTransferableData> {
        if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE != CodeInsightSettings.NO) {
            try {
                return listOf(content.getTransferData(KotlinReferenceTransferableData.dataFlavor) as KotlinReferenceTransferableData)
            } catch (ignored: UnsupportedFlavorException) {
            } catch (ignored: IOException) {
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
        val (sourceReferencesWithRanges, sourceDeclarations, sourceFileCopy, sourceLocation) = values.single()
        val targetOffset = bounds.startOffset

        if (!isRestoringRequired(sourceFileCopy, sourceLocation, targetFile, targetOffset)) return

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        // Step 1. Find source references in target file and create smart pointers for them.
        // We need to use smart pointers because references are resolved later in a background thread, and at that point the formatting
        // of pasted text is already performed, so it is not possible to rely on text ranges anymore.
        val sourceReferencesInTargetFile = Helper.findSourceReferencesInTargetFile(sourceReferencesWithRanges, targetFile, targetOffset)

        KotlinCopyPasteCoroutineScopeService.getCoroutineScope(project).launch {
            val targetReferencesToRestore = try {
                withBackgroundProgress(project, KotlinBundle.message("copy.paste.resolve.pasted.references"), cancellable = true) {
                    // Step 2. Resolve references in source file.
                    val resolvedSourceReferences = readAction {
                        analyzeCopy(sourceFileCopy, DanglingFileResolutionMode.PREFER_SELF) {
                            Helper.getResolvedSourceReferencesThatMightRequireRestoring(sourceReferencesWithRanges, sourceDeclarations)
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
            } catch (e: CancellationException) {
                emptyList()
            }

            // Step 4. Restore references, i.e. add missing imports or qualifiers.
            withContext(Dispatchers.EDT) {
                // TODO: remove `blockingContext`, see KTIJ-30071
                blockingContext {
                    project.executeCommand(KotlinBundle.message("copy.paste.restore.pasted.references.capitalized")) {
                        buildList {
                            ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(
                                /* title = */ KotlinBundle.message("copy.paste.restore.pasted.references"),
                                /* project = */ project,
                                /* parentComponent = */ null,
                            ) { indicator ->
                                for ((index, referenceToRestore) in targetReferencesToRestore.withIndex()) {
                                    if (indicator.isCanceled) break

                                    Helper.restoreReference(targetFile, referenceToRestore)

                                    add(referenceToRestore)
                                    indicator.fraction = (index + 1).toDouble() / targetReferencesToRestore.size
                                }
                            }
                        }
                    }
                }
            }
        }
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
}
