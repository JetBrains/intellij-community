// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.parameterInfo.TYPE_INFO_PREFIX

@Suppress("UnstableApiUsage")
abstract class KotlinAbstractHintsProvider<T : Any> : InlayHintsProvider<T> {

    override val key: SettingsKey<T> = SettingsKey(this::class.simpleName!!)
    override val previewText: String? = ""
    open val hintsArePlacedAtTheEndOfLine = false

    abstract fun isElementSupported(resolved: HintType?, settings: T): Boolean

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: T, sink: InlayHintsSink): InlayHintsCollector? {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val resolved = HintType.resolve(element) ?: return true
                if (!isElementSupported(resolved, settings)) return true

                resolved.provideHints(element)
                        .mapNotNull { info -> convert(info, editor.project) }
                        .forEach { p ->
                            sink.addInlineElement(p.offset, p.relatesToPrecedingText, p.presentation, hintsArePlacedAtTheEndOfLine)
                        }

                return true
            }

            fun convert(inlayInfo: InlayInfo, project: Project?): PresentationAndSettings? {
                val inlayText = getInlayPresentation(inlayInfo.text)
                val presentation = factory.roundWithBackground(factory.smallText(inlayText))

                val finalPresentation = if (project == null) presentation else
                    InsetPresentation(
                        MenuOnClickPresentation(presentation, project) {
                            val provider = this@KotlinAbstractHintsProvider
                            listOf(
                                InlayProviderDisablingAction(provider.name, file.language, project, provider.key),
                                ShowInlayHintsSettings()
                            )
                        }, left = 1
                    )

                return PresentationAndSettings(finalPresentation, inlayInfo.offset, inlayInfo.relatesToPrecedingText)
            }

            fun getInlayPresentation(inlayText: String): String =
                if (inlayText.startsWith(TYPE_INFO_PREFIX)) {
                    inlayText.substring(TYPE_INFO_PREFIX.length)
                } else {
                    "$inlayText:"
                }
        }
    }

    data class PresentationAndSettings(val presentation: InlayPresentation, val offset: Int, val relatesToPrecedingText: Boolean)
}