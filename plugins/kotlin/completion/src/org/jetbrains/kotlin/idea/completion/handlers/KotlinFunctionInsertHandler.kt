// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionInitializationContext.IDENTIFIER_END_OFFSET
import com.intellij.codeInsight.completion.CompletionInitializationContext.START_OFFSET
import com.intellij.codeInsight.completion.CompositeDeclarativeInsertHandler
import com.intellij.codeInsight.completion.DeclarativeInsertHandler2
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.completion.LambdaSignatureTemplates
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getLastParentOfTypeInRow
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.KotlinType

class GenerateLambdaInfo(val lambdaType: KotlinType, val explicitParameters: Boolean)

class KotlinFunctionCompositeDeclarativeInsertHandler(
    handlers: Map<String, Lazy<DeclarativeInsertHandler2>>,
    fallbackInsertHandler: InsertHandler<LookupElement>?,
    val isLambda: Boolean,
    val inputValueArguments: Boolean,
    val inputTypeArguments: Boolean
) : CompositeDeclarativeInsertHandler(handlers, fallbackInsertHandler) {

    companion object {
        fun withUniversalHandler(
            completionChars: String,
            handler: DeclarativeInsertHandler2.LazyBuilder
        ): CompositeDeclarativeInsertHandler {
            val handlersMap = mapOf(completionChars to handler)
            // it's important not to provide a fallbackInsertHandler here
            return KotlinFunctionCompositeDeclarativeInsertHandler(handlersMap, null, false, false, false)
        }
    }
}

fun createNormalFunctionInsertHandler(
    editor: Editor,
    callType: CallType<*>,
    functionName: Name,
    inputTypeArguments: Boolean,
    inputValueArguments: Boolean,
    argumentText: String = "",
    lambdaInfo: GenerateLambdaInfo? = null,
    argumentsOnly: Boolean = false
): InsertHandler<LookupElement> {
    if (lambdaInfo != null) {
        assert(argumentText == "")
    }

    val chars = editor.document.charsSequence
    val lazyHandlers = mutableMapOf<String, Lazy<DeclarativeInsertHandler2>>()

    // \n - NormalCompletion
    lazyHandlers[Lookup.NORMAL_SELECT_CHAR.toString()] = DeclarativeInsertHandler2.LazyBuilder(holdReadLock = true) { builder ->
        val argumentsStringToInsert = StringBuilder()

        val offset = editor.caretModel.offset
        val insertLambda = lambdaInfo != null
        val openingBracket = if (insertLambda) '{' else '('
        val closingBracket = if (insertLambda) '}' else ')'

        val insertTypeArguments = inputTypeArguments && !(insertLambda && lambdaInfo!!.explicitParameters)
        if (insertTypeArguments) {
            argumentsStringToInsert.append("<>")
            builder.offsetToPutCaret += 1
        }

        var absoluteOpeningBracketOffset = chars.indexOfSkippingSpace(openingBracket, offset)
        var absoluteCloseBracketOffset = absoluteOpeningBracketOffset?.let { chars.indexOfSkippingSpace(closingBracket, it + 1) }
        if (insertLambda && lambdaInfo!!.explicitParameters && absoluteCloseBracketOffset == null) {
            absoluteOpeningBracketOffset = null
        }

        if (absoluteOpeningBracketOffset == null) {
            var lambdaCaseInsideBracketOffset = 0
            var noLambdaCaseInsideBracketOffset = 0
            if (insertLambda) {
                val file = PsiDocumentManager.getInstance(editor.project!!).getPsiFile(editor.document)!!
                if (file.kotlinCustomSettings.INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD) {
                    argumentsStringToInsert.append(" {  }")
                    lambdaCaseInsideBracketOffset = 3
                } else {
                    argumentsStringToInsert.append(" {}")
                    lambdaCaseInsideBracketOffset = 2
                }
            } else {
                argumentsStringToInsert.append("($argumentText)")
                noLambdaCaseInsideBracketOffset = 1
            }
            val shouldPlaceCaretInBrackets = inputValueArguments || lambdaInfo != null
            if (!insertTypeArguments) {
                // no need to insert typeParams, may move cursor around valueParams
                if (shouldPlaceCaretInBrackets) {
                    builder.offsetToPutCaret += noLambdaCaseInsideBracketOffset + lambdaCaseInsideBracketOffset
                    builder.withPopupOptions(DeclarativeInsertHandler2.PopupOptions.ParameterInfo)
                } else {
                    builder.offsetToPutCaret += argumentsStringToInsert.toString().length
                }
            } else {
                // we would love to put caret inside value params, but we can't, cause we have to stay on typeParams first
                // so do nothing here.
            }
        } else if (!(insertLambda && lambdaInfo!!.explicitParameters)) {
            builder.addOperation(absoluteOpeningBracketOffset + 1 - offset, argumentText)
            if (absoluteCloseBracketOffset != null) {
                absoluteCloseBracketOffset += argumentText.length
            }

            if (!insertTypeArguments) {
                builder.offsetToPutCaret = absoluteOpeningBracketOffset + 1 - offset
                val shouldPlaceCaretInBrackets = inputValueArguments || lambdaInfo != null
                if (shouldPlaceCaretInBrackets) {
                    builder.withPopupOptions(DeclarativeInsertHandler2.PopupOptions.ParameterInfo)
                }
            }
        }

        var prefixModificationOperation: DeclarativeInsertHandler2.RelativeTextEdit? = null
        var alreadyHasBackTickInTheEnd = false
        if (!argumentsOnly) {
            val specialSymbols = charArrayOf('_', '`', '~')
            val typedFuzzyName = editor.document.text.subSequence(0, offset)
                .reversed()
                .takeWhile { it.isLetterOrDigit() || specialSymbols.contains(it) }
                .toString()
            val functionStartOffset = offset - typedFuzzyName.length

            /*
                Essentially `normalizedBeforeFunctionOffset' can be reduced to just
                val normalizedBeforeFunctionOffset = 0 - functionName.asString().length

                Though it is not obvious why. Operation offsets are relative to cursor position before insertion. In this
                case it will be offset of lookup element text end - which is functionStartOffset + `functionName.asString().length`.
                NB! asString() and not render(), we rely on knowledge that backticks are not elevated into LookupElement.

                Example:
                we have a function "fun fooBar(i: Int) {}" which we want to call from a string template.
                In editor we have: "$fb<caret>"
                                    |  \
                                    \   \-offset
                                    \- functionStartOffset


                Insert handler will be applied at stage: "$fooBar<caret>", (result of handler application should be "${fooBar(<caret>)}")
                And this new caret position is calculated here as `lookupElementEndPosition`
                So all the offsets should be relative to the new caret position.

                "$fooBar<caret>"
                  \- normalizedBeforeFunctionOffset
             */
            val lookupElementEndPosition =
                functionStartOffset + functionName.asString().length // NB! It's `asString()` on purpose, do not change to `render()`

            val normalizedBeforeFunctionOffset = functionStartOffset - lookupElementEndPosition

            // surroundWithBracesIfInStringTemplate
            run {
                if (functionStartOffset > 0) {
                    if (chars[functionStartOffset - 1] == '$') {
                        // add paranoia check
                        val dollarIsEscaped = (functionStartOffset - 2).let { predollarOffset ->
                            if (predollarOffset >= 0) chars[predollarOffset] == '\\'
                            else false
                        }

                        if (!dollarIsEscaped) {
                            argumentsStringToInsert.append('}')

                            prefixModificationOperation = DeclarativeInsertHandler2.RelativeTextEdit(
                                normalizedBeforeFunctionOffset,
                                normalizedBeforeFunctionOffset,
                                "{"
                            )
                        }
                    }
                }
            }

            // enclosing with backticks
            run {
                if (!functionName.isSpecial) {
                    val renderedName = functionName.render()
                    // it's possible, that nothing typedFuzzyName is empty, and cursor is located after the last symbol in the document,
                    // which means: `functionStartOffset` and `offset` are outside `chars` bounds.
                    val alreadyHasTickAtFront = chars.getOrNull(functionStartOffset) == '`'

                    if (renderedName.firstOrNull() == '`') {
                        alreadyHasBackTickInTheEnd = chars.getOrNull(offset) == '`'

                        // requires backticks
                        if (!alreadyHasTickAtFront) {
                            // backtick is not present already, so need to add it manually
                            prefixModificationOperation = when (val operation = prefixModificationOperation) {
                                null -> DeclarativeInsertHandler2.RelativeTextEdit(
                                    normalizedBeforeFunctionOffset,
                                    normalizedBeforeFunctionOffset,
                                    "`"
                                )
                                else -> operation.copy(newText = operation.newText + '`')
                            }
                        }

                        if (!alreadyHasBackTickInTheEnd) {
                            argumentsStringToInsert.insert(0, "`")
                            builder.offsetToPutCaret += 1
                        }
                    } else {
                        // no backticks required
                        if (alreadyHasTickAtFront) {
                            prefixModificationOperation = when (val operation = prefixModificationOperation) {
                                null -> DeclarativeInsertHandler2.RelativeTextEdit(
                                    normalizedBeforeFunctionOffset,
                                    normalizedBeforeFunctionOffset + 1,
                                    ""
                                )

                                else -> operation.copy(rangeTo = operation.rangeTo + 1) // already insert brace in front, now need to turn insertion into replacement
                            }
                        }
                    }
                }
            }
        }

        prefixModificationOperation?.also { builder.addOperation(it) }
        if (alreadyHasBackTickInTheEnd) {
            builder.addOperation(1, argumentsStringToInsert.toString())
            builder.offsetToPutCaret += 1
        } else {
            builder.addOperation(0, argumentsStringToInsert.toString())
        }

        builder.withPostInsertHandler(InsertHandler<LookupElement> { context, item ->
            // The following code looks hacky:
            // brackets with arguments are already present, so are braces and backticks, and they should be kept.
            // that's why we provide fake context which is adjusted
            // NB: it is important to fork context here and keep the original one intact
            context.forkByOffsetMap().also { forkedContext ->
                val forkedDocument = forkedContext.document
                val newStartOffset = when {
                    forkedDocument.isTextAt(forkedContext.startOffset, "{") -> forkedContext.startOffset + 1
                    forkedContext.startOffset > 0 && forkedDocument.isTextAt(forkedContext.startOffset - 1, "`") ->
                        forkedContext.startOffset - 1

                    else -> forkedContext.startOffset
                }

                val newTailOffset = newStartOffset + functionName.render().length

                forkedContext.offsetMap.addOffset(START_OFFSET, newStartOffset)
                forkedContext.offsetMap.addOffset(IDENTIFIER_END_OFFSET, newTailOffset)
                forkedContext.tailOffset = newTailOffset

                KotlinCallableInsertHandler.addImport(forkedContext, item, callType)

                // hack for KT-31902
                if (callType == CallType.DEFAULT) {
                    val psiDocumentManager = PsiDocumentManager.getInstance(forkedContext.project)

                    forkedContext.file
                        .findElementAt(forkedContext.startOffset)
                        ?.parent?.getLastParentOfTypeInRow<KtDotQualifiedExpression>()
                        ?.createSmartPointer()?.let {
                            psiDocumentManager.commitDocument(forkedDocument)
                            val dotQualifiedExpression = it.element ?: return@let
                            KotlinCallableInsertHandler.SHORTEN_REFERENCES.process(dotQualifiedExpression)
                        }
                }
            }
        })
    }

    val fallbackHandler =
        KotlinFunctionInsertHandler.Normal(callType, inputTypeArguments, inputValueArguments, argumentText, lambdaInfo, argumentsOnly)

    return KotlinFunctionCompositeDeclarativeInsertHandler(
        handlers = lazyHandlers, fallbackInsertHandler = fallbackHandler,
        isLambda = lambdaInfo != null, inputValueArguments = inputValueArguments,
        inputTypeArguments = inputTypeArguments
    )
}

sealed class KotlinFunctionInsertHandler(callType: CallType<*>) : KotlinCallableInsertHandler(callType) {

    class Normal(
        callType: CallType<*>,
        val inputTypeArguments: Boolean,
        val inputValueArguments: Boolean,
        val argumentText: String = "",
        val lambdaInfo: GenerateLambdaInfo? = null,
        val argumentsOnly: Boolean = false
    ) : KotlinFunctionInsertHandler(callType) {
        init {
            if (lambdaInfo != null) {
                assert(argumentText == "")
            }
        }

        //TODO: add 'data' or special annotation when supported
        fun copy(
            callType: CallType<*> = this.callType,
            inputTypeArguments: Boolean = this.inputTypeArguments,
            inputValueArguments: Boolean = this.inputValueArguments,
            argumentText: String = this.argumentText,
            lambdaInfo: GenerateLambdaInfo? = this.lambdaInfo,
            argumentsOnly: Boolean = this.argumentsOnly
        ) = Normal(callType, inputTypeArguments, inputValueArguments, argumentText, lambdaInfo, argumentsOnly)

        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
            val document = context.document

            if (!argumentsOnly) {
                surroundWithBracesIfInStringTemplate(context)

                super.handleInsert(context, item)
            }

            psiDocumentManager.commitDocument(document)
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)

            val startOffset = context.startOffset
            val element = context.file.findElementAt(startOffset) ?: return

            addArguments(context, element)

            // hack for KT-31902
            if (callType == CallType.DEFAULT) {
                context.file
                    .findElementAt(startOffset)
                    ?.parent?.getLastParentOfTypeInRow<KtDotQualifiedExpression>()
                    ?.createSmartPointer()?.let {
                        psiDocumentManager.commitDocument(document)
                        val dotQualifiedExpression = it.element ?: return@let
                        SHORTEN_REFERENCES.process(dotQualifiedExpression)
                    }
            }
        }

        private fun addArguments(context: InsertionContext, offsetElement: PsiElement) {
            val completionChar = context.completionChar
            if (completionChar == '(') { //TODO: more correct behavior related to braces type
                context.setAddCompletionChar(false)
            }

            var offset = context.tailOffset
            val document = context.document
            val editor = context.editor
            val project = context.project
            var chars = document.charsSequence

            val isSmartEnterCompletion = completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR
            val isReplaceCompletion = completionChar == Lookup.REPLACE_SELECT_CHAR
            val isNormalCompletion = completionChar == Lookup.NORMAL_SELECT_CHAR

            val insertLambda = lambdaInfo != null && completionChar != '(' && !(isReplaceCompletion && chars.isCharAt(offset, '('))

            val openingBracket = if (insertLambda) '{' else '('
            val closingBracket = if (insertLambda) '}' else ')'

            var insertTypeArguments = inputTypeArguments && (isNormalCompletion || isReplaceCompletion || isSmartEnterCompletion)

            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            if (isReplaceCompletion) {
                val offset1 = chars.skipSpaces(offset)
                if (offset1 < chars.length) {
                    if (chars[offset1] == '<') {
                        psiDocumentManager.commitDocument(document)
                        val token = context.file.findElementAt(offset1)!!
                        if (token.node.elementType == KtTokens.LT) {
                            val parent = token.parent
                            /* if type argument list is on multiple lines this is more likely wrong parsing*/
                            if (parent is KtTypeArgumentList && parent.getText().indexOf('\n') < 0) {
                                offset = parent.endOffset
                                insertTypeArguments = false
                            }
                        }
                    }
                }
            }

            if (insertLambda && lambdaInfo!!.explicitParameters) {
                insertTypeArguments = false
            }

            if (insertTypeArguments) {
                document.insertString(offset, "<>")
                chars = document.charsSequence
                editor.caretModel.moveToOffset(offset + 1)
                offset += 2
            }

            var openingBracketOffset = chars.indexOfSkippingSpace(openingBracket, offset)
            var closeBracketOffset = openingBracketOffset?.let { chars.indexOfSkippingSpace(closingBracket, it + 1) }
            var inBracketsShift = 0

            if (insertLambda && lambdaInfo!!.explicitParameters && closeBracketOffset == null) {
                openingBracketOffset = null
            }

            if (openingBracketOffset == null) {
                if (insertLambda) {
                    if (completionChar == ' ' || completionChar == '{') {
                        context.setAddCompletionChar(false)
                    }

                    if (isInsertSpacesInOneLineFunctionEnabled(context.file)) {
                        document.insertString(offset, " {  }")
                        inBracketsShift = 1
                    } else {
                        document.insertString(offset, " {}")
                    }
                } else {
                    if (isSmartEnterCompletion) {
                        document.insertString(offset, "(")
                    } else {
                        document.insertString(offset, "()")
                    }
                }
                psiDocumentManager.commitDocument(document)

                openingBracketOffset = document.charsSequence.indexOfSkippingSpace(openingBracket, offset)!!
                closeBracketOffset = document.charsSequence.indexOfSkippingSpace(closingBracket, openingBracketOffset + 1)
            }

            if (insertLambda && lambdaInfo!!.explicitParameters) {
                val placeholderRange = TextRange(openingBracketOffset, closeBracketOffset!! + 1)
                val explicitParameterTypes = LambdaSignatureTemplates.explicitParameterTypesRequired(
                    context,
                    placeholderRange,
                    lambdaInfo.lambdaType,
                )

                LambdaSignatureTemplates.insertTemplate(
                    context,
                    placeholderRange,
                    lambdaInfo.lambdaType,
                    explicitParameterTypes,
                    signatureOnly = false
                )
                return
            }

            document.insertString(openingBracketOffset + 1, argumentText)
            if (closeBracketOffset != null) {
                closeBracketOffset += argumentText.length
            }

            if (!insertTypeArguments) {
                if (shouldPlaceCaretInBrackets(completionChar) || closeBracketOffset == null) {
                    editor.caretModel.moveToOffset(openingBracketOffset + 1 + inBracketsShift)
                    AutoPopupController.getInstance(project)?.autoPopupParameterInfo(editor, offsetElement)
                } else {
                    editor.caretModel.moveToOffset(closeBracketOffset + 1)
                }
            }
        }

        private fun shouldPlaceCaretInBrackets(completionChar: Char): Boolean {
            if (completionChar == ',' || completionChar == '.' || completionChar == '=') return false
            if (completionChar == '(') return true
            return inputValueArguments || lambdaInfo != null
        }

        private fun isInsertSpacesInOneLineFunctionEnabled(file: PsiFile) =
            file.kotlinCustomSettings.INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD
    }

    object Infix : KotlinFunctionInsertHandler(CallType.INFIX) {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            super.handleInsert(context, item)

            if (context.completionChar == ' ') {
                context.setAddCompletionChar(false)
            }

            val tailOffset = context.tailOffset
            context.document.insertString(tailOffset, " ")
            context.editor.caretModel.moveToOffset(tailOffset + 1)
        }
    }

    class OnlyName(callType: CallType<*>) : KotlinFunctionInsertHandler(callType)

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        super.handleInsert(context, item)

        val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
        psiDocumentManager.commitDocument(context.document)
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)
    }
}