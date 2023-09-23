// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.generation.actions.PresentableCodeInsightActionHandler
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.navigation.actions.GotoSuperAction
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.util.EditSourceUtil
import com.intellij.idea.ActionsBundle
import com.intellij.java.JavaBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.search.declarationsSearch.findSuperDescriptors
import org.jetbrains.kotlin.psi.*

class GotoSuperActionHandler : PresentableCodeInsightActionHandler {
    data class SuperDeclarationsAndDescriptor(val supers: List<PsiElement>, val descriptor: DeclarationDescriptor?) {
        constructor() : this(emptyList(), null)

        companion object {
            fun forDeclarationAtCaret(editor: Editor, file: PsiFile): SuperDeclarationsAndDescriptor {
                val declaration = findDeclarationAtCaret(editor, file) ?: return SuperDeclarationsAndDescriptor()

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

            fun findDeclarationAtCaret(editor: Editor, file: PsiFile): KtDeclaration? {
                val element = file.findElementAt(editor.caretModel.offset) ?: return null
                return findDeclaration(element)
            }

            fun findDeclaration(element: PsiElement): KtDeclaration? {
                val declaration = PsiTreeUtil.getParentOfType<KtDeclaration>(
                    element,
                    KtNamedFunction::class.java,
                    KtClassOrObject::class.java,
                    KtProperty::class.java,
                ) ?: return null

                return declaration.takeUnless { it is KtEnumEntry } ?: findDeclaration(declaration.parent)
            }
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(GotoSuperAction.FEATURE_ID)

        val declarationAtCaret = SuperDeclarationsAndDescriptor.forDeclarationAtCaret(editor, file)
        val supers = declarationAtCaret.supers
        if (supers.isEmpty()) return
        if (supers.size == 1) {
            val navigatable = EditSourceUtil.getDescriptor(supers[0])
            if (navigatable != null && navigatable.canNavigate()) {
                navigatable.navigate(true)
            }
        } else {
            val descriptor = declarationAtCaret.descriptor
            val message = getTitle(descriptor!!)
            PsiTargetNavigator { supers }
                .also {
                    if (descriptor !is ClassDescriptor) {
                        it.presentationProvider(KtFunctionPsiElementCellRenderer())
                    }
                }
                .navigate(editor, message)
        }
    }

    override fun update(editor: Editor, file: PsiFile, presentation: Presentation?) {
        update(editor, file, presentation, null)
    }

    override fun update(editor: Editor, file: PsiFile, presentation: Presentation?, actionPlace: String?) {
        if (presentation == null) return

        val useShortName = actionPlace != null && (ActionPlaces.MAIN_MENU == actionPlace || ActionPlaces.isPopupPlace(actionPlace))
        when (val declaration = SuperDeclarationsAndDescriptor.findDeclarationAtCaret(editor, file)) {
            is KtClassOrObject -> {
                val superTypes = declaration.superTypeListEntries
                presentation.text = when {
                    superTypes.all { it is KtSuperTypeEntry } -> KotlinBundle.message(
                        if (useShortName) "action.GotoSuperInterface.MainMenu.text" else "action.GotoSuperInterface.text"
                    )

                    superTypes.singleOrNull() is KtSuperTypeCallEntry -> KotlinBundle.message(
                        if (useShortName) "action.GotoSuperClass.MainMenu.text" else "action.GotoSuperClass.text"
                    )

                    else -> JavaBundle.message(
                        if (useShortName) "action.GotoSuperClass.MainMenu.text" else "action.GotoSuperClass.text"
                    )
                }

                presentation.description = JavaBundle.message("action.GotoSuperClass.description")
            }

            is KtProperty -> {
                presentation.text = KotlinBundle.message(
                    if (useShortName) "action.GotoSuperProperty.MainMenu.text" else "action.GotoSuperProperty.text"
                )

                presentation.description = KotlinBundle.message("action.GotoSuperProperty.description")
            }

            else -> {
                presentation.text = ActionsBundle.actionText(
                    if (useShortName) "GotoSuperMethod.MainMenu" else "GotoSuperMethod"
                )

                presentation.description = ActionsBundle.actionDescription("GotoSuperMethod")
            }
        }
    }

    @Nls
    private fun getTitle(descriptor: DeclarationDescriptor): String? = when (descriptor) {
        is ClassDescriptor -> KotlinBundle.message("goto.super.chooser.class.title")
        is PropertyDescriptor -> KotlinBundle.message("goto.super.chooser.property.title")
        is SimpleFunctionDescriptor -> KotlinBundle.message("goto.super.chooser.function.title")
        else -> null
    }


    override fun startInWriteAction() = false
}
