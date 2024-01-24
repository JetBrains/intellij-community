// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetails
import org.jetbrains.kotlin.idea.codeInsight.hints.NamedInlayInfoOption
import org.jetbrains.kotlin.idea.codeInsight.hints.NoInlayInfoOption
import org.jetbrains.kotlin.idea.codeInsight.hints.PsiInlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.TextInlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.TypeInlayInfoDetail
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.ifEmpty
import kotlin.let

abstract class AbstractKotlinInlayHintsProvider(private vararg val hintTypes: HintType): InlayHintsProvider {

    override fun createCollector(
        file: PsiFile,
        editor: Editor
    ): InlayHintsCollector? {
        val project = editor.project ?: file.project
        if (project.isDefault) return null

        return object : SharedBypassCollector {
            override fun collectFromElement(
                element: PsiElement,
                sink: InlayTreeSink
            ) {
                val resolved = hintTypes.filter { it.isApplicable(element) }.ifEmpty { return }

                resolved.forEach { hintType ->
                    hintType.provideHintDetails(element).forEach { details: InlayInfoDetails ->
                        val inlayInfo: InlayInfo = details.inlayInfo
                        details.option?.let {
                            when (it) {
                                is NamedInlayInfoOption -> sink.whenOptionEnabled(it.name) {
                                    addInlayInfo(sink, inlayInfo, details)
                                }

                                NoInlayInfoOption -> addInlayInfo(sink, inlayInfo, details)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        internal fun PresentationTreeBuilder.addInlayInfoDetail(detail: InlayInfoDetail) {
                when (detail) {
                    is TextInlayInfoDetail -> text(detail.text)
                    is TypeInlayInfoDetail ->
                        text(detail.text,
                             detail.fqName?.let {
                                 InlayActionData(
                                     StringInlayActionPayload(it),
                                     KotlinFqnDeclarativeInlayActionHandler.HANDLER_NAME
                                 )
                             })

                    is PsiInlayInfoDetail ->
                        text(detail.text,
                             detail.element.createSmartPointer().let {
                                 InlayActionData(
                                     PsiPointerInlayActionPayload(it),
                                     PsiPointerInlayActionNavigationHandler.HANDLER_ID
                                 )
                             })

                    else -> {}
                }
        }

        internal fun addInlayInfo(sink: InlayTreeSink, inlayInfo: InlayInfo, details: InlayInfoDetails) {
            sink.addPresentation(InlineInlayPosition(inlayInfo.offset, true), hasBackground = true) {
                details.details.forEach { detail ->
                    addInlayInfoDetail(detail)
                }
            }
        }
    }
}