// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInsight.KotlinCopyPasteReferenceProcessor
import org.jetbrains.kotlin.idea.codeInsight.KotlinReferenceData
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.idea.statistics.ConversionType
import org.jetbrains.kotlin.idea.statistics.J2KFusCollector
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.ParseContext
import org.jetbrains.kotlin.j2k.ParseContext.CODE_BLOCK
import org.jetbrains.kotlin.j2k.ParseContext.TOP_LEVEL
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.awt.datatransfer.Transferable
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance(ConvertJavaCopyPasteProcessor::class.java)
private const val MAX_TEXT_LENGTH_TO_CONVERT_WITHOUT_ASKING_USER = 1000

/**
 * Handles the J2K conversion of copy-pasted code from a Java file into a Kotlin file.
 *
 * See also [ConvertTextJavaCopyPasteProcessor] for the related case of arbitrary code copy-pasted into a Kotlin file.
 *
 * Tests: [org.jetbrains.kotlin.nj2k.NewJavaToKotlinCopyPasteConversionTestGenerated].
 */
class ConvertJavaCopyPasteProcessor : CopyPastePostProcessor<TextBlockTransferableData>() {
    override fun extractTransferableData(content: Transferable): List<TextBlockTransferableData> {
        try {
            if (content.isDataFlavorSupported(CopiedJavaCode.DATA_FLAVOR)) {
                return listOf(content.getTransferData(CopiedJavaCode.DATA_FLAVOR) as TextBlockTransferableData)
            }
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            LOG.error(e)
        }
        return listOf()
    }

    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<TextBlockTransferableData> {
        if (file !is PsiJavaFile) return listOf()
        return listOf(CopiedJavaCode(file.getText()!!, startOffsets, endOffsets))
    }

    override fun processTransferableData(
        project: Project,
        editor: Editor,
        bounds: RangeMarker,
        caretOffset: Int,
        indented: Ref<in Boolean>,
        values: List<TextBlockTransferableData>
    ) {
        if (DumbService.getInstance(project).isDumb) return
        if (!KotlinEditorOptions.getInstance().isEnableJavaToKotlinConversion) return

        val (targetFile, targetBounds, targetDocument) = getTargetData(project, editor.document, caretOffset, bounds) ?: return
        if (!isConversionSupportedAtPosition(targetFile, targetBounds.startOffset)) return

        val copiedJavaCode = values.single() as CopiedJavaCode
        val dataForConversion = DataForConversion.prepare(copiedJavaCode, project)
        val j2kKind = getJ2kKind(targetFile)
        val converter = J2KCopyPasteConverter(project, editor, dataForConversion, j2kKind, targetFile, targetBounds, targetDocument)

        val textLength = copiedJavaCode.startOffsets.indices.sumOf { copiedJavaCode.endOffsets[it] - copiedJavaCode.startOffsets[it] }
        if (textLength < MAX_TEXT_LENGTH_TO_CONVERT_WITHOUT_ASKING_USER && converter.convertAndRestoreReferencesIfTextIsUnchanged()) {
            // If the text to convert is short enough, attempt conversion without
            // requiring user permission and skip the dialog if no conversion was made.
            return
        }

        if (!confirmConvertJavaOnPaste(project, isPlainText = false)) return

        val conversionTime = measureTimeMillis { converter.convert() }
        J2KFusCollector.log(
            type = ConversionType.PSI_EXPRESSION,
            isNewJ2k = j2kKind == K1_NEW,
            conversionTime,
            linesCount = dataForConversion.elementsAndTexts.lineCount(),
            filesCount = 1
        )

        Util.conversionPerformed = true
    }

    object Util {
        @get:TestOnly
        var conversionPerformed: Boolean = false
    }
}

/**
 * Runs J2K on the pasted code and updates [targetFile] as a side effect.
 * Used by [ConvertJavaCopyPasteProcessor].
 */
private class J2KCopyPasteConverter(
    private val project: Project,
    private val editor: Editor,
    private val dataForConversion: DataForConversion,
    private val j2kKind: J2kConverterExtension.Kind,
    private val targetFile: KtFile,
    private val targetBounds: RangeMarker,
    private val targetDocument: Document
) {
    /**
     * @property changedText The transformed Kotlin code, or `null` if no conversion occurred (the result is the same as original code).
     * @property referenceData A list of references within the converted Kotlin code that may need to be processed or resolved.
     * @property importsToAdd A set of fully qualified names to be explicitly imported in the converted Kotlin file.
     */
    private data class Result(
        val changedText: String?,
        val referenceData: List<KotlinReferenceData>,
        val importsToAdd: Set<FqName>,
        val converterContext: ConverterContext?
    )

    private lateinit var result: Result

    fun convert() {
        if (!::result.isInitialized && convertAndRestoreReferencesIfTextIsUnchanged()) return

        val (changedText, referenceData, importsToAdd, converterContext) = result
        checkNotNull(changedText) // the case with unchanged text has already been handled by `convertAndRestoreReferencesIfTextIsUnchanged`

        val endOffsetAfterReplace = targetBounds.startOffset + changedText.length
        val boundsAfterReplace = TextRange(targetBounds.startOffset, endOffsetAfterReplace)
        runWriteAction {
            targetDocument.replaceString(targetBounds.startOffset, targetBounds.endOffset, changedText)
            editor.caretModel.moveToOffset(endOffsetAfterReplace)
        }

        val newBounds = restoreReferencesAndInsertImports(boundsAfterReplace, referenceData, importsToAdd)
        PsiDocumentManager.getInstance(project).commitDocument(targetDocument)
        runPostProcessing(project, targetFile, newBounds, converterContext, j2kKind)
    }

    /**
     * This is a shortcut for copy-pasting trivial code that doesn't need to be converted (for example, a single identifier).
     * In this case, we don't bother showing a J2K dialog and only restore references / insert required imports in the Kotlin file.
     *
     * Always runs the J2K conversion once and saves the result for later reference.
     *
     * @return `true` if the conversion text remains unchanged; `false` otherwise.
     */
    fun convertAndRestoreReferencesIfTextIsUnchanged(): Boolean {
        fun runConversion() {
            val conversionResult = dataForConversion.elementsAndTexts.convertCodeToKotlin(project, targetFile, j2kKind)
            val (text, parseContext, importsToAdd, isTextChanged, converterContext) = conversionResult
            val referenceData = buildReferenceData(text, parseContext, dataForConversion.importsAndPackage, targetFile)
            val changedText = if (isTextChanged) text else null
            result = Result(changedText, referenceData, importsToAdd, converterContext)
        }

        runConversion() // initializes `result`
        if (result.changedText != null) return false
        val boundsTextRange = targetBounds.asTextRange ?: return true
        restoreReferencesAndInsertImports(boundsTextRange, result.referenceData, result.importsToAdd)
        return true
    }

    /**
     * Returns a list of references in the converted Kotlin code that need to be restored (i.e., to become resolved).
     * A reference is restored by qualifying it or adding an import statement (see `KotlinCopyPasteReferenceProcessor`).
     */
    private fun buildReferenceData(
        text: String,
        parseContext: ParseContext,
        importsAndPackage: String,
        targetFile: KtFile
    ): List<KotlinReferenceData> {
        var blockStart: Int
        var blockEnd: Int
        val dummyFileText = buildString {
            val (contextPrefix, contextSuffix) = when (parseContext) {
                CODE_BLOCK -> "fun ${generateDummyFunctionName(text)}() {\n" to "\n}"
                TOP_LEVEL -> "" to ""
            }

            append(importsAndPackage)
            append(contextPrefix)
            blockStart = length
            append(text)
            blockEnd = length
            append(contextSuffix)
        }

        val dummyFile = KtPsiFactory.contextual(targetFile).createFile("dummy.kt", dummyFileText)
        val startOffset = blockStart
        val endOffset = blockEnd
        val referenceDataList =
            KotlinCopyPasteReferenceProcessor().collectReferenceData(dummyFile, intArrayOf(startOffset), intArrayOf(endOffset))

        return referenceDataList.map { data: KotlinReferenceData ->
            data.copy(startOffset = data.startOffset - startOffset, endOffset = data.endOffset - startOffset)
        }
    }

    private fun generateDummyFunctionName(convertedCode: String): String {
        var i = 0
        while (true) {
            val name = "dummy$i"
            if (convertedCode.indexOf(name) < 0) return name
            i++
        }
    }

    /**
     * Restores references and inserts necessary imports for converted code in [bounds].
     * @return The updated TextRange of converted code
     */
    private fun restoreReferencesAndInsertImports(
        bounds: TextRange,
        referenceData: List<KotlinReferenceData>,
        importsToAdd: Collection<FqName>
    ): TextRange? {
        if (referenceData.isEmpty() && importsToAdd.isEmpty()) return bounds
        PsiDocumentManager.getInstance(project).commitDocument(targetDocument)

        val rangeMarker = targetDocument.createRangeMarker(bounds)
        rangeMarker.isGreedyToLeft = true
        rangeMarker.isGreedyToRight = true

        KotlinCopyPasteReferenceProcessor()
            .processReferenceData(project, editor, targetFile, bounds.startOffset, referenceData.toTypedArray())

        val importInsertHelper = ImportInsertHelper.getInstance(project)
        val declarationDescriptorsToImport = importsToAdd.mapNotNull { fqName ->
            targetFile.resolveImportReference(fqName).firstOrNull()
        }
        runWriteAction {
            for (descriptor in declarationDescriptorsToImport) {
                importInsertHelper.importDescriptor(targetFile, descriptor)
            }
        }

        return rangeMarker.asTextRange
    }
}