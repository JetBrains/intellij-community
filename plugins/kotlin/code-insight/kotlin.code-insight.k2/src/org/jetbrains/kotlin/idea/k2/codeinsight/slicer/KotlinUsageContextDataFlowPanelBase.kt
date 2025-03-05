// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.slicer.DuplicateMap
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SlicePanel
import com.intellij.slicer.SliceRootNode
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageContextPanel
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.impl.UsageContextPanelBase
import com.intellij.usages.impl.UsageViewImpl
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtDeclaration
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

sealed class KotlinUsageContextDataFlowPanelBase(presentation: UsageViewPresentation, private val isInflow: Boolean)
    : UsageContextPanelBase(presentation) {
    private var panel: JPanel? = null

    abstract class ProviderBase : UsageContextPanel.Provider {
        override fun isAvailableFor(usageView: UsageView): Boolean {
            val target = (usageView as UsageViewImpl).targets.firstOrNull() ?: return false
            val element = (target as? PsiElementUsageTarget)?.element
            return element is KtDeclaration && element.isValid
        }
    }

    private fun createParams(element: PsiElement): SliceAnalysisParams {
        return SliceAnalysisParams().apply {
            scope = AnalysisScope(element.project)
            dataFlowToThis = isInflow
            showInstanceDereferences = true
        }
    }

    protected fun createPanel(element: PsiElement, dataFlowToThis: Boolean): JPanel {
        val project = element.project
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.FIND) ?: error("Can't find ToolWindowId.FIND")
        val params = createParams(element)

        val rootNode = SliceRootNode(project, DuplicateMap(), KotlinSliceUsage(element, params))

        return object : SlicePanel(project, dataFlowToThis, rootNode, false, toolWindow) {
            override fun isToShowAutoScrollButton() = false

            override fun isToShowPreviewButton() = false

            override fun isAutoScroll() = false

            override fun setAutoScroll(autoScroll: Boolean) {}

            override fun isPreview() = false

            override fun setPreview(preview: Boolean) {}
        }
    }

    public override fun updateLayoutLater(infos: List<UsageInfo>?) {
        if (infos.isNullOrEmpty()) {
            removeAll()
            val title = UsageViewBundle.message("select.the.usage.to.preview")
            add(JLabel(title, SwingConstants.CENTER), BorderLayout.CENTER)
        } else {
            val element = infos.firstOrNull()?.element ?: return
            if (panel != null) {
                Disposer.dispose(panel as Disposable)
            }

            val panel = createPanel(element, isInflow)
            Disposer.register(this, panel as Disposable)
            removeAll()
            add(panel, BorderLayout.CENTER)
            this.panel = panel
        }
        revalidate()
    }

    override fun dispose() {
        super.dispose()
        panel = null
    }
}

class KotlinUsageContextDataInflowPanel(presentation: UsageViewPresentation)
    : KotlinUsageContextDataFlowPanelBase(presentation, true) {
    class Provider : ProviderBase() {
        override fun create(usageView: UsageView): UsageContextPanel {
            return KotlinUsageContextDataInflowPanel(usageView.getPresentation())
        }

        override fun getTabTitle() = KotlinBundle.message("slicer.title.dataflow.to.here")
    }
}

class KotlinUsageContextDataOutflowPanel(presentation: UsageViewPresentation)
    : KotlinUsageContextDataFlowPanelBase(presentation, false) {
    class Provider : ProviderBase() {
        override fun create(usageView: UsageView): UsageContextPanel {
            return KotlinUsageContextDataOutflowPanel(usageView.getPresentation())
        }

        override fun getTabTitle() = KotlinBundle.message("slicer.title.dataflow.from.here")
    }
}