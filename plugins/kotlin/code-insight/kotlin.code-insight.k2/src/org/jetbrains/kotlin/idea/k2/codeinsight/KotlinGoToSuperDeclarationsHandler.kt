// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.codeInsight.generation.actions.PresentableCodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoSuperAction
import com.intellij.codeInsight.navigation.getPsiElementPopup
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.SuperDeclaration
import org.jetbrains.kotlin.idea.codeInsight.SuperDeclarationProvider
import org.jetbrains.kotlin.psi.*

class KotlinGoToSuperDeclarationsHandler : PresentableCodeInsightActionHandler {
    companion object {
        fun findTargetDeclaration(
            file: PsiFile,
            editor: Editor
        ): KtDeclaration? {
            val element = file.findElementAt(editor.caretModel.offset)?: return null
            return SuperDeclarationProvider.findDeclaration(element)
        }

        fun findSuperDeclarations(targetDeclaration: KtDeclaration): HandlerResult? {
            val superDeclarations =
                SuperDeclarationProvider.findSuperDeclarations(targetDeclaration).takeIf { it.isNotEmpty() } ?: return null

            if (superDeclarations.size == 1) {
                return HandlerResult.Single(superDeclarations.single())
            } else {
                val title = when (targetDeclaration) {
                    is KtClassOrObject, is PsiClass -> KotlinBundle.message("goto.super.chooser.class.title")
                    is KtFunction, is PsiMethod -> KotlinBundle.message("goto.super.chooser.function.title")
                    is KtProperty, is KtParameter -> KotlinBundle.message("goto.super.chooser.property.title")
                    else -> error("Unexpected declaration $targetDeclaration")
                }
                return HandlerResult.Multiple(title, superDeclarations)
            }
        }

        fun gotoSuperDeclarations(targetDeclaration: KtDeclaration) : JBPopup? {
            when (val result = findSuperDeclarations(targetDeclaration)) {
                is HandlerResult.Single -> {
                    result.item.descriptor
                        ?.takeIf { it.canNavigate() }
                        ?.navigate(/* requestFocus = */ true)
                }

                is HandlerResult.Multiple -> {
                    val superDeclarationsArray = result.items
                        .mapNotNull { it.declaration.element }
                        .toTypedArray()

                    if (superDeclarationsArray.isNotEmpty()) {
                        return getPsiElementPopup(superDeclarationsArray, result.title)
                    }
                }

                null -> {}
            }
            return null
        }
    }

    sealed class HandlerResult {
        class Single(val item: SuperDeclaration) : HandlerResult() {
            override val items: List<SuperDeclaration>
                get() = listOf(item)
        }

        class Multiple(val title: @PopupTitle String, override val items: List<SuperDeclaration>) : HandlerResult()

        abstract val items: List<SuperDeclaration>
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is KtFile) {
            return
        }

        FeatureUsageTracker.getInstance().triggerFeatureUsed(GotoSuperAction.FEATURE_ID)

        val targetDeclaration = findTargetDeclaration(file, editor) ?: return
        gotoSuperDeclarations(targetDeclaration)?.showInBestPositionFor(editor)
    }

    override fun update(editor: Editor, file: PsiFile, presentation: Presentation?) {
        if (file !is KtFile) return
        val targetDeclaration = findTargetDeclaration(file, editor) ?: return
        presentation?.text = when (targetDeclaration) {
            is KtClassOrObject, is PsiClass -> KotlinBundle.message("action.GotoSuperClass.MainMenu.text")
            is KtFunction, is PsiMethod -> ActionsBundle.actionText("GotoSuperMethod.MainMenu")
            is KtProperty, is KtParameter -> KotlinBundle.message("action.GotoSuperProperty.MainMenu.text")
            else -> error("Unexpected declaration $targetDeclaration")
        }
    }

    override fun startInWriteAction() = false
}
