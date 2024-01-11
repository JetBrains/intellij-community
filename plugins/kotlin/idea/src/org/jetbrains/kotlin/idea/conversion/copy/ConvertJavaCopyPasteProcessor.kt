// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.refactoring.suggested.range
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.actions.JavaToKotlinAction
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInsight.KotlinCopyPasteReferenceProcessor
import org.jetbrains.kotlin.idea.codeInsight.KotlinReferenceData
import org.jetbrains.kotlin.idea.configuration.ExperimentalFeatures
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.idea.statistics.ConversionType
import org.jetbrains.kotlin.idea.statistics.J2KFusCollector
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.awt.datatransfer.Transferable
import kotlin.system.measureTimeMillis

class ConvertJavaCopyPasteProcessor : CopyPastePostProcessor<TextBlockTransferableData>() {
    private val LOG = Logger.getInstance(ConvertJavaCopyPasteProcessor::class.java)

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

        val data = values.single() as CopiedJavaCode

        val (targetFile, targetBounds, document) = getTargetData(project, editor, caretOffset, bounds) ?: return
        val useNewJ2k = checkUseNewJ2k(targetFile)

        val targetModule = targetFile.module

        if (isNoConversionPosition(targetFile, targetBounds.startOffset)) return

        data class Result(
            val text: String?,
            val referenceData: Collection<KotlinReferenceData>,
            val explicitImports: Set<FqName>,
            val converterContext: ConverterContext?
        )

        val dataForConversion = DataForConversion.prepare(data, project)

        fun doConversion(): Result {
            val result = dataForConversion.elementsAndTexts.convertCodeToKotlin(project, targetModule, useNewJ2k)
            val referenceData = buildReferenceData(result.text, result.parseContext, dataForConversion.importsAndPackage, targetFile)
            val text = if (result.textChanged) result.text else null
            return Result(text, referenceData, result.importsToAdd, result.converterContext)
        }

        fun insertImports(
            bounds: TextRange,
            referenceData: Collection<KotlinReferenceData>,
            explicitImports: Collection<FqName>
        ): TextRange? {
            if (referenceData.isEmpty() && explicitImports.isEmpty()) return bounds

            PsiDocumentManager.getInstance(project).commitDocument(document)

            val rangeMarker = document.createRangeMarker(bounds)
            rangeMarker.isGreedyToLeft = true
            rangeMarker.isGreedyToRight = true

            KotlinCopyPasteReferenceProcessor()
                .processReferenceData(project, editor, targetFile, bounds.startOffset, referenceData.toTypedArray())

            runWriteAction {
                explicitImports.forEach { fqName ->
                    targetFile.resolveImportReference(fqName).firstOrNull()?.let {
                        ImportInsertHelper.getInstance(project).importDescriptor(targetFile, it)
                    }
                }
            }

            return rangeMarker.range
        }

        var conversionResult: Result? = null

        fun doConversionAndInsertImportsIfUnchanged(): Boolean {
            conversionResult = doConversion()

            if (conversionResult!!.text != null) return false

            insertImports(
                targetBounds.range ?: return true,
                conversionResult!!.referenceData,
                conversionResult!!.explicitImports
            )
            return true
        }

        val textLength = data.startOffsets.indices.sumOf { data.endOffsets[it] - data.startOffsets[it] }
        // if the text to convert is short enough, try to do conversion without permission from user and skip the dialog if nothing converted
        if (textLength < 1000 && doConversionAndInsertImportsIfUnchanged()) return

        fun convert() {
            if (conversionResult == null && doConversionAndInsertImportsIfUnchanged()) return
            val (text, referenceData, explicitImports) = conversionResult!!
            text!! // otherwise we should get true from doConversionAndInsertImportsIfUnchanged and return above

            val boundsAfterReplace = runWriteAction {
                val startOffset = targetBounds.startOffset
                document.replaceString(startOffset, targetBounds.endOffset, text)

                val endOffsetAfterCopy = startOffset + text.length
                editor.caretModel.moveToOffset(endOffsetAfterCopy)
                TextRange(startOffset, endOffsetAfterCopy)
            }

            val newBounds = insertImports(boundsAfterReplace, referenceData, explicitImports)

            PsiDocumentManager.getInstance(project).commitDocument(document)
            runPostProcessing(project, targetFile, newBounds, conversionResult?.converterContext, useNewJ2k)

            conversionPerformed = true
        }

        if (confirmConvertJavaOnPaste(project, isPlainText = false)) {
            val conversionTime = measureTimeMillis {
                convert()
            }
            J2KFusCollector.log(
              ConversionType.PSI_EXPRESSION,
              ExperimentalFeatures.NewJ2k.isEnabled,
              conversionTime,
              dataForConversion.elementsAndTexts.linesCount(),
              filesCount = 1
            )
        }
    }

    private fun buildReferenceData(
        text: String,
        parseContext: ParseContext,
        importsAndPackage: String,
        targetFile: KtFile
    ): Collection<KotlinReferenceData> {
        var blockStart: Int
        var blockEnd: Int
        val fileText = buildString {
            append(importsAndPackage)

            val (contextPrefix, contextSuffix) = when (parseContext) {
                ParseContext.CODE_BLOCK -> "fun ${generateDummyFunctionName(text)}() {\n" to "\n}"
                ParseContext.TOP_LEVEL -> "" to ""
            }

            append(contextPrefix)

            blockStart = length
            append(text)
            blockEnd = length

            append(contextSuffix)
        }

        val dummyFile = KtPsiFactory.contextual(targetFile).createFile("dummy.kt", fileText)
        val startOffset = blockStart
        val endOffset = blockEnd
        return KotlinCopyPasteReferenceProcessor().collectReferenceData(dummyFile, intArrayOf(startOffset), intArrayOf(endOffset)).map {
            it.copy(startOffset = it.startOffset - startOffset, endOffset = it.endOffset - startOffset)
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

    companion object {
        @get:TestOnly
        var conversionPerformed: Boolean = false
    }
}

internal class ConversionResult(
    val text: String,
    val parseContext: ParseContext,
    val importsToAdd: Set<FqName>,
    val textChanged: Boolean,
    val converterContext: ConverterContext?
)

internal fun ElementAndTextList.convertCodeToKotlin(project: Project, targetModule: Module?, useNewJ2k: Boolean): ConversionResult {
    val converter =
        J2kConverterExtension.extension(useNewJ2k).createJavaToKotlinConverter(
            project,
            targetModule,
            ConverterSettings.defaultSettings
        )

    val inputElements = toList().filterIsInstance<PsiElement>()
    val (results, _, converterContext) =
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable {
                runReadAction { converter.elementsToKotlin(inputElements) }
            },
            JavaToKotlinAction.Handler.title,
            true,
            project
        )


    val importsToAdd = LinkedHashSet<FqName>()

    var resultIndex = 0
    val convertedCodeBuilder = StringBuilder()
    val originalCodeBuilder = StringBuilder()
    var parseContext: ParseContext? = null
    this.process(object : ElementsAndTextsProcessor {
        override fun processElement(element: PsiElement) {
            val originalText = element.text
            originalCodeBuilder.append(originalText)

            val result = results[resultIndex++]
            if (result != null) {
                convertedCodeBuilder.append(result.text.trimEnd('\n'))
                if (parseContext == null) { // use parse context of the first converted element as parse context for the whole text
                    parseContext = result.parseContext
                }
                importsToAdd.addAll(result.importsToAdd)
            } else { // failed to convert element to Kotlin, insert "as is"
                convertedCodeBuilder.append(originalText)
            }
        }

        override fun processText(string: String) {
            originalCodeBuilder.append(string)
            convertedCodeBuilder.append(string)
        }
    })

    val convertedCode = convertedCodeBuilder.toString()
    val originalCode = originalCodeBuilder.toString()
    return ConversionResult(
        convertedCode,
        parseContext ?: ParseContext.CODE_BLOCK,
        importsToAdd,
        convertedCode != originalCode,
        converterContext
    )
}

internal fun isNoConversionPosition(file: KtFile, offset: Int): Boolean {
    if (offset == 0) return false
    val token = file.findElementAt(offset - 1) ?: return true

    if (token !is PsiWhiteSpace && token.endOffset != offset) return true // pasting into the middle of token

    for (element in token.parentsWithSelf) {
        when (element) {
            is PsiComment -> return element.node.elementType == KtTokens.EOL_COMMENT || offset != element.endOffset
            is KtStringTemplateEntryWithExpression -> return false
            is KtStringTemplateExpression -> return true
        }
    }
    return false
}

internal fun confirmConvertJavaOnPaste(project: Project, isPlainText: Boolean): Boolean {
    if (KotlinEditorOptions.getInstance().isDonTShowConversionDialog) return true

    val dialog = KotlinPasteFromJavaDialog(project, isPlainText)
    dialog.show()
    return dialog.isOK
}


fun ElementAndTextList.linesCount() =
    toList()
        .filterIsInstance<PsiElement>()
        .sumOf { StringUtil.getLineBreakCount(it.text) }

fun checkUseNewJ2k(targetFile: KtFile): Boolean {
    if (targetFile is KtCodeFragment) return false
    return ExperimentalFeatures.NewJ2k.isEnabled
}

fun runPostProcessing(
    project: Project,
    file: KtFile,
    bounds: TextRange?,
    converterContext: ConverterContext?,
    useNewJ2k: Boolean
) {
    val postProcessor = J2kConverterExtension.extension(useNewJ2k).createPostProcessor(formatCode = true)
    if (useNewJ2k) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                val processor =
                    J2kConverterExtension.extension(useNewJ2k = true).createWithProgressProcessor(
                        ProgressManager.getInstance().progressIndicator!!,
                        emptyList(),
                        postProcessor.phasesCount
                    )
                AfterConversionPass(project, postProcessor)
                    .run(
                        file,
                        converterContext,
                        bounds
                    ) { phase, description ->
                        processor.updateState(0, phase, description)
                    }
            },
            @Suppress("DialogTitleCapitalization") KotlinBundle.message("copy.text.convert.java.to.kotlin.title"),
            true,
            project
        )
    } else {
        AfterConversionPass(project, postProcessor)
            .run(
                file,
                converterContext,
                bounds
            )
    }
}