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
import org.jetbrains.kotlin.idea.KotlinLanguage

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
                        .map {
                            PresentationAndSettings(
                                getInlayPresentationForInlayInfoDetails(it, f, editor.project ?: element.project, this@KotlinAbstractHintsProvider),
                                it.inlayInfo.offset,
                                it.inlayInfo.relatesToPrecedingText
                            )
                        }
                        .forEach { p ->
                            sink.addInlineElement(p.offset, p.relatesToPrecedingText, p.presentation, hintsArePlacedAtTheEndOfLine)
                        }

                return true
            }
        }
    }

    companion object {
        fun getInlayPresentationForInlayInfoDetails(
            infoDetails: InlayInfoDetails,
            factory: PresentationFactory,
            project: Project,
            provider: InlayHintsProvider<*>
        ): InlayPresentation {
            val details = mergeAdjacentTextInlayInfoDetails(infoDetails.details)
            val basePresentation = when (details.size) {
                1 -> getInlayPresentationForInlayInfoDetail(details.first(), factory, project)
                else -> factory.seq(*(details.map { getInlayPresentationForInlayInfoDetail(it, factory, project) }.toTypedArray()))
            }
            val roundedPresentation = factory.roundWithBackground(basePresentation)
            return InsetPresentation(
                MenuOnClickPresentation(roundedPresentation, project) {
                    listOf(
                        InlayProviderDisablingAction(provider.name, KotlinLanguage.INSTANCE, project, provider.key),
                        ShowInlayHintsSettings()
                    )
                }, left = 1
            )
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

        private fun getInlayPresentationForInlayInfoDetail(
            details: InlayInfoDetail,
            factory: PresentationFactory,
            project: Project
        ): InlayPresentation {
            val textPresentation = factory.smallText(details.text)
            val navigationElementProvider: (() -> PsiElement?)? = when(details) {
                is PsiInlayInfoDetail -> {{ details.element }}
                is TypeInlayInfoDetail -> details.fqName?.run {
                    { project.resolveClass(this)?.navigationElement }
                }
                else -> null
            }
            val basePresentation = navigationElementProvider?.let {
                factory.psiSingleReference(textPresentation, withDebugToString = true, it)
            } ?: textPresentation
            return basePresentation
        }
    }

    data class PresentationAndSettings(val presentation: InlayPresentation, val offset: Int, val relatesToPrecedingText: Boolean)
}