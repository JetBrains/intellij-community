// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInsight.KotlinCopyPasteReferenceProcessor
import org.jetbrains.kotlin.idea.codeInsight.KotlinReferenceData
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.ParseContext
import org.jetbrains.kotlin.j2k.ParseContext.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Runs J2K on the pasted code and updates [targetFile] as a side effect.
 * Used by [ConvertJavaCopyPasteProcessor].
 */
class J2KCopyPasteConverter(
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