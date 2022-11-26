// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.codeInsight.navigation.actions.GotoSuperAction
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.SuperDeclaration
import org.jetbrains.kotlin.idea.codeInsight.SuperDeclarationProvider
import org.jetbrains.kotlin.psi.*

internal class KotlinGoToSuperDeclarationsHandler : CodeInsightActionHandler {
    companion object {
        private val ALLOWED_DECLARATION_CLASSES = arrayOf(
            KtNamedFunction::class.java,
            KtClassOrObject::class.java,
            KtProperty::class.java
        )

        fun findSuperDeclarations(file: KtFile, offset: Int): HandlerResult? {
            val element = file.findElementAt(offset) ?: return null
            val targetDeclaration = PsiTreeUtil.getParentOfType<KtDeclaration>(element, *ALLOWED_DECLARATION_CLASSES) ?: return null
            val superDeclarations = SuperDeclarationProvider.findSuperDeclarations(targetDeclaration).takeIf { it.isNotEmpty() } ?: return null

            if (superDeclarations.size == 1) {
                return HandlerResult.Single(superDeclarations.single())
            } else {
                val title = when (targetDeclaration) {
                    is KtClass -> KotlinBundle.message("goto.super.chooser.class.title")
                    is KtFunction -> KotlinBundle.message("goto.super.chooser.function.title")
                    is KtProperty -> KotlinBundle.message("goto.super.chooser.property.title")
                    else -> error("Unexpected declaration $targetDeclaration")
                }

                return HandlerResult.Multiple(title, superDeclarations)
            }
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

        when (val result = findSuperDeclarations(file, editor.caretModel.offset)) {
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
                    NavigationUtil.getPsiElementPopup(superDeclarationsArray, result.title).showInBestPositionFor(editor)
                }
            }
            null -> {}
        }
    }

    override fun startInWriteAction() = false
}