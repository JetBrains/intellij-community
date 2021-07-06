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
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex

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

            private fun convert(inlayInfoDetails: HintType.InlayInfoDetails, f: PresentationFactory, project: Project): PresentationAndSettings {
                val inlayInfo = inlayInfoDetails.inlayInfo
                val textPresentation = f.smallText(inlayInfo.text)
                val basePresentation = basePresentation(inlayInfoDetails, project, f, textPresentation)
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

            private fun basePresentation(
                inlayInfoDetails: HintType.InlayInfoDetails,
                project: Project,
                factory: PresentationFactory,
                textPresentation: InlayPresentation
            ): InlayPresentation {
                val navigationElement =
                    when (inlayInfoDetails) {
                        is HintType.TypedInlayInfoDetails -> {
                            inlayInfoDetails.type.fqName?.asString()?.run {
                                KotlinFullClassNameIndex.getInstance().get(
                                    this,
                                    project,
                                    GlobalSearchScope.allScope(project)
                                ).firstOrNull()?.navigationElement
                            }
                        }
                        is HintType.PsiInlayInfoDetails -> inlayInfoDetails.element
                        else -> null
                    }
                val basePresentation = navigationElement?.let {
                    factory.psiSingleReference(textPresentation) { it }
                } ?: textPresentation
                return basePresentation
            }

        }
    }

    data class PresentationAndSettings(val presentation: InlayPresentation, val offset: Int, val relatesToPrecedingText: Boolean)
}