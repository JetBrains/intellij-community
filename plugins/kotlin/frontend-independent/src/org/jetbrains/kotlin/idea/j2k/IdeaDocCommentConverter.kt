// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.javadoc.PsiDocToken
import com.intellij.psi.javadoc.PsiInlineDocTag
import com.intellij.psi.xml.*
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

object IdeaDocCommentConverter : DocCommentConverter {
    override fun convertDocComment(docComment: PsiDocComment): String {
        val html = buildString {
            appendJavadocElements(docComment.descriptionElements)

            tagsLoop@
            for (tag in docComment.tags) {
                when (tag.name) {
                    "deprecated" -> continue@tagsLoop
                    "see" -> append("@see ${convertJavadocLink(tag.content())}\n")
                    else -> {
                        appendJavadocElements(tag.children)
                        if (!endsWithNewline()) append("\n")
                    }
                }
            }
        }

        if (html.trim().isEmpty() && docComment.findTagByName("deprecated") != null) {
            // @deprecated was the only content of the doc comment; we can drop the comment
            return ""
        }

        val htmlFile = PsiFileFactory.getInstance(docComment.project).createFileFromText(
            "javadoc.html", HtmlFileType.INSTANCE, html
        )
        val htmlToMarkdownConverter = HtmlToMarkdownConverter()
        htmlFile.accept(htmlToMarkdownConverter)
        return htmlToMarkdownConverter.result
    }

    private fun StringBuilder.appendJavadocElements(elements: Array<PsiElement>): StringBuilder {
        elements.forEach {
            if (it is PsiInlineDocTag) {
                append(convertInlineDocTag(it))
            } else {
                if (it.node?.elementType != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
                    append(it.text)
                }
            }
        }
        return this
    }

    /**
     * Returns true if the builder ends with a new-line optionally followed by some spaces
     */
    private fun StringBuilder.endsWithNewline(): Boolean {
        for (i in length - 1 downTo 0) {
            val c = get(i)
            if (c.isWhitespace()) {
                if (c == '\n' || c == '\r') return true
            } else {
                return false
            }
        }
        return false
    }


    private fun convertInlineDocTag(tag: PsiInlineDocTag) = when (tag.name) {
        "code", "literal" -> {
            val text = tag.dataElements.joinToString("") { it.text }
            val escaped = StringUtil.escapeXml(text.trimStart())
            if (tag.name == "code") "<code>$escaped</code>" else escaped
        }

        "link", "linkplain" -> {
            val valueElement = tag.linkElement()
            val labelText = tag.dataElements.firstOrNull { it is PsiDocToken }?.text ?: ""
            val kdocLink = convertJavadocLink(valueElement?.text)
            val linkText = if (labelText.isEmpty()) kdocLink else StringUtil.escapeXml(labelText)
            "<a docref=\"$kdocLink\">$linkText</a>"
        }

        else -> tag.text
    }

    private fun convertJavadocLink(link: String?): String = link?.substringBefore('(')?.replace('#', '.') ?: ""

    private fun PsiDocTag.linkElement(): PsiElement? = valueElement ?: dataElements.firstOrNull { it !is PsiWhiteSpace }

    private fun XmlTag.attributesAsString() = if (attributes.isNotEmpty())
        attributes.joinToString(separator = " ", prefix = " ") { it.text }
    else
        ""

    private class HtmlToMarkdownConverter() : XmlRecursiveElementVisitor() {
        private enum class ListType { Ordered, Unordered; }
        data class MarkdownSpan(val prefix: String, val suffix: String) {
            companion object {
                val Empty = MarkdownSpan("", "")

                fun wrap(text: String) = MarkdownSpan(text, text)
                fun prefix(text: String) = MarkdownSpan(text, "")

                fun preserveTag(tag: XmlTag) =
                    MarkdownSpan("<${tag.name}${tag.attributesAsString()}>", "</${tag.name}>")
            }
        }


        val result: String
            get() = markdownBuilder.toString()

        private val markdownBuilder = StringBuilder("/**")
        private var afterLineBreak = false
        private var whitespaceIsPartOfText = true
        private var currentListType = ListType.Unordered

        override fun visitWhiteSpace(space: PsiWhiteSpace) {
            super.visitWhiteSpace(space)

            if (whitespaceIsPartOfText) {
                appendPendingText()
                val lines = space.text.lines()
                if (lines.size == 1) {
                    markdownBuilder.append(space.text)
                } else {
                    //several lines of spaces:
                    //drop first line - it contains trailing spaces before the first new-line;
                    //do not add star for the last line, it is handled by appendPendingText()
                    //and it is not needed in the end of the comment
                    lines.drop(1).dropLast(1).forEach {
                        markdownBuilder.append("\n * ")
                    }
                    markdownBuilder.append("\n")
                    afterLineBreak = true
                }
            }
        }

        override fun visitElement(element: PsiElement) {
            super.visitElement(element)

            when (element.node.elementType) {
                XmlTokenType.XML_DATA_CHARACTERS -> {
                    appendPendingText()
                    markdownBuilder.append(element.text)
                }
                XmlTokenType.XML_CHAR_ENTITY_REF -> {
                    appendPendingText()
                    val grandParent = element.parentsWithSelf.match(XmlToken::class, XmlText::class, last = HtmlTag::class)
                    if (grandParent?.name == "code" || grandParent?.name == "literal")
                        markdownBuilder.append(StringUtil.unescapeXmlEntities(element.text))
                    else
                        markdownBuilder.append(element.text)
                }
            }

        }

        override fun visitXmlTag(tag: XmlTag) {
            withWhitespaceAsPartOfText(false) {
                val oldListType = currentListType
                val atLineStart = afterLineBreak
                appendPendingText()
                val (openingMarkdown, closingMarkdown) = getMarkdownForTag(tag, atLineStart)
                markdownBuilder.append(openingMarkdown)

                super.visitXmlTag(tag)

                //appendPendingText()
                markdownBuilder.append(closingMarkdown)
                currentListType = oldListType
            }
        }

        override fun visitXmlText(text: XmlText) {
            withWhitespaceAsPartOfText(true) {
                super.visitXmlText(text)
            }
        }

        private inline fun withWhitespaceAsPartOfText(newValue: Boolean, block: () -> Unit) {
            val oldValue = whitespaceIsPartOfText
            whitespaceIsPartOfText = newValue
            try {
                block()
            } finally {
                whitespaceIsPartOfText = oldValue
            }
        }

        private fun getMarkdownForTag(tag: XmlTag, atLineStart: Boolean): MarkdownSpan = when (tag.name) {
            "b", "strong" -> MarkdownSpan.wrap("**")

            "p" -> if (atLineStart) MarkdownSpan.prefix("\n * ") else MarkdownSpan.prefix("\n *\n *")

            "i", "em" -> MarkdownSpan.wrap("*")

            "s", "del" -> MarkdownSpan.wrap("~~")

            "code" -> {
                val innerText = tag.value.text.trim()
                if (innerText.startsWith('`') && innerText.endsWith('`'))
                    MarkdownSpan("`` ", " ``")
                else
                    MarkdownSpan.wrap("`")
            }

            "a" -> {
                if (tag.getAttributeValue("docref") != null) {
                    val docRef = tag.getAttributeValue("docref")
                    val innerText = tag.value.text
                    if (docRef == innerText) MarkdownSpan("[", "]") else MarkdownSpan("[", "][$docRef]")
                } else if (tag.getAttributeValue("href") != null) {
                    MarkdownSpan("[", "](${tag.getAttributeValue("href") ?: ""})")
                } else {
                    MarkdownSpan.preserveTag(tag)
                }
            }

            "ul" -> {
                currentListType = ListType.Unordered; MarkdownSpan.Empty
            }

            "ol" -> {
                currentListType = ListType.Ordered; MarkdownSpan.Empty
            }

            "li" -> if (currentListType == ListType.Unordered) MarkdownSpan.prefix(" * ") else MarkdownSpan.prefix(" 1. ")

            else -> MarkdownSpan.preserveTag(tag)
        }

        private fun appendPendingText() {
            if (afterLineBreak) {
                markdownBuilder.append(" * ")
                afterLineBreak = false
            }
        }

        override fun visitXmlFile(file: XmlFile) {
            super.visitXmlFile(file)

            markdownBuilder.append(" */")
        }
    }
}
