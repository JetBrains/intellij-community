// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.codeInsight.navigation.actions.GotoSuperAction
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.search.declarationsSearch.findSuperDescriptors
import org.jetbrains.kotlin.psi.*

class GotoSuperActionHandler : CodeInsightActionHandler {
    data class SuperDeclarationsAndDescriptor(val supers: List<PsiElement>, val descriptor: DeclarationDescriptor?) {
        constructor() : this(emptyList(), null)

        companion object {
            fun forDeclarationAtCaret(editor: Editor, file: PsiFile): SuperDeclarationsAndDescriptor {
                val element = file.findElementAt(editor.caretModel.offset) ?: return SuperDeclarationsAndDescriptor()
                val declaration = PsiTreeUtil.getParentOfType<KtDeclaration>(
                    element,
                    KtNamedFunction::class.java,
                    KtClass::class.java,
                    KtProperty::class.java,
                    KtObjectDeclaration::class.java
                ) ?: return SuperDeclarationsAndDescriptor()

                val project = declaration.project
                val descriptor = declaration.resolveToDescriptorIfAny() ?: return SuperDeclarationsAndDescriptor()
                val superDeclarations = findSuperDescriptors(declaration, descriptor).mapNotNull {
                    DescriptorToSourceUtilsIde.getAnyDeclaration(project, it)
                }.toList()

                return SuperDeclarationsAndDescriptor(
                    supers = superDeclarations,
                    descriptor = descriptor
                )
            }
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(GotoSuperAction.FEATURE_ID)

        val (allDeclarations, descriptor) = SuperDeclarationsAndDescriptor.forDeclarationAtCaret(editor, file)
        if (allDeclarations.isEmpty()) return
        if (allDeclarations.size == 1) {
            val navigatable = EditSourceUtil.getDescriptor(allDeclarations[0])
            if (navigatable != null && navigatable.canNavigate()) {
                navigatable.navigate(true)
            }
        } else {
            val message = getTitle(descriptor!!)
            val superDeclarationsArray = PsiUtilCore.toPsiElementArray(allDeclarations)
            val popup = if (descriptor is ClassDescriptor)
                NavigationUtil.getPsiElementPopup(superDeclarationsArray, message)
            else
                NavigationUtil.getPsiElementPopup(superDeclarationsArray, KtFunctionPsiElementCellRenderer(), message)

            popup.showInBestPositionFor(editor)
        }
    }

    private fun getTitle(descriptor: DeclarationDescriptor): String? =
        when (descriptor) {
            is ClassDescriptor -> KotlinBundle.message("goto.super.chooser.class.title")
            is PropertyDescriptor -> KotlinBundle.message("goto.super.chooser.property.title")
            is SimpleFunctionDescriptor -> KotlinBundle.message("goto.super.chooser.function.title")
            else -> null
        }

    override fun startInWriteAction() = false
}
