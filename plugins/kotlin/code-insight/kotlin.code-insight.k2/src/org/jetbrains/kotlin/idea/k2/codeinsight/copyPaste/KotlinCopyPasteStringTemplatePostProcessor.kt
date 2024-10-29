// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.EntryUpdateDiff
import org.jetbrains.kotlin.idea.codeinsights.impl.base.changeInterpolationPrefix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

internal class KotlinStringTemplateTransferableData(
    val selectionDataForSelections: List<StringTemplateSelectionData>,
) : TextBlockTransferableData {
    override fun getFlavor(): DataFlavor = dataFlavor

    companion object {
        val dataFlavor: DataFlavor by lazy {
            val transferableDataJavaClass = KotlinStringTemplateTransferableData::class.java
            DataFlavor(
                DataFlavor.javaJVMLocalObjectMimeType + ";class=" + transferableDataJavaClass.name,
                transferableDataJavaClass.simpleName,
                transferableDataJavaClass.classLoader,
            )
        }
    }
}

internal class StringTemplateSelectionData(
    val range: TextRange,
    val prefixLength: Int,
    val selectedText: String,
    val templateText: String,
    val startOffsetRelativeToTemplate: Int,
    val endOffsetRelativeToTemplate: Int,
    val isSingleQuoted: Boolean,
)

internal val LOG = Logger.getInstance(KotlinCopyPasteStringTemplatePostProcessor::class.java)

/**
 * Copy-paste post processor for handling transfers between Kotlin strings.
 * Uses [Transferable] information to update pasted text with respect to different string quoting and interpolation prefixes.
 */
internal class KotlinCopyPasteStringTemplatePostProcessor : CopyPastePostProcessor<KotlinStringTemplateTransferableData>() {
    override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean = false

    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<KotlinStringTemplateTransferableData?> {
        if (file !is KtFile) return emptyList()

        val templateSelectionData = startOffsets.zip(endOffsets).mapNotNull { (start, end) ->
            createSelectionData(file, editor, start, end)
        }
        return listOf(KotlinStringTemplateTransferableData(templateSelectionData))
    }

    // pass info for selections that belong to one string template
    private fun createSelectionData(file: KtFile, editor: Editor, start: Int, end: Int): StringTemplateSelectionData? {
        val range = TextRange(start, end)
        val startKtElement = file.findElementAt(start)?.parentOfType<KtStringTemplateEntry>(withSelf = true) ?: return null
        val endKtElement = file.findElementAt(end - 1)?.parentOfType<KtStringTemplateEntry>(withSelf = true) ?: return null
        val parentStringTemplate = startKtElement.parent as? KtStringTemplateExpression ?: return null
        if (endKtElement.parent !== parentStringTemplate) return null
        val selectedText = editor.document.getText(range)
        val parentTemplateText = parentStringTemplate.text
        val startOffsetRelative = start - parentStringTemplate.startOffset
        val endOffsetRelative = end - parentStringTemplate.startOffset

        return StringTemplateSelectionData(
            range,
            parentStringTemplate.interpolationPrefix?.textLength ?: 0,
            selectedText,
            parentTemplateText,
            startOffsetRelative,
            endOffsetRelative,
            parentStringTemplate.isSingleQuoted(),
        )
    }

    override fun extractTransferableData(content: Transferable): List<KotlinStringTemplateTransferableData?> {
        if (content.isDataFlavorSupported(KotlinStringTemplateTransferableData.dataFlavor)) {
            try {
                return listOf(
                    content.getTransferData(KotlinStringTemplateTransferableData.dataFlavor) as KotlinStringTemplateTransferableData
                )
            } catch (_: IOException) { // fall through if transfer data is unavailable after the isDataFlavorSupported check
            } catch (_: UnsupportedFlavorException) {
            }
        }
        return emptyList()
    }

    /**
     * In cases of the same prefix length and the same quotes return the original text unchanged.
     * If only quotes change, use only preprocessor, though there are known poorly handled cases, such as `trimMargin`.
     * When the prefix lengths are different:
     *  * update interpolation prefixes of the template and its entries
     *  * unescape `$` that no longer require escaping (prefix length increased)
     *  * escape `$` that now require escaping (prefix length decreased — certain old unescaped $ can be misinterpreted as entries)
     *  * handle escaped characters when pasting between strings with different quote lengths
     *
     * Multi-caret cases are not supported — preprocessor results are kept as-is with no specific expectations.
     */
    override fun processTransferableData(
        project: Project,
        editor: Editor,
        bounds: RangeMarker,
        caretOffset: Int,
        indented: Ref<in Boolean>,
        values: List<KotlinStringTemplateTransferableData?>
    ) {
        val stringTemplateData = values.singleOrNull() ?: return
        val document = editor.document
        val targetFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? KtFile ?: return

        val replacementResult = try {
            prepareReplacement(targetFile, bounds, stringTemplateData)
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            LOG.error(e)
            ReplacementResult.KeepPreprocessed
        }

        val replacementText = when (replacementResult) {
            is ReplacementResult.KeepPreprocessed -> return
            is ReplacementResult.KeepOriginal -> replacementResult.originalText
            is ReplacementResult.ReplaceWith -> replacementResult.updatedText
        }

        ApplicationManager.getApplication().runWriteAction {
            document.replaceString(bounds.startOffset, bounds.endOffset, replacementText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    private sealed class ReplacementResult {
        /**
         * @see [org.jetbrains.kotlin.idea.editor.KotlinLiteralCopyPasteProcessor]
         */
        object KeepPreprocessed : ReplacementResult()
        class KeepOriginal(val originalText: String) : ReplacementResult()
        class ReplaceWith(val updatedText: String) : ReplacementResult()
    }

    private fun prepareReplacement(
        file: KtFile,
        bounds: RangeMarker,
        stringTemplateData: KotlinStringTemplateTransferableData
    ): ReplacementResult {
        val startElement = file.findElementAt(bounds.startOffset)
        // Keep preprocessor results when pasting outside string templates
        val destinationStringTemplate = startElement?.parentOfType<KtStringTemplateExpression>(withSelf = true)
            ?: return ReplacementResult.KeepPreprocessed
        // Don't try applying string-to-string pasting logic in multi-caret cases
        val selectionData = stringTemplateData.selectionDataForSelections.singleOrNull() ?: return ReplacementResult.KeepPreprocessed

        val destinationStringPrefixLength = destinationStringTemplate.interpolationPrefix?.textLength ?: 0
        val originalStringPrefixLength = selectionData.prefixLength
        val quotesChanged = selectionData.isSingleQuoted != destinationStringTemplate.isSingleQuoted()

        // return the original text, reverting preprocessor changes, when prefix lengths and quotes are the same
        // copy-pasting from "" to $"" or vise versa follows the same rule
        if (destinationStringPrefixLength == originalStringPrefixLength
            || destinationStringPrefixLength + originalStringPrefixLength == 1
        ) {
            if (quotesChanged) return ReplacementResult.KeepPreprocessed
            else return ReplacementResult.KeepOriginal(selectionData.selectedText)
        }

        return prepareReplacementForDifferentPrefixes(file, selectionData, destinationStringTemplate)
    }

    private fun prepareReplacementForDifferentPrefixes(
        file: KtFile,
        selectionData: StringTemplateSelectionData,
        destinationStringTemplate: KtStringTemplateExpression,
    ): ReplacementResult.ReplaceWith {
        val originalTemplate = KtPsiFactory(file.project).createExpression(selectionData.templateText) as KtStringTemplateExpression

        val nonContentAdjustment = calculateNonContentAdjustment(selectionData, destinationStringTemplate)
        var startOffsetAdjustment = nonContentAdjustment
        var endOffsetAdjustment = nonContentAdjustment

        val replacedTemplate = originalTemplate.changeInterpolationPrefix(
            newPrefixLength = destinationStringTemplate.interpolationPrefix?.textLength ?: 0,
            isSourceSingleQuoted = originalTemplate.isSingleQuoted(),
            isDestinationSingleQuoted = destinationStringTemplate.isSingleQuoted(),
        ) { updateInfo ->
            updateInfo.diffs.forEach { diff ->
                val adjustment = calculateSelectionAdjustmentsForEntryDiff(updateInfo.oldEntry, diff, selectionData)
                startOffsetAdjustment += adjustment.start
                endOffsetAdjustment += adjustment.end
            }
        }

        val newText = replacedTemplate.text.substring(
            selectionData.startOffsetRelativeToTemplate + startOffsetAdjustment,
            selectionData.endOffsetRelativeToTemplate + endOffsetAdjustment
        )
        return ReplacementResult.ReplaceWith(newText)
    }

    private fun calculateNonContentAdjustment(
        selectionData: StringTemplateSelectionData,
        destinationStringTemplate: KtStringTemplateExpression,
    ): Int {
        val originalStringPrefixLength = selectionData.prefixLength
        val destinationStringPrefixLength = destinationStringTemplate.interpolationPrefix?.textLength ?: 0
        val prefixLengthDiff = destinationStringPrefixLength - originalStringPrefixLength
        val isSourceTemplateSingleQuoted = selectionData.isSingleQuoted
        val isDestinationTemplateSingleQuoted = destinationStringTemplate.isSingleQuoted()
        val quoteLengthDiff = when {
            isSourceTemplateSingleQuoted && !isDestinationTemplateSingleQuoted -> 2
            !isSourceTemplateSingleQuoted && isDestinationTemplateSingleQuoted -> -2
            else -> 0
        }

        return prefixLengthDiff + quoteLengthDiff
    }

    private class OffsetAdjustments(
        val start: Int,
        val end: Int,
    ) {
        override fun toString(): String = "(%+d,%+d)".format(start, end)
    }

    private fun calculateSelectionAdjustmentsForEntryDiff(
        oldEntry: KtStringTemplateEntry,
        diff: EntryUpdateDiff,
        selectionData: StringTemplateSelectionData,
    ): OffsetAdjustments {
        val range = diff.oldRange
        val diffStartOffset = oldEntry.startOffsetInParent + range.first
        val diffEndOffset = oldEntry.startOffsetInParent + range.last + 1
        val textLengthDiff = diff.newText.length - diff.oldText.length

        return when {
            // the entire diff entry is to the left of the selection — add the whole text length difference to both borders
            diffEndOffset <= selectionData.startOffsetRelativeToTemplate -> {
                OffsetAdjustments(textLengthDiff, textLengthDiff)
            }
            // the entire diff entry is to the right of the selection — no changes needed
            diffStartOffset >= selectionData.endOffsetRelativeToTemplate -> OffsetAdjustments(0, 0)
            // one or both borders of the selection are inside the entry — adjust to include the whole replacement
            else -> {
                OffsetAdjustments(
                    // for the selection start: get rid of a partial start offset if present
                    -maxOf(selectionData.startOffsetRelativeToTemplate - diffStartOffset, 0),
                    // for the selection end: remove a possible partial selection and add text length diff
                    maxOf(diffEndOffset - selectionData.endOffsetRelativeToTemplate, 0) + textLengthDiff,
                )
            }
        }
    }
}
