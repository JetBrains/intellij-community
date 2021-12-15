// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.documentation.DocumentationSettings.InlineCodeHighlightingMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.idea.highlighter.textAttributesKeyForKtElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.wrapTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType


object KDocRenderer {

    fun StringBuilder.appendKDocContent(docComment: KDocTag): StringBuilder =
        append(markdownToHtml(docComment, allowSingleParagraph = true))

    fun StringBuilder.appendKDocSections(sections: List<KDocSection>) {
        fun findTagsByName(name: String) =
            sequence { sections.forEach { yieldAll(it.findTagsByName(name)) } }

        fun findTagByName(name: String) = findTagsByName(name).firstOrNull()

        appendTag(findTagByName("receiver"), KotlinBundle.message("kdoc.section.title.receiver"))

        val paramTags = findTagsByName("param").filter { it.getSubjectName() != null }
        appendTagList(paramTags, KotlinBundle.message("kdoc.section.title.parameters"), KotlinHighlightingColors.PARAMETER)

        val propertyTags = findTagsByName("property").filter { it.getSubjectName() != null }
        appendTagList(
            propertyTags, KotlinBundle.message("kdoc.section.title.properties"), KotlinHighlightingColors.INSTANCE_PROPERTY
        )

        appendTag(findTagByName("constructor"), KotlinBundle.message("kdoc.section.title.constructor"))

        appendTag(findTagByName("return"), KotlinBundle.message("kdoc.section.title.returns"))

        val throwTags = findTagsByName("throws").filter { it.getSubjectName() != null }
        val exceptionTags = findTagsByName("exception").filter { it.getSubjectName() != null }
        appendThrows(throwTags, exceptionTags)

        appendAuthors(findTagsByName("author"))
        appendTag(findTagByName("since"), KotlinBundle.message("kdoc.section.title.since"))
        appendTag(findTagByName("suppress"), KotlinBundle.message("kdoc.section.title.suppress"))

        appendSeeAlso(findTagsByName("see"))

        val sampleTags = findTagsByName("sample").filter { it.getSubjectLink() != null }
        appendSamplesList(sampleTags)
    }

    private fun StringBuilder.appendHyperlink(kDocLink: KDocLink) {
        if (DumbService.isDumb(kDocLink.project)) {
            append(kDocLink.getLinkText())
        } else {
            DocumentationManagerUtil.createHyperlink(
                this,
                kDocLink.getLinkText(),
                highlightQualifiedName(kDocLink.getLinkText(), getTargetLinkElementAttributes(kDocLink.getTargetElement())),
                false,
                true
            )
        }
    }

    private fun getTargetLinkElementAttributes(element: PsiElement?): TextAttributes {
        return element
            ?.let { textAttributesKeyForKtElement(it) }
            ?.let { getTargetLinkElementAttributes(it) }
            ?: TextAttributes().apply {
                foregroundColor = EditorColorsManager.getInstance().globalScheme.getColor(DefaultLanguageHighlighterColors.DOC_COMMENT_LINK)
            }
    }

    private fun getTargetLinkElementAttributes(key: TextAttributesKey): TextAttributes {
        return tuneAttributesForLink(EditorColorsManager.getInstance().globalScheme.getAttributes(key))
    }

    private fun highlightQualifiedName(qualifiedName: String, lastSegmentAttributes: TextAttributes): String {
        val linkComponents = qualifiedName.split(".")
        val qualifiedPath = linkComponents.subList(0, linkComponents.lastIndex)
        val elementName = linkComponents.last()
        return buildString {
            for (pathSegment in qualifiedPath) {
                val segmentAttributes = when {
                    pathSegment.isEmpty() || pathSegment.first().isLowerCase() -> DefaultLanguageHighlighterColors.IDENTIFIER
                    else -> KotlinHighlightingColors.CLASS
                }
                appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), segmentAttributes, pathSegment)
                appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), KotlinHighlightingColors.DOT, ".")
            }
            appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), lastSegmentAttributes, elementName)
        }
    }

    private fun KDocLink.getTargetElement(): PsiElement? {
        return getChildrenOfType<KDocName>().last().mainReference.resolve()
    }

    private fun PsiElement.extractExampleText() = when (this) {
        is KtDeclarationWithBody -> {
            when (val bodyExpression = bodyExpression) {
                is KtBlockExpression -> bodyExpression.text.removeSurrounding("{", "}")
                else -> bodyExpression!!.text
            }
        }
        else -> text
    }

    private fun trimCommonIndent(text: String): String {
        fun String.leadingIndent() = indexOfFirst { !it.isWhitespace() }

        val lines = text.split('\n')
        val minIndent = lines.filter { it.trim().isNotEmpty() }.minOfOrNull(String::leadingIndent) ?: 0
        return lines.joinToString("\n") { it.drop(minIndent) }
    }

    private fun StringBuilder.appendSection(title: String, content: StringBuilder.() -> Unit) {
        append(SECTION_HEADER_START, title, ":", SECTION_SEPARATOR)
        content()
        append(SECTION_END)
    }

    private fun StringBuilder.appendSamplesList(sampleTags: Sequence<KDocTag>) {
        if (!sampleTags.any()) return

        appendSection(KotlinBundle.message("kdoc.section.title.samples")) {
            sampleTags.forEach {
                it.getSubjectLink()?.let { subjectLink ->
                    append("<p>")
                    this@appendSamplesList.appendHyperlink(subjectLink)
                    wrapTag("pre") {
                        wrapTag("code") {
                            if (DumbService.isDumb(subjectLink.project)) {
                                append("// " + KotlinBundle.message("kdoc.comment.unresolved"))
                            } else {
                                val codeSnippet = when (val target = subjectLink.getTargetElement()) {
                                    null -> "// " + KotlinBundle.message("kdoc.comment.unresolved")
                                    else -> trimCommonIndent(target.extractExampleText()).htmlEscape()
                                }
                                this@appendSamplesList.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                                    when (DocumentationSettings.isHighlightingOfCodeBlocksEnabled()) {
                                        true -> InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING
                                        false -> InlineCodeHighlightingMode.NO_HIGHLIGHTING
                                    },
                                    subjectLink.project,
                                    KotlinLanguage.INSTANCE,
                                    codeSnippet
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun StringBuilder.appendSeeAlso(seeTags: Sequence<KDocTag>) {
        if (!seeTags.any()) return

        val iterator = seeTags.iterator()

        appendSection(KotlinBundle.message("kdoc.section.title.see.also")) {
            while (iterator.hasNext()) {
                val tag = iterator.next()
                val subjectName = tag.getSubjectName()
                val link = tag.getChildrenOfType<KDocLink>().lastOrNull()
                when {
                    link != null -> this.appendHyperlink(link)
                    subjectName != null -> DocumentationManagerUtil.createHyperlink(this, subjectName, subjectName, false, true)
                    else -> append(tag.getContent())
                }
                if (iterator.hasNext()) {
                    append(",<br>")
                }
            }
        }
    }

    private fun StringBuilder.appendAuthors(authorTags: Sequence<KDocTag>) {
        if (!authorTags.any()) return

        val iterator = authorTags.iterator()

        appendSection(KotlinBundle.message("kdoc.section.title.author")) {
            while (iterator.hasNext()) {
                append(iterator.next().getContent())
                if (iterator.hasNext()) {
                    append(", ")
                }
            }
        }
    }

    private fun StringBuilder.appendThrows(throwsTags: Sequence<KDocTag>, exceptionsTags: Sequence<KDocTag>) {
        if (!throwsTags.any() && !exceptionsTags.any()) return

        appendSection(KotlinBundle.message("kdoc.section.title.throws")) {

            fun KDocTag.append() {
                val subjectName = getSubjectName()
                if (subjectName != null) {
                    append("<p><code>")
                    val highlightedLinkLabel =
                        highlightQualifiedName(subjectName, getTargetLinkElementAttributes(KotlinHighlightingColors.CLASS))
                    DocumentationManagerUtil.createHyperlink(this@appendSection, subjectName, highlightedLinkLabel, false, true)
                    append("</code>")
                    val exceptionDescription = markdownToHtml(this)
                    if (exceptionDescription.isNotBlank()) {
                        append(" - $exceptionDescription")
                    }
                }
            }

            throwsTags.forEach { it.append() }
            exceptionsTags.forEach { it.append() }
        }
    }


    private fun StringBuilder.appendTagList(tags: Sequence<KDocTag>, title: String, titleAttributes: TextAttributesKey) {
        if (!tags.any()) {
            return
        }
        appendSection(title) {
            tags.forEach {
                val subjectName = it.getSubjectName()
                if (subjectName != null) {
                    append("<p><code>")
                    when (val link = it.getChildrenOfType<KDocLink>().firstOrNull()) {
                        null -> appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), titleAttributes, subjectName)
                        else -> appendHyperlink(link)
                    }
                    append("</code>")
                    val elementDescription = markdownToHtml(it)
                    if (elementDescription.isNotBlank()) {
                        append(" - $elementDescription")
                    }
                }
            }
        }
    }

    private fun StringBuilder.appendTag(tag: KDocTag?, title: String) {
        if (tag != null) {
            appendSection(title) {
                append(markdownToHtml(tag))
            }
        }
    }

    private fun markdownToHtml(comment: KDocTag, allowSingleParagraph: Boolean = false): String {
        val markdownTree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(comment.getContent())
        val markdownNode = MarkdownNode(markdownTree, null, comment)

        // Avoid wrapping the entire converted contents in a <p> tag if it's just a single paragraph
        val maybeSingleParagraph = markdownNode.children.singleOrNull { it.type != MarkdownTokenTypes.EOL }

        val firstParagraphOmitted = when {
          maybeSingleParagraph != null && !allowSingleParagraph -> {
              maybeSingleParagraph.children.joinToString("") { if (it.text == "\n") " " else it.toHtml() }
          }
          else -> markdownNode.toHtml()
        }

        val topMarginOmitted = when {
            firstParagraphOmitted.startsWith("<p>") -> firstParagraphOmitted.replaceFirst("<p>", "<p style='margin-top:0;padding-top:0;'>")
            else -> firstParagraphOmitted
        }

        return topMarginOmitted
    }

    class MarkdownNode(val node: ASTNode, val parent: MarkdownNode?, val comment: KDocTag) {
        val children: List<MarkdownNode> = node.children.map { MarkdownNode(it, this, comment) }
        val endOffset: Int get() = node.endOffset
        val startOffset: Int get() = node.startOffset
        val type: IElementType get() = node.type
        val text: String get() = comment.getContent().substring(startOffset, endOffset)
        fun child(type: IElementType): MarkdownNode? = children.firstOrNull { it.type == type }
    }

    private fun MarkdownNode.visit(action: (MarkdownNode, () -> Unit) -> Unit) {
        action(this) {
            for (child in children) {
                child.visit(action)
            }
        }
    }

    private fun MarkdownNode.toHtml(): String {
        if (node.type == MarkdownTokenTypes.WHITE_SPACE) {
            return text   // do not trim trailing whitespace
        }

        var currentCodeFenceLang = "kotlin"

        val sb = StringBuilder()
        visit { node, processChildren ->
            fun wrapChildren(tag: String, newline: Boolean = false) {
                sb.append("<$tag>")
                processChildren()
                sb.append("</$tag>")
                if (newline) sb.appendLine()
            }

            val nodeType = node.type
            val nodeText = node.text
            when (nodeType) {
                MarkdownElementTypes.UNORDERED_LIST -> wrapChildren("ul", newline = true)
                MarkdownElementTypes.ORDERED_LIST -> wrapChildren("ol", newline = true)
                MarkdownElementTypes.LIST_ITEM -> wrapChildren("li")
                MarkdownElementTypes.EMPH -> wrapChildren("em")
                MarkdownElementTypes.STRONG -> wrapChildren("strong")
                GFMElementTypes.STRIKETHROUGH -> wrapChildren("del")
                MarkdownElementTypes.ATX_1 -> wrapChildren("h1")
                MarkdownElementTypes.ATX_2 -> wrapChildren("h2")
                MarkdownElementTypes.ATX_3 -> wrapChildren("h3")
                MarkdownElementTypes.ATX_4 -> wrapChildren("h4")
                MarkdownElementTypes.ATX_5 -> wrapChildren("h5")
                MarkdownElementTypes.ATX_6 -> wrapChildren("h6")
                MarkdownElementTypes.BLOCK_QUOTE -> wrapChildren("blockquote")
                MarkdownElementTypes.PARAGRAPH -> {
                    sb.trimEnd()
                    wrapChildren("p", newline = true)
                }
                MarkdownElementTypes.CODE_SPAN -> {
                    val startDelimiter = node.child(MarkdownTokenTypes.BACKTICK)?.text
                    if (startDelimiter != null) {
                        val text = node.text.substring(startDelimiter.length).removeSuffix(startDelimiter)
                        sb.append("<code style='font-size:${DocumentationSettings.getMonospaceFontSizeCorrection(true)}%;'>")
                        sb.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                            DocumentationSettings.getInlineCodeHighlightingMode(),
                            comment.project,
                            KotlinLanguage.INSTANCE,
                            text
                        )
                        sb.append("</code>")
                    }
                }
                MarkdownElementTypes.CODE_BLOCK,
                MarkdownElementTypes.CODE_FENCE -> {
                    sb.trimEnd()
                    sb.append("<pre><code style='font-size:${DocumentationSettings.getMonospaceFontSizeCorrection(true)}%;'>")
                    processChildren()
                    sb.append("</code></pre>")
                }
                MarkdownTokenTypes.FENCE_LANG -> {
                    currentCodeFenceLang = nodeText
                }
                MarkdownElementTypes.SHORT_REFERENCE_LINK,
                MarkdownElementTypes.FULL_REFERENCE_LINK -> {
                    val linkLabelNode = node.child(MarkdownElementTypes.LINK_LABEL)
                    val linkLabelContent = linkLabelNode?.children
                        ?.dropWhile { it.type == MarkdownTokenTypes.LBRACKET }
                        ?.dropLastWhile { it.type == MarkdownTokenTypes.RBRACKET }
                    if (linkLabelContent != null) {
                        val label = linkLabelContent.joinToString(separator = "") { it.text }
                        val linkText = node.child(MarkdownElementTypes.LINK_TEXT)?.toHtml() ?: label
                        if (DumbService.isDumb(comment.project)) {
                            sb.append(linkText)
                        } else {
                            comment.findDescendantOfType<KDocName> { it.text == label }
                                ?.mainReference
                                ?.resolve()
                                ?.let { resolvedLinkElement ->
                                    DocumentationManagerUtil.createHyperlink(
                                        sb,
                                        label,
                                        highlightQualifiedName(linkText, getTargetLinkElementAttributes(resolvedLinkElement)),
                                        false,
                                        true
                                    )
                                }
                                ?: sb.appendStyledSpan(true, KotlinHighlightingColors.RESOLVED_TO_ERROR, label)
                        }
                    } else {
                        sb.append(node.text)
                    }
                }
                MarkdownElementTypes.INLINE_LINK -> {
                    val label = node.child(MarkdownElementTypes.LINK_TEXT)?.toHtml()
                    val destination = node.child(MarkdownElementTypes.LINK_DESTINATION)?.text
                    if (label != null && destination != null) {
                        sb.append("<a href=\"$destination\">$label</a>")
                    } else {
                        sb.append(node.text)
                    }
                }
                MarkdownTokenTypes.TEXT,
                MarkdownTokenTypes.WHITE_SPACE,
                MarkdownTokenTypes.COLON,
                MarkdownTokenTypes.SINGLE_QUOTE,
                MarkdownTokenTypes.DOUBLE_QUOTE,
                MarkdownTokenTypes.LPAREN,
                MarkdownTokenTypes.RPAREN,
                MarkdownTokenTypes.LBRACKET,
                MarkdownTokenTypes.RBRACKET,
                MarkdownTokenTypes.EXCLAMATION_MARK,
                GFMTokenTypes.CHECK_BOX,
                GFMTokenTypes.GFM_AUTOLINK -> {
                    sb.append(nodeText)
                }
                MarkdownTokenTypes.CODE_LINE,
                MarkdownTokenTypes.CODE_FENCE_CONTENT -> {
                    sb.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                        when (DocumentationSettings.isHighlightingOfCodeBlocksEnabled()) {
                            true -> InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING
                            false -> InlineCodeHighlightingMode.NO_HIGHLIGHTING
                        },
                        comment.project,
                        guessLanguage(currentCodeFenceLang) ?: KotlinLanguage.INSTANCE,
                        nodeText
                    )
                }
                MarkdownTokenTypes.EOL -> {
                    val parentType = node.parent?.type
                    if (parentType == MarkdownElementTypes.CODE_BLOCK || parentType == MarkdownElementTypes.CODE_FENCE) {
                        sb.append("\n")
                    } else {
                        sb.append(" ")
                    }
                }
                MarkdownTokenTypes.GT -> sb.append("&gt;")
                MarkdownTokenTypes.LT -> sb.append("&lt;")

                MarkdownElementTypes.LINK_TEXT -> {
                    val childrenWithoutBrackets = node.children.drop(1).dropLast(1)
                    for (child in childrenWithoutBrackets) {
                        sb.append(child.toHtml())
                    }
                }

                MarkdownTokenTypes.EMPH -> {
                    val parentNodeType = node.parent?.type
                    if (parentNodeType != MarkdownElementTypes.EMPH && parentNodeType != MarkdownElementTypes.STRONG) {
                        sb.append(node.text)
                    }
                }

                GFMTokenTypes.TILDE -> {
                    if (node.parent?.type != GFMElementTypes.STRIKETHROUGH) {
                        sb.append(node.text)
                    }
                }

                GFMElementTypes.TABLE -> {
                    val alignment: List<String> = getTableAlignment(node)
                    var addedBody = false
                    sb.append("<table>")

                    for (child in node.children) {
                        if (child.type == GFMElementTypes.HEADER) {
                            sb.append("<thead>")
                            processTableRow(sb, child, "th", alignment)
                            sb.append("</thead>")
                        } else if (child.type == GFMElementTypes.ROW) {
                            if (!addedBody) {
                                sb.append("<tbody>")
                                addedBody = true
                            }

                            processTableRow(sb, child, "td", alignment)
                        }
                    }

                    if (addedBody) {
                        sb.append("</tbody>")
                    }
                    sb.append("</table>")
                }

                else -> {
                    processChildren()
                }
            }
        }
        return sb.toString().trimEnd()
    }

    private fun StringBuilder.trimEnd() {
        while (length > 0 && this[length - 1] == ' ') {
            deleteCharAt(length - 1)
        }
    }

    private fun String.htmlEscape(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun processTableRow(sb: StringBuilder, node: MarkdownNode, cellTag: String, alignment: List<String>) {
        sb.append("<tr>")
        for ((i, child) in node.children.filter { it.type == GFMTokenTypes.CELL }.withIndex()) {
            val alignValue = alignment.getOrElse(i) { "" }
            val alignTag = if (alignValue.isEmpty()) "" else " align=\"$alignValue\""
            sb.append("<$cellTag$alignTag>")
            sb.append(child.toHtml())
            sb.append("</$cellTag>")
        }
        sb.append("</tr>")
    }

    private fun getTableAlignment(node: MarkdownNode): List<String> {
        val separatorRow = node.child(GFMTokenTypes.TABLE_SEPARATOR)
            ?: return emptyList()

        return separatorRow.text.split('|').filterNot { it.isBlank() }.map {
            val trimmed = it.trim()
            val left = trimmed.startsWith(':')
            val right = trimmed.endsWith(':')
            if (left && right) "center"
            else if (right) "right"
            else if (left) "left"
            else ""
        }
    }

    private fun StringBuilder.appendStyledSpan(doHighlighting: Boolean, attributesKey: TextAttributesKey, value: String?): StringBuilder {
        if (doHighlighting) {
            HtmlSyntaxInfoUtil.appendStyledSpan(this, attributesKey, value, DocumentationSettings.getHighlightingSaturation(true))
        } else {
            append(value)
        }
        return this
    }

    private fun StringBuilder.appendStyledSpan(doHighlighting: Boolean, attributes: TextAttributes, value: String?): StringBuilder {
        if (doHighlighting) {
            HtmlSyntaxInfoUtil.appendStyledSpan(this, attributes, value, DocumentationSettings.getHighlightingSaturation(true))
        } else {
            append(value)
        }
        return this
    }

    private fun StringBuilder.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
        highlightingMode: InlineCodeHighlightingMode,
        project: Project,
        language: Language,
        codeSnippet: String
    ): StringBuilder {
        val codeSnippetBuilder = StringBuilder()
        if (highlightingMode == InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING) { // highlight code by lexer
            HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                codeSnippetBuilder, project, language, codeSnippet, false, DocumentationSettings.getHighlightingSaturation(true)
            )
        } else {
            codeSnippetBuilder.append(StringUtil.escapeXmlEntities(codeSnippet))
        }
        if (highlightingMode != InlineCodeHighlightingMode.NO_HIGHLIGHTING) {
            // set code text color as editor default code color instead of doc component text color
            val codeAttributes = EditorColorsManager.getInstance().globalScheme.getAttributes(HighlighterColors.TEXT).clone()
            codeAttributes.backgroundColor = null
            appendStyledSpan(true, codeAttributes, codeSnippetBuilder.toString())
        } else {
            append(codeSnippetBuilder.toString())
        }
        return this
    }

    /**
     * If highlighted links has the same color as highlighted inline code blocks they will be indistinguishable.
     * In this case we should change link color to standard hyperlink color which we believe is apriori different.
     */
    private fun tuneAttributesForLink(attributes: TextAttributes): TextAttributes {
        val globalScheme = EditorColorsManager.getInstance().globalScheme
        if (attributes.foregroundColor == globalScheme.getAttributes(HighlighterColors.TEXT).foregroundColor
            || attributes.foregroundColor == globalScheme.getAttributes(DefaultLanguageHighlighterColors.IDENTIFIER).foregroundColor
        ) {
            val tuned = attributes.clone()
            if (ApplicationManager.getApplication().isUnitTestMode) {
                tuned.foregroundColor = globalScheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES).foregroundColor
            }
            else {
                tuned.foregroundColor = globalScheme.getColor(DefaultLanguageHighlighterColors.DOC_COMMENT_LINK)
            }
            return tuned
        }
        return attributes
    }

    private fun guessLanguage(name: String): Language? {
        val lower = StringUtil.toLowerCase(name)
        return Language.findLanguageByID(lower)
            ?: Language.getRegisteredLanguages().firstOrNull { StringUtil.toLowerCase(it.id) == lower }
    }
}
