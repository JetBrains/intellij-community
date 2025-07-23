// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.endLine
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.startLine
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.stubs.elements.KtFunctionElementType

class KotlinFoldingBuilder : CustomFoldingBuilder(), DumbAware {

    private val collectionFactoryFunctionsNames: Set<String> =
        setOf(
            "arrayOf", "booleanArrayOf", "byteArrayOf", "charArrayOf", "doubleArrayOf",
            "floatArrayOf", "intArrayOf", "longArrayOf", "shortArrayOf", "arrayListOf",
            "hashMapOf", "hashSetOf",
            "linkedMapOf", "linkedSetOf", "linkedStringMapOf", "linkedStringSetOf",
            "listOf", "listOfNotNull",
            "mapOf",
            "mutableListOf", "mutableMapOf", "mutableSetOf",
            "setOf",
            "sortedMapOf", "sortedSetOf",
            "stringMapOf", "stringSetOf"
        )

    override fun buildLanguageFoldRegions(
      descriptors: MutableList<FoldingDescriptor>,
      root: PsiElement, document: Document, quick: Boolean
    ) {
        if (root !is PsiFile || root.fileType != KotlinFileType.INSTANCE) return

        val importList = PsiTreeUtil.findChildOfType(root, KtImportList::class.java) ?: return

        val firstImport = importList.imports.firstOrNull()
        if (firstImport != null && importList.imports.size > 1) {
            val importKeyword = firstImport.firstChild

            val startOffset = importKeyword.endOffset + 1
            val endOffset = importList.endOffset

            descriptors.add(FoldingDescriptor(importList, TextRange(startOffset, endOffset)).apply {
                setCanBeRemovedWhenCollapsed(true)
            })
        }

        appendDescriptors(root.node, document, descriptors)
    }

    private fun appendDescriptors(node: ASTNode, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        if (needFolding(node, document)) {
            val textRange = getRangeToFold(node, document)
            val relativeRange = textRange.shiftRight(-node.textRange.startOffset)
            val foldRegionText = node.chars.subSequence(relativeRange.startOffset, relativeRange.endOffset)
            if (StringUtil.countNewLines(foldRegionText) > 0) {
                descriptors.add(FoldingDescriptor(node, textRange))
            }
        }

        var child = node.firstChildNode
        while (child != null) {
            appendDescriptors(child, document, descriptors)
            child = child.treeNext
        }
    }

    private fun needFolding(node: ASTNode, document: Document): Boolean {
        val type = node.elementType
        val parentType = node.treeParent?.elementType

        if (type is KtFunctionElementType) {
            val bodyExpression = (node.psi as? KtNamedFunction)?.bodyExpression
            if (bodyExpression != null && bodyExpression !is KtBlockExpression) return true
        }

        return type == KtNodeTypes.FUNCTION_LITERAL ||
               (type == KtNodeTypes.BLOCK && parentType != KtNodeTypes.FUNCTION_LITERAL && parentType != KtNodeTypes.SCRIPT) ||
               type == KtNodeTypes.CLASS_BODY || type == KtTokens.BLOCK_COMMENT || type == KDocTokens.KDOC ||
               type == KtNodeTypes.STRING_TEMPLATE || type == KtNodeTypes.PRIMARY_CONSTRUCTOR || type == KtNodeTypes.WHEN ||
               node.shouldFoldCall(document)
    }

    private fun ASTNode.shouldFoldCall(document: Document): Boolean {
        val call = (psi as? KtCallExpression)?.takeUnless { it.valueArguments.size < 2 } ?: return false

        return call.startLine(document) != call.endLine(document)
    }

    private fun getRangeToFold(node: ASTNode, document: Document): TextRange {
        if (node.elementType is KtFunctionElementType) {
            val function = node.psi as? KtNamedFunction
            val funKeyword = function?.funKeyword
            val bodyExpression = function?.bodyExpression
            if (funKeyword != null && bodyExpression != null && bodyExpression !is KtBlockExpression) {
                if (funKeyword.startLine(document) != bodyExpression.startLine(document)) {
                    val lineBreak = bodyExpression.siblings(forward = false, withItself = false).firstOrNull { "\n" in it.text }
                    if (lineBreak != null) {
                        return TextRange(lineBreak.startOffset, bodyExpression.endOffset)
                    }
                }
                return bodyExpression.textRange
            }
        }

        if (node.elementType == KtNodeTypes.FUNCTION_LITERAL) {
            val psi = node.psi as? KtFunctionLiteral
            val lbrace = psi?.lBrace
            val rbrace = psi?.rBrace
            if (lbrace != null && rbrace != null) {
                return TextRange(lbrace.startOffset, rbrace.endOffset)
            }
        }

        if (node.elementType == KtNodeTypes.CALL_EXPRESSION) {
            val valueArgumentList = (node.psi as? KtCallExpression)?.valueArgumentList
            val leftParenthesis = valueArgumentList?.leftParenthesis
            val rightParenthesis = valueArgumentList?.rightParenthesis
            if (leftParenthesis != null && rightParenthesis != null) {
                return TextRange(leftParenthesis.startOffset, rightParenthesis.endOffset)
            }
        }

        if (node.elementType == KtNodeTypes.WHEN) {
            val whenExpression = node.psi as? KtWhenExpression
            val openBrace = whenExpression?.openBrace
            val closeBrace = whenExpression?.closeBrace
            if (openBrace != null && closeBrace != null) {
                return TextRange(openBrace.startOffset, closeBrace.endOffset)
            }
        }

        return node.textRange
    }

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String = when {
        node.elementType == KtTokens.BLOCK_COMMENT -> "/${getFirstLineOfComment(node)}.../"
        node.elementType == KDocTokens.KDOC -> "/**${getFirstLineOfComment(node)}...*/"
        node.elementType == KtNodeTypes.STRING_TEMPLATE -> "\"\"\"${getTrimmedFirstLineOfString(node).addSpaceIfNeeded()}...\"\"\""
      node.elementType == KtNodeTypes.PRIMARY_CONSTRUCTOR || node.elementType == KtNodeTypes.CALL_EXPRESSION -> "(...)"
        node.psi is KtImportList -> "..."
        else -> "{...}"
    }

    private fun getTrimmedFirstLineOfString(node: ASTNode): String {
        val lines = node.text.split("\n")
        val firstLine = lines.asSequence().map { it.replace("\"\"\"", "").trim() }.firstOrNull(String::isNotEmpty)
        return firstLine ?: ""
    }

    private fun String.addSpaceIfNeeded(): String {
        if (isEmpty() || endsWith(" ")) return this
        return "$this "
    }

    private fun getFirstLineOfComment(node: ASTNode): String {
        val targetCommentLine = node.text.split("\n").firstOrNull {
            getCommentContents(it).isNotEmpty()
        } ?: return ""
        return " ${getCommentContents(targetCommentLine)} "
    }

    private fun getCommentContents(line: String): String {
        return line.trim()
            .removePrefix("/**")
            .removePrefix("/*")
            .removePrefix("*/")
            .removePrefix("*")
            .trim()
    }

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
        val settings = JavaCodeFoldingSettings.getInstance()

        if (node.psi is KtImportList) {
            return settings.isCollapseImports
        }

        val type = node.elementType
        if (type == KtTokens.BLOCK_COMMENT || type == KDocTokens.KDOC) {
            if (isFirstElementInFile(node.psi)) {
                return settings.isCollapseFileHeader
            }
        }

        return false
    }

    override fun isCustomFoldingRoot(node: ASTNode) = node.elementType == KtNodeTypes.BLOCK || node.elementType == KtNodeTypes.CLASS_BODY

    private fun isFirstElementInFile(element: PsiElement): Boolean {
        val parent = element.parent
        if (parent is PsiFile) {
            val firstNonWhiteSpace = parent.allChildren.firstOrNull {
                it.textLength != 0 && it !is PsiWhiteSpace
            }

            return element == firstNonWhiteSpace
        }

        return false
    }
}