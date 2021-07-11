// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

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

                val f = factory
                resolved.provideHintDetails(element)
                        .map { convert(it, f, editor.project ?: element.project) }
                        .forEach { p ->
                            sink.addInlineElement(p.offset, p.relatesToPrecedingText, p.presentation, hintsArePlacedAtTheEndOfLine)
                        }

                return true
            }

            private fun convert(
                infoDetails: InlayInfoDetails,
                f: PresentationFactory, project: Project
            ): PresentationAndSettings {
                val inlayInfo = infoDetails.inlayInfo
                val details = mergeAdjacentTextInlayInfoDetails(infoDetails.details)
                val basePresentation = when (details.size) {
                    1 -> convert(details.first(), f, project)
                    else -> f.seq(*(details.map { convert(it, f, project) }.toTypedArray()))
                }
                val roundedPresentation = f.roundWithBackground(basePresentation)
                val finalPresentation = InsetPresentation(
                    MenuOnClickPresentation(roundedPresentation, project) {
                        val provider = this@KotlinAbstractHintsProvider
                        listOf(
                            InlayProviderDisablingAction(provider.name, file.language, project, provider.key),
                            ShowInlayHintsSettings()
                        )
                    }, left = 1
                )

                return PresentationAndSettings(finalPresentation, inlayInfo.offset, inlayInfo.relatesToPrecedingText)
            }

            private fun mergeAdjacentTextInlayInfoDetails(details: List<InlayInfoDetail>): List<InlayInfoDetail> {
                val result = mutableListOf<InlayInfoDetail>()
                val iterator = details.iterator()
                var builder: StringBuilder? = null
                while (iterator.hasNext()) {
                    when (val next = iterator.next()) {
                        is TextInlayInfoDetail -> {
                            builder = builder ?: StringBuilder()
                            builder.append(next.text)
                        }
                        else -> {
                            builder?.let {
                                result.add(TextInlayInfoDetail(it.toString()))
                                builder = null
                            }
                            result.add(next)
                        }
                    }
                }
                builder?.let {
                    result.add(TextInlayInfoDetail(it.toString()))
                    builder = null
                }
                return result
            }

            private fun convert(
                details: InlayInfoDetail,
                f: PresentationFactory,
                project: Project
            ): InlayPresentation {
                val textPresentation = f.smallText(details.text)
                val navigationElement = when(details) {
                    is PsiInlayInfoDetail -> details.element
                    is TypeInlayInfoDetail -> details.fqName?.run {
                        project.resolveClass(this)?.navigationElement
                    }
                    else -> null
                }
                val basePresentation = navigationElement?.let {
                    factory.psiSingleReference(textPresentation, withDebugToString = true) { it }
                } ?: textPresentation
                return basePresentation
            }

        }
    }

    data class PresentationAndSettings(val presentation: InlayPresentation, val offset: Int, val relatesToPrecedingText: Boolean)
}