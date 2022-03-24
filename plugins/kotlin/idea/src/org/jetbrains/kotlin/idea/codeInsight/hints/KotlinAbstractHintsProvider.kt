// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("UnstableApiUsage")
abstract class KotlinAbstractHintsProvider<T : Any> : InlayHintsProvider<T> {

    override val previewText: String? = ""

    open val hintsArePlacedAtTheEndOfLine = false

    abstract fun isElementSupported(resolved: HintType?, settings: T): Boolean

    override fun createFile(project: Project, fileType: FileType, document: Document): PsiFile =
        createKtFile(project, document, fileType)

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: T, sink: InlayHintsSink): InlayHintsCollector? {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val project = editor.project ?: element.project
                if (DumbService.isDumb(project)) return true
                val resolved = HintType.resolve(element).takeIf { it.isNotEmpty() } ?: return true
                val f = factory
                resolved.forEach { hintType ->
                    if (isElementSupported(hintType, settings)) {
                        hintType.provideHintDetails(element).forEach { details ->
                            val p = PresentationAndSettings(
                                getInlayPresentationForInlayInfoDetails(details, f, project, this@KotlinAbstractHintsProvider),
                                details.inlayInfo.offset,
                                details.inlayInfo.relatesToPrecedingText
                            )
                            sink.addInlineElement(p.offset, p.relatesToPrecedingText, p.presentation, hintsArePlacedAtTheEndOfLine)
                        }
                    }
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
            if (details.size <= 1) return details

            val result = mutableListOf<InlayInfoDetail>()
            val iterator = details.iterator()
            var builder: StringBuilder? = null
            var smallText = false
            while (iterator.hasNext()) {
                when (val next = iterator.next()) {
                    is TextInlayInfoDetail -> {
                        smallText = smallText or next.smallText
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
                result.add(TextInlayInfoDetail(it.toString(), smallText = smallText))
                builder = null
            }
            return result
        }

        private fun getInlayPresentationForInlayInfoDetail(
            details: InlayInfoDetail,
            factory: PresentationFactory,
            project: Project
        ): InlayPresentation {
            val textPresentation =
                details.safeAs<TextInlayInfoDetail>()?.run {
                    if (!smallText) factory.text(details.text) else null
                } ?: factory.smallText(details.text)

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

        internal fun createKtFile(
            project: Project,
            document: Document,
            fileType: FileType
        ): KtFile {
            val factory = KtPsiFactory(project)
            val file = factory.createPhysicalFile("dummy.kt", document.text)
            FileTypeIndex.processFiles(fileType, Processor { virtualFile ->
                virtualFile.toPsiFile(project).safeAs<KtFile>()?.let {
                    file.analysisContext = it
                    false
                } ?: true
            }, GlobalSearchScope.projectScope(project))

            return file
        }
    }

    data class PresentationAndSettings(val presentation: InlayPresentation, val offset: Int, val relatesToPrecedingText: Boolean)
}