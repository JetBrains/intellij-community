// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class BatchTemplateRunner(private val project: Project) {
    private val elementsAndFactories = ArrayList<Pair<SmartPsiElementPointer<*>, () -> Template?>>()

    fun addTemplateFactory(element: PsiElement, factory: () -> Template?) {
        elementsAndFactories.add(element.createSmartPointer() to factory)
    }

    fun runTemplates() {
        runTemplates(elementsAndFactories.iterator())
    }

    private fun getEditor(pointer: SmartPsiElementPointer<*>): Editor? {
        val element = pointer.element ?: return null
        val virtualFile = element.containingFile?.virtualFile ?: return null
        val descriptor = OpenFileDescriptor(project, virtualFile, element.textRange.startOffset)
        return FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    private fun runTemplates(iterator: Iterator<Pair<SmartPsiElementPointer<*>, () -> Template?>>) {
        if (!iterator.hasNext()) return

        val manager = TemplateManager.getInstance(project)
        project.executeWriteCommand("") {
            val (pointer, factory) = iterator.next()

            val editor = getEditor(pointer) ?: return@executeWriteCommand
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            val template = factory() ?: return@executeWriteCommand
            manager.startTemplate(
                editor,
                template,
                object : TemplateEditingAdapter() {
                    override fun templateFinished(template: Template, brokenOff: Boolean) {
                        if (brokenOff) return
                        ApplicationManager.getApplication().invokeLater { runTemplates(iterator) }
                    }
                }
            )
        }
    }
}