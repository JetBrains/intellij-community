// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.ide.SelectInEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceUsage
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usageView.UsageInfo
import com.intellij.usages.TextChunk
import com.intellij.util.FontUtil
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.codeInsight.slicer.AbstractKotlinSliceUsage
import org.jetbrains.kotlin.idea.codeInsight.slicer.KotlinSliceAnalysisMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.awt.Font

open class KotlinSliceUsage : AbstractKotlinSliceUsage {
    private var usageInfo: AdaptedUsageInfo? = null

    constructor(
        element: PsiElement,
        parent: SliceUsage,
        mode: KotlinSliceAnalysisMode,
        forcedExpressionMode: Boolean,
    ) : super(element, parent, mode, forcedExpressionMode) {
        initializeUsageInfo()
    }

    constructor(element: PsiElement, params: SliceAnalysisParams) : super(element, params) {
        initializeUsageInfo()
    }

    //TODO: it's all hacks due to UsageInfo stored in the base class - fix it in IDEA
    private fun initializeUsageInfo() {
        usageInfo = getUsageInfo().element?.let { AdaptedUsageInfo(it, mode) }
        resetCachedPresentation()
    }

    override fun getUsageInfo(): UsageInfo {
        return usageInfo ?: super.getUsageInfo()
    }

    override fun getMergedInfos(): Array<UsageInfo> {
        return arrayOf(getUsageInfo())
    }

    override fun computeText(): Array<TextChunk> {
        val text = super.computeText()

        val result = mutableListOf<TextChunk>()
        for ((i, textChunk) in text.withIndex()) {
            var attributes = textChunk.simpleAttributesIgnoreBackground
            if (isDereference) {
                attributes = attributes.derive(attributes.style, JBColor.LIGHT_GRAY, attributes.bgColor, attributes.waveColor)
            }

            if (attributes.fontStyle == Font.BOLD) {
                attributes = attributes.derive(attributes.style or SimpleTextAttributes.STYLE_UNDERLINE, null, null, null)
            }

            result.add(TextChunk(attributes.toTextAttributes(), textChunk.text))
            if (i == 0) {
                result.add(TextChunk(TextAttributes(), FontUtil.spaceAndThinSpace()))
            }
        }

        for (behaviour in mode.behaviourStack.reversed()) {
            result.add(TextChunk(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.toTextAttributes(), behaviour.slicePresentationPrefix))
        }

        val containerSuffix = KotlinSliceUsageSuffix.containerSuffix(this)
        if (containerSuffix != null) {
            result.add(TextChunk(TextAttributes(), " "))
            result.add(TextChunk(SimpleTextAttributes.GRAY_ATTRIBUTES.toTextAttributes(), containerSuffix))
        }

        return result.toTypedArray()
    }

    override fun openTextEditor(focus: Boolean): Editor? {
        val project = getUsageInfo().project
        val descriptor = OpenFileDescriptor(project, file, getUsageInfo().navigationOffset)
        return FileEditorManager.getInstance(project).openTextEditor(descriptor, focus)
    }

    override fun highlightInEditor() {
        if (!isValid) return

        val usageInfo = getUsageInfo()
        val range = usageInfo.navigationRange ?: return
        SelectInEditorManager.getInstance(getUsageInfo().project).selectInEditor(file, range.startOffset, range.endOffset, false, false)

        if (usageInfo.navigationOffset != range.startOffset) {
            openTextEditor(false) // to position the caret at the identifier
        }
    }

    override fun copy(): KotlinSliceUsage {
        val element = getUsageInfo().element ?: error("No more valid usageInfo.element")
        return if (parent == null)
            KotlinSliceUsage(element, params)
        else
            KotlinSliceUsage(element, parent, mode, forcedExpressionMode)
    }

    override fun canBeLeaf() = element != null && mode == KotlinSliceAnalysisMode.Default

    public override fun processUsagesFlownDownTo(
        element: PsiElement,
        uniqueProcessor: Processor<in SliceUsage>
    ) {
        val ktElement = element as? KtElement ?: return
        val behaviour = mode.currentBehaviour
        if (behaviour != null) {
            behaviour.processUsages(ktElement, this, uniqueProcessor)
        } else {
            InflowSlicer(ktElement, uniqueProcessor, this).processChildren(forcedExpressionMode)
        }
    }

    public override fun processUsagesFlownFromThe(
        element: PsiElement,
        uniqueProcessor: Processor<in SliceUsage>
    ) {
        val ktElement = element as? KtElement ?: return
        val behaviour = mode.currentBehaviour
        if (behaviour != null) {
            behaviour.processUsages(ktElement, this, uniqueProcessor)
        } else {
            OutflowSlicer(ktElement, uniqueProcessor, this).processChildren(forcedExpressionMode)
        }
    }

    @Suppress("EqualsOrHashCode")
    private class AdaptedUsageInfo(element: PsiElement, private val mode: KotlinSliceAnalysisMode) : UsageInfo(element) {
        override fun equals(other: Any?): Boolean {
            return other is AdaptedUsageInfo && super.equals(other) && mode == other.mode
        }

        override fun getNavigationRange(): Segment? {
            val element = element ?: return null
            return when (element) {
                is KtParameter -> {
                    val nameRange = element.nameIdentifier?.textRange ?: return super.getNavigationRange()
                    val start = element.valOrVarKeyword?.startOffset ?: nameRange.startOffset
                    val end = element.typeReference?.endOffset ?: nameRange.endOffset
                    TextRange(start, end)
                }

                is KtVariableDeclaration -> {
                    val nameRange = element.nameIdentifier?.textRange ?: return super.getNavigationRange()
                    val start = element.valOrVarKeyword?.startOffset ?: nameRange.startOffset
                    val end = element.typeReference?.endOffset ?: nameRange.endOffset
                    TextRange(start, end)
                }

                is KtNamedFunction -> {
                    val funKeyword = element.funKeyword
                    val parameterList = element.valueParameterList
                    val typeReference = element.typeReference
                    if (funKeyword != null && parameterList != null)
                        TextRange(funKeyword.startOffset, typeReference?.endOffset ?: parameterList.endOffset)
                    else
                        null
                }

                is KtPrimaryConstructor -> {
                    element.containingClassOrObject?.nameIdentifier
                        ?.let { TextRange(it.startOffset, element.endOffset) }
                }

                else -> null
            } ?: TextRange(element.textOffset, element.endOffset)
        }

        override fun getRangeInElement(): ProperTextRange? {
            val elementRange = element?.textRange ?: return null
            return navigationRange
                ?.takeIf { it in elementRange }
                ?.let { ProperTextRange(it.startOffset, it.endOffset).shiftRight(-elementRange.startOffset) }
                ?: super.getRangeInElement()
        }

        override fun getNavigationOffset(): Int {
            return element?.textOffset ?: -1
        }
    }
}

