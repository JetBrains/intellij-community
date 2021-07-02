// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.handlers

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getLastParentOfTypeInRow
import org.jetbrains.kotlin.types.KotlinType

class GenerateLambdaInfo(val lambdaType: KotlinType, val explicitParameters: Boolean)

fun createNormalFunctionInsertHandler(
    editor: Editor,
    callType: CallType<*>,
    inputTypeArguments: Boolean,
    inputValueArguments: Boolean,
    argumentText: String = "",
    lambdaInfo: GenerateLambdaInfo? = null,
    argumentsOnly: Boolean = false
): InsertHandler<LookupElement> {
    if (lambdaInfo != null) {
        assert(argumentText == "")
    }

    val handlers = mutableMapOf<Char, DeclarativeInsertHandler2>()

    val chars = editor.document.charsSequence


    // \n - NormalCompletion
    run {
        val builder = DeclarativeInsertHandler2.Builder()

        val stringToInsert = StringBuilder()

        val offset = editor.caretModel.offset
        val insertLambda = lambdaInfo != null
        val openingBracket = if (insertLambda) '{' else '('
        val closingBracket = if (insertLambda) '}' else ')'

        val insertTypeArguments = inputTypeArguments && !(insertLambda && lambdaInfo!!.explicitParameters)
        if (insertTypeArguments) {
            stringToInsert.append("<>")
            builder.offsetToPutCaret += 1
        }

        var absoluteOpeningBracketOffset = chars.indexOfSkippingSpace(openingBracket, offset)
        var absoluteCloseBracketOffset = absoluteOpeningBracketOffset?.let { chars.indexOfSkippingSpace(closingBracket, it + 1) }
        if (insertLambda && lambdaInfo!!.explicitParameters && absoluteCloseBracketOffset == null) {
            absoluteOpeningBracketOffset = null
        }

        if (absoluteOpeningBracketOffset == null) {
            if (insertLambda) {
                // todo: get file outside
                val file = PsiDocumentManager.getInstance(editor.project!!).getPsiFile(editor.document)!!
                if (file.kotlinCustomSettings.INSERT_WHITESPACES_IN_SIMPLE_ONE_LINE_METHOD) {
                    builder.offsetToPutCaret = stringToInsert.length + 4
                    stringToInsert.append(" {  }")
                } else {
                    builder.offsetToPutCaret = stringToInsert.length + 3
                    stringToInsert.append(" {}")
                }
            } else {
                builder.offsetToPutCaret = stringToInsert.length + 1
                stringToInsert.append("($argumentText)")
            }
            val shouldPlaceCaretInBrackets = inputValueArguments || lambdaInfo != null
            if (!insertTypeArguments && shouldPlaceCaretInBrackets) {
                builder.withPopupOptions(DeclarativeInsertHandler2.PopupOptions.ParameterInfo)
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
        builder.addOperation(0, stringToInsert.toString())
        builder.withPostInsertHandler(InsertHandler<LookupElement> { context, item ->
            KotlinCallableInsertHandler.addImport(context, item, callType)
        })

        handlers[Lookup.NORMAL_SELECT_CHAR] = builder.build()
    }

    // \t
/*
    run {
        // identifier123| ***
        val replaceInsertHandler = DeclarativeInsertHandler2(
            insertOperations = insertOperations,
            offsetToPutCaret = offsetToPutCaret,
            postInsertHandler = postInsertHandler
        )

        var offset = context.replacementOffset

        val insertLambda = lambdaInfo != null && !chars.isCharAt(offset, '(')
        var insertTypeArguments = inputTypeArguments && !(insertLambda && lambdaInfo!!.explicitParameters)


        val offset1 = chars.skipSpaces(offset)
        if (offset1 < chars.length) {
            if (chars[offset1] == '<') {
                val token = context.file.findElementAt(offset1)!!
                if (token.node.elementType == KtTokens.LT) {
                    val parent = token.parent
                    */
/* if type argument list is on multiple lines this is more likely wrong parsing*//*

                    if (parent is KtTypeArgumentList && parent.getText().indexOf('\n') < 0) {
                        offset = parent.endOffset
                        insertTypeArguments = false
                    }
                }
            }
        }

    }
*/
    // (

    val fallbackHandler = KotlinFunctionInsertHandler.Normal(callType, inputTypeArguments, inputValueArguments, argumentText, lambdaInfo, argumentsOnly)
    return CompositeDeclarativeInsertHandler(handlers = handlers, fallbackInsertHandler = fallbackHandler)
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

            psiDocumentManager.commitAllDocuments()
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

            //val a = intellijIdeaRulezzzz()       <Hello>
            /**
             *
             *
             * <intellijIdeaRulezzzz <List<ll>, List<ll>> >
             *
             * <intellijIdea| <T>| >
             *
             * <intellijIdea|123 <T>| >
             * <intellijIdea1|123 <T>| >
             * <intellijIdea1| <T>| >
             *
             *
             *     declarativeIH = \t: base=replacementOffset;
             *                      retain=ktTypeArgumentList.endOffset - replacementOffset;
             *                      insert (){\n}
             *                      retain 1
             *
             *                      insert=();
             *                      insert={};
             *                      insert=\n;
             *
             *
             *
             *                      retain=7
             *
             *                     \n: base=startOffset+lookupStringLength; insert=()
             */
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
                val explicitParameterTypes =
                    LambdaSignatureTemplates.explicitParameterTypesRequired(context.file as KtFile, placeholderRange, lambdaInfo.lambdaType)
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
        psiDocumentManager.commitAllDocuments()
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)
    }
}