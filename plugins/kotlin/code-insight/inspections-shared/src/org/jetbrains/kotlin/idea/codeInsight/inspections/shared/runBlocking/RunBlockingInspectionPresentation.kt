// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ui.DefaultInspectionToolPresentation
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.profile.codeInspection.ui.readHTML
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.StartupUiUtil.labelFont
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

private class RunBlockingInspectionPresentation(
    toolWrapper: InspectionToolWrapper<*, *>,
    context: GlobalInspectionContextImpl
) : DefaultInspectionToolPresentation(toolWrapper, context) {

    override fun getCustomPreviewPanel(descriptor: CommonProblemDescriptor, parent: Disposable): JComponent? {
        if (descriptor is RunBlockingProblemDescriptor) {
            val htmlView = DescriptionEditorPane()
            val css = (htmlView.editorKit as HTMLEditorKit).styleSheet
            css.addRule("p.problem-description-group {text-indent: " + scale(9) + "px;font-weight:bold;}")
            css.addRule("div.problem-description {margin-left: " + scale(9) + "px;}")
            css.addRule("ul {margin-left:" + scale(10) + "px;text-indent: 0}")
            css.addRule("code {font-family:" + labelFont.family + "}")
            htmlView.addHyperlinkListener(object : HyperlinkAdapter() {
                override fun hyperlinkActivated(e: HyperlinkEvent) {
                    val url = e.url ?: return
                    val ref: @NonNls String? = url.ref
                    val offset = ref!!.toInt()
                    var fileURL = url.toExternalForm()
                    fileURL = fileURL.substring(0, fileURL.indexOf('#'))
                    var vFile = VirtualFileManager.getInstance().findFileByUrl(fileURL)
                    if (vFile == null) {
                        vFile = VfsUtil.findFileByURL(url)
                    }
                    if (vFile != null) {
                        val currentDescriptor =
                            PsiNavigationSupport.getInstance().createNavigatable(project, vFile, offset)
                        currentDescriptor.navigate(true)
                    }
                }
            })
            htmlView.readHTML(getDescription(descriptor.trace))
            return htmlView
        }
        return null
    }
    
    private fun getDescription(callStack: List<TraceElement>): String {
        return buildString {
            append("<div class=\"problem-description\"><h>${KotlinBundle.message("inspection.runblocking.presentation.text")}</h><p><ul>")
            callStack.forEach { append("<li><a HREF=${it.url}>${it.fqName} (${it.fileAndLine})</a></li>") }
            append("</ul></p></div>")
        }
    }
}