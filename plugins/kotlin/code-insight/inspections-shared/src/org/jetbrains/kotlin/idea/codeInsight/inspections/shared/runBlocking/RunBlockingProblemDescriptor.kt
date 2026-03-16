// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking

import com.intellij.codeInspection.ExportedInspectionsResultModifier
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import org.jdom.Element
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

internal class RunBlockingProblemDescriptor(
    psiElement: PsiElement,
    val trace: List<TraceElement>
) : ProblemDescriptorBase(
    psiElement,
    psiElement,
    KotlinBundle.message("inspection.runblocking.presentation.text"),
    arrayOf(),
    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
    false,
    null,
    true,
    false
), ExportedInspectionsResultModifier {
    override fun modifyResult(inspectionResult: Element?) {
        val jdomTrace = Element("trace")
        trace.forEach {
            val jdomTraceElement = Element("trace_element")
            jdomTraceElement.setAttribute("fq_name", it.fqName)
            jdomTraceElement.setAttribute("url", it.url)
            jdomTraceElement.setAttribute("file_and_line", it.fileAndLine)
            jdomTrace.addContent(jdomTraceElement)
        }
        inspectionResult?.addContent(jdomTrace)
    }
}