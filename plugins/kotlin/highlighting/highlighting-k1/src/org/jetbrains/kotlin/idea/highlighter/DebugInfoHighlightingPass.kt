// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.checkers.utils.DebugInfoUtil
import org.jetbrains.kotlin.idea.base.highlighting.shouldHighlightErrors
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * Quick showing possible problems with Kotlin internals in IDEA with tooltips
 */
class DebugInfoHighlightingPass(file: KtFile, document: Document) : AbstractBindingContextAwareHighlightingPassBase(file, document) {

    override fun annotate(element: PsiElement, holder: HighlightInfoHolder) {
        if (element is KtFile && element !is KtCodeFragment) {

            @Suppress("HardCodedStringLiteral")
            fun errorAnnotation(
                expression: PsiElement,
                message: String,
                textAttributes: TextAttributesKey? = KotlinHighlightingColors.DEBUG_INFO
            ) {
                val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .descriptionAndTooltip("[DEBUG] $message")
                    .range(expression.textRange)
                    .also {
                        textAttributes?.let { ta -> it.textAttributes(ta) }
                    }.create()
                holder.add(info)
            }
            try {
                DebugInfoUtil.markDebugAnnotations(element, bindingContext(), object : DebugInfoUtil.DebugInfoReporter() {
                    override fun reportElementWithErrorType(expression: KtReferenceExpression) =
                        errorAnnotation(expression, "Resolved to error element", KotlinHighlightingColors.RESOLVED_TO_ERROR)

                    override fun reportMissingUnresolved(expression: KtReferenceExpression) =
                        errorAnnotation(
                            expression,
                            "Reference is not resolved to anything, but is not marked unresolved"
                        )

                    override fun reportUnresolvedWithTarget(expression: KtReferenceExpression, target: String) =
                        errorAnnotation(
                            expression,
                            "Reference marked as unresolved is actually resolved to $target"
                        )
                })
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Throwable) {
                // TODO
                errorAnnotation(element, "${e.javaClass.canonicalName}: ${e.message}", null)
                e.printStackTrace()
            }

        }
    }

    class Factory : TextEditorHighlightingPassFactory {
        override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
            val useDebugInfoPass =
              psiFile is KtFile &&
              !psiFile.isCompiled &&
              // Temporary workaround to ignore red code in library sources
              psiFile.shouldHighlightErrors() &&
              (isUnitTestMode() || isApplicationInternalMode() && (KotlinIdePlugin.isSnapshot || KotlinIdePlugin.isDev)) &&
              RootKindFilter.projectAndLibrarySources.matches(psiFile) &&
              (!DumbService.isDumb(psiFile.project) || Registry.`is`("ide.dumb.mode.check.awareness"))

            return if (useDebugInfoPass) DebugInfoHighlightingPass(psiFile as KtFile, editor.document) else null
        }
    }

    class Registrar : TextEditorHighlightingPassFactoryRegistrar {
        override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
            registrar.registerTextEditorHighlightingPass(
                Factory(),
                /* runAfterCompletionOf = */ intArrayOf(Pass.UPDATE_ALL),
                /* runAfterStartingOf = */ null,
                /* runIntentionsPassAfter = */ false,
                /* forcedPassId = */ -1
            )
        }
    }

}