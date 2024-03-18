// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.lang.documentation.QuickDocHighlightingHelper.appendStyledCodeBlock
import com.intellij.lang.documentation.QuickDocHighlightingHelper.appendStyledFragment
import com.intellij.lang.documentation.QuickDocHighlightingHelper.appendStyledInlineCode
import com.intellij.lang.documentation.QuickDocHighlightingHelper.appendStyledCodeFragment
import com.intellij.lang.documentation.QuickDocHighlightingHelper.appendStyledLinkFragment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.highlighting.textAttributesKeyForKtElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.idea.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager
import org.jetbrains.kotlin.idea.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager.Companion.eraseTypeParameter
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType


object KDocRenderer {

    private fun StringBuilder.appendKDocContent(docComment: KDocTag): StringBuilder =
        append(markdownToHtml(docComment, allowSingleParagraph = true))

    private fun StringBuilder.appendKDocSections(sections: List<KDocSection>) {
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

    fun StringBuilder.renderKDoc(
        contentTag: KDocTag,
        sections: List<KDocSection> = if (contentTag is KDocSection) listOf(contentTag) else emptyList()
    ) {
        insert(KDocTemplate.DescriptionBodyTemplate.Kotlin()) {
            content {
                appendKDocContent(contentTag)
            }
            sections {
                appendKDocSections(sections)
            }
        }
    }

    private fun StringBuilder.appendHyperlink(kDocLink: KDocLink) {
        val linkText = kDocLink.getLinkText()
        if (DumbService.isDumb(kDocLink.project)) {
            append(linkText)
        } else {
            DocumentationManagerUtil.createHyperlink(
                this,
                linkText,
                highlightQualifiedName(linkText, getTargetLinkElementAttributes(kDocLink.getTargetElement())),
                false,
                true
            )
        }
    }

    private fun getTargetLinkElementAttributes(element: PsiElement?): TextAttributes {
        return element
            ?.let { textAttributesKeyForKtElement(it)?.attributesKey }
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
                appendStyledLinkFragment(pathSegment, segmentAttributes, )
                appendStyledLinkFragment(".", KotlinHighlightingColors.DOT)
            }
            appendStyledLinkFragment(elementName, lastSegmentAttributes)
        }
    }

    private fun KDocLink.getTargetElement(): PsiElement? {
        return getChildrenOfType<KDocName>().last().references.firstOrNull { it is KDocReference }?.resolve()
    }

    @Nls
    fun generateJavadoc(psiMethod: PsiMethod): String {
        val javaDocInfoGenerator = JavaDocInfoGeneratorFactory.create(psiMethod.project, psiMethod)
        val builder = StringBuilder()
        @Suppress("HardCodedStringLiteral")
        if (javaDocInfoGenerator.generateDocInfoCore(builder, false)) {
            val renderedJava = builder.toString()
            return renderedJava.removeRange(
                renderedJava.indexOf(DEFINITION_START),
                renderedJava.indexOf(DEFINITION_END)
            ) // Cut off light method signature
        }
        return ""
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
                    this@appendSamplesList.appendStyledCodeBlock(
                        subjectLink.project,
                        KotlinLanguage.INSTANCE,
                        if (DumbService.isDumb(subjectLink.project))
                            "// " + KotlinBundle.message("kdoc.comment.unresolved")
                        else when (val target = subjectLink.getTargetElement()) {
                            null -> "// " + KotlinBundle.message("kdoc.comment.unresolved")
                            else -> trimCommonIndent(target.extractExampleText()).htmlEscape()
                        }
                    )
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
                val subjectName = it.getSubjectName() ?: return@forEach

                append("<p><code>")
                when (val link = it.getChildrenOfType<KDocLink>().firstOrNull()) {
                    null -> appendStyledLinkFragment(subjectName, titleAttributes)
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
                        sb.appendStyledInlineCode(comment.project, KotlinLanguage.INSTANCE, text)
                    }
                }

                MarkdownElementTypes.CODE_BLOCK,
                MarkdownElementTypes.CODE_FENCE -> {
                    sb.trimEnd()
                    var language: String? = null
                    val contents = StringBuilder()
                    node.children.forEach { child ->
                        when (child.type) {
                            MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.CODE_LINE, MarkdownTokenTypes.EOL ->
                                contents.append(child.text)

                            MarkdownTokenTypes.FENCE_LANG ->
                                language = child.text.trim().split(' ')[0]
                        }
                    }
                    sb.appendStyledCodeBlock(
                        project = comment.project,
                        language = QuickDocHighlightingHelper.guessLanguage(language) ?: KotlinLanguage.INSTANCE,
                        code = contents
                    )
                }

                MarkdownTokenTypes.FENCE_LANG, MarkdownTokenTypes.CODE_LINE, MarkdownTokenTypes.CODE_FENCE_CONTENT -> {
                    // skip
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
                                ?.references
                                ?.firstOrNull { it is KDocReference }
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
                                ?: sb.appendStyledFragment(label, KotlinHighlightingColors.RESOLVED_TO_ERROR)
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

                MarkdownTokenTypes.EOL -> {
                    sb.append(" ")
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
        while (isNotEmpty() && this[length - 1] == ' ') {
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
            } else {
                tuned.foregroundColor = globalScheme.getColor(DefaultLanguageHighlighterColors.DOC_COMMENT_LINK)
            }
            return tuned
        }
        return attributes
    }

    fun StringBuilder.appendHighlighted(
        value: String,
        project: Project,
        attributesBuilder: KotlinIdeDescriptorRendererHighlightingManager<KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes>.()
        -> KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes
    ) {
        with(createHighlightingManager(project)) {
            this@appendHighlighted.appendHighlighted(value, attributesBuilder())
        }
    }

    fun highlight(
        value: String,
        project: Project,
        attributesBuilder: KotlinIdeDescriptorRendererHighlightingManager<KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes>.()
        -> KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes
    ): String {
        return buildString { appendHighlighted(value, project, attributesBuilder) }
    }

    fun StringBuilder.appendCodeSnippetHighlightedByLexer(project: Project, codeSnippet: String) {
        with(createHighlightingManager(project)) {
            appendCodeSnippetHighlightedByLexer(codeSnippet)
        }
    }

    private data class TextAttributesAdapter(val attributes: TextAttributes) :
        KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes

    fun createHighlightingManager(project: Project): KotlinIdeDescriptorRendererHighlightingManager<KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes> {
        if (!DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled()) {
            return KotlinIdeDescriptorRendererHighlightingManager.NO_HIGHLIGHTING
        }
        return object : KotlinIdeDescriptorRendererHighlightingManager<TextAttributesAdapter> {
            override fun StringBuilder.appendHighlighted(
                value: String,
                attributes: TextAttributesAdapter
            ) {
                appendStyledFragment(value, attributes.attributes)
            }

            override fun StringBuilder.appendCodeSnippetHighlightedByLexer(codeSnippet: String) {
                appendStyledCodeFragment(project, KotlinLanguage.INSTANCE, codeSnippet)
            }

            private fun resolveKey(key: TextAttributesKey): TextAttributesAdapter {
                return TextAttributesAdapter(
                    EditorColorsManager.getInstance().globalScheme.getAttributes(key)
                )
            }

            override val asError get() = resolveKey(KotlinHighlightingColors.RESOLVED_TO_ERROR)
            override val asInfo get() = resolveKey(KotlinHighlightingColors.BLOCK_COMMENT)
            override val asDot get() = resolveKey(KotlinHighlightingColors.DOT)
            override val asComma get() = resolveKey(KotlinHighlightingColors.COMMA)
            override val asColon get() = resolveKey(KotlinHighlightingColors.COLON)
            override val asDoubleColon get() = resolveKey(KotlinHighlightingColors.DOUBLE_COLON)
            override val asParentheses get() = resolveKey(KotlinHighlightingColors.PARENTHESIS)
            override val asArrow get() = resolveKey(KotlinHighlightingColors.ARROW)
            override val asBrackets get() = resolveKey(KotlinHighlightingColors.BRACKETS)
            override val asBraces get() = resolveKey(KotlinHighlightingColors.BRACES)
            override val asOperationSign get() = resolveKey(KotlinHighlightingColors.OPERATOR_SIGN)
            override val asNonNullAssertion get() = resolveKey(KotlinHighlightingColors.EXCLEXCL)
            override val asNullityMarker get() = resolveKey(KotlinHighlightingColors.QUEST)
            override val asKeyword get() = resolveKey(KotlinHighlightingColors.KEYWORD)
            override val asVal get() = resolveKey(KotlinHighlightingColors.VAL_KEYWORD)
            override val asVar get() = resolveKey(KotlinHighlightingColors.VAR_KEYWORD)
            override val asAnnotationName get() = resolveKey(KotlinHighlightingColors.ANNOTATION)
            override val asAnnotationAttributeName get() = resolveKey(KotlinHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES)
            override val asClassName get() = resolveKey(KotlinHighlightingColors.CLASS)
            override val asPackageName get() = resolveKey(DefaultLanguageHighlighterColors.IDENTIFIER)
            override val asObjectName get() = resolveKey(KotlinHighlightingColors.OBJECT)
            override val asInstanceProperty get() = resolveKey(KotlinHighlightingColors.INSTANCE_PROPERTY)
            override val asTypeAlias get() = resolveKey(KotlinHighlightingColors.TYPE_ALIAS)
            override val asParameter get() = resolveKey(KotlinHighlightingColors.PARAMETER)
            override val asTypeParameterName get() = resolveKey(KotlinHighlightingColors.TYPE_PARAMETER)
            override val asLocalVarOrVal get() = resolveKey(KotlinHighlightingColors.LOCAL_VARIABLE)
            override val asFunDeclaration get() = resolveKey(KotlinHighlightingColors.FUNCTION_DECLARATION)
            override val asFunCall get() = resolveKey(KotlinHighlightingColors.FUNCTION_CALL)
        }
            .eraseTypeParameter()
    }
}
