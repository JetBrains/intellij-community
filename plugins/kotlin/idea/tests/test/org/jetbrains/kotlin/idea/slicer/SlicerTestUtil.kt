// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.analysis.AnalysisScope
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.slicer.*
import com.intellij.ui.JBColor
import com.intellij.usages.TextChunk
import com.intellij.util.FontUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.codeInsight.slicer.AbstractKotlinSliceUsage
import org.jetbrains.kotlin.idea.codeInsight.slicer.HackedSliceLeafValueClassNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.awt.Font

internal class TestSliceTreeStructure(private val rootNode: SliceNode) : AbstractTreeStructureBase(rootNode.project) {
    override fun getProviders() = emptyList<TreeStructureProvider>()

    override fun getRootElement() = rootNode

    override fun commit() {
    }

    override fun hasSomethingToCommit() = false
}

internal fun buildTreeRepresentation(rootNode: SliceNode): String {
    val project = rootNode.element!!.project!!
    val projectScope = GlobalSearchScope.projectScope(project)

    fun TextChunk.render(newText: String): String {
        var text = newText
        if (attributes.fontType == Font.BOLD) {
            text = "<bold>$text</bold>"
        }
        if (attributes.foregroundColor == JBColor.GRAY) {
            if (text.startsWith(" ")) {
                text = " (${text.drop(1)})"
            }
            else {
                text = "($text)"
            }
        }
        return text.removeSuffix(FontUtil.thinSpace())
    }

    fun SliceNode.isSliceLeafValueClassNode() = this is HackedSliceLeafValueClassNode

    fun process(node: SliceNode, indent: Int): String {
        node.update()
        val usage = node.element!!.value

        node.calculateDupNode()
        val isDuplicated = !node.isSliceLeafValueClassNode() && node.duplicate != null

        return buildString {
            when {
                node is SliceRootNode && usage.element is KtFile -> {
                    node.sortedChildren.forEach { append(process(it, indent)) }
                    return@buildString
                }

                // SliceLeafValueClassNode is package-private
                node.isSliceLeafValueClassNode() -> append("[${node.nodeText}]\n")

                else -> {
                    val chunks = usage.text
                    if (!PsiSearchScopeUtil.isInScope(projectScope, usage.element!!)) {
                        append("LIB ")
                    }
                    else {
                        append(chunks.first().let { it.render(it.text) } + " ")
                    }
                    check(chunks[1].text == FontUtil.spaceAndThinSpace())

                    repeat(indent) { append('\t') }

                    if (usage is AbstractKotlinSliceUsage && usage.isDereference) {
                        append("DEREFERENCE: ")
                    }

                    if (usage is JavaSliceUsage) {
                        append("JAVA: ")
                    }

                    val expectedBehaviourSuffixes = mutableSetOf<String>()
                    if (usage is AbstractKotlinSliceUsage) {
                        usage.mode.inlineCallStack.forEach {
                            append("(INLINE CALL ${it.function?.name}) ")
                        }
                        for (behaviour in usage.mode.behaviourStack.reversed()) {
                            append(behaviour.testPresentationPrefix)
                            expectedBehaviourSuffixes.add(behaviour.slicePresentationPrefix)
                        }
                    }

                    if (isDuplicated) {
                        append("DUPLICATE: ")
                    }

                    chunks.drop(2).joinTo(this, separator = "") {
                        var text = it.text
                        for (s in expectedBehaviourSuffixes.toList()) {
                            if (text.contains(s)) {
                                text = text.replace(s, "")
                                expectedBehaviourSuffixes.remove(s)
                            }
                        }

                        if (text.isNotEmpty()) {
                            it.render(text)
                        }
                        else {
                            ""
                        }
                    }

                    check(expectedBehaviourSuffixes.isEmpty()) {
                        "Missing suffixes: " + expectedBehaviourSuffixes.joinToString()
                    }

                    append("\n")
                }
            }

            if (!isDuplicated) {
                node.sortedChildren.forEach { append(process(it, indent + 1)) }
            }
        }.replace(Regex("</bold><bold>"), "")
    }

    return process(rootNode, 0)
}

private val SliceNode.sortedChildren: List<SliceNode>
    get() = children.sortedBy { it.value.element?.startOffset ?: -1 }

internal fun testSliceFromOffset(
    file: KtFile,
    offset: Int,
    doTest: (sliceProvider: SliceLanguageSupportProvider, rootNode: SliceRootNode) -> Unit
) {
    val fileText = file.text
    val flowKind = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// FLOW: ")
    val withDereferences = InTextDirectivesUtils.isDirectiveDefined(fileText, "// WITH_DEREFERENCES")
    val analysisParams = SliceAnalysisParams().apply {
        dataFlowToThis = when (flowKind) {
            "IN" -> true
            "OUT" -> false
            else -> throw AssertionError("Invalid flow kind: $flowKind")
        }
        showInstanceDereferences = withDereferences
        scope = AnalysisScope(file.project)
    }

    val elementAtCaret = file.findElementAt(offset)!!
    val sliceProvider = LanguageSlicing.getProvider(elementAtCaret)
    val expression = sliceProvider.getExpressionAtCaret(elementAtCaret, analysisParams.dataFlowToThis)!!
    val rootUsage = sliceProvider.createRootUsage(expression, analysisParams)
    val rootNode = SliceRootNode(file.project, DuplicateMap(), rootUsage)
    doTest(sliceProvider, rootNode)
}
