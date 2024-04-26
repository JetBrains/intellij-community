// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.hint.HintManager
import com.intellij.ide.util.MemberChooser
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

@ApiStatus.Internal
abstract class AbstractGenerateMembersHandler<T : ClassMember> : LanguageCodeInsightActionHandler {
    abstract val toImplement: Boolean

    fun collectMembersToGenerateUnderProgress(classOrObject: KtClassOrObject): Collection<T> {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously<Collection<T>, RuntimeException>(
            { runReadAction { collectMembersToGenerate(classOrObject) } },
            KotlinBundle.message("dialog.progress.collect.members.to.generate"), true, classOrObject.project
        )
    }

    @RequiresBackgroundThread(generateAssertion = false)
    abstract fun collectMembersToGenerate(classOrObject: KtClassOrObject): Collection<T>

    abstract fun generateMembers(editor: Editor, classOrObject: KtClassOrObject, selectedElements: Collection<T>, copyDoc: Boolean)

    @NlsContexts.DialogTitle
    protected abstract fun getChooserTitle(): String

    @NlsContexts.HintText
    protected abstract fun getNoMembersFoundHint(): String

    protected open fun isValidForClass(classOrObject: KtClassOrObject) = true

    private fun showOverrideImplementChooser(project: Project, members: Collection<T>): MemberChooser<T>? {
        @Suppress("UNCHECKED_CAST")
        val memberArray = members.toTypedArray<ClassMember>() as Array<T>
        val chooser = MemberChooser(memberArray, false, true, project)
        chooser.title = getChooserTitle()
        if (toImplement) {
            chooser.selectElements(memberArray)
        }

        chooser.show()
        if (chooser.exitCode != DialogWrapper.OK_EXIT_CODE) return null
        return chooser
    }

    override fun isValidFor(editor: Editor, file: PsiFile): Boolean {
        if (file !is KtFile) return false
        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
        val classOrObject = elementAtCaret?.getNonStrictParentOfType<KtClassOrObject>()
        return classOrObject != null && isValidForClass(classOrObject)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        invoke(project, editor, file, implementAll = isUnitTestMode())
    }

    fun invoke(project: Project, editor: Editor, file: PsiFile, implementAll: Boolean) {
        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
        val classOrObject = elementAtCaret?.getNonStrictParentOfType<KtClassOrObject>() ?: return

        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return

        val members = collectMembersToGenerateUnderProgress(classOrObject)
        if (members.isEmpty() && !implementAll) {
            HintManager.getInstance().showErrorHint(editor, getNoMembersFoundHint())
            return
        }

        val copyDoc: Boolean
        val selectedElements: Collection<T>

        if (implementAll) {
            selectedElements = members
            copyDoc = false
        } else {
            val chooser = showOverrideImplementChooser(project, members) ?: return
            selectedElements = chooser.selectedElements ?: return
            copyDoc = chooser.isCopyJavadoc
        }

        if (selectedElements.isEmpty()) return

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        generateMembers(editor, classOrObject, selectedElements, copyDoc)
    }

    override fun startInWriteAction(): Boolean = false
}