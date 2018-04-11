// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.ActionRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

internal abstract class CreateMemberAction(
  target: GrTypeDefinition,
  protected open val request: ActionRequest
) : IntentionAction {

  private val myTargetPointer = target.createSmartPointer()

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return myTargetPointer.element != null && request.isValid
  }

  protected val target: GrTypeDefinition
    get() = requireNotNull(myTargetPointer.element) {
      "Don't access this property if isAvailable() returned false"
    }

  open fun getTarget(): JvmClass = target

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = target

  override fun startInWriteAction(): Boolean = true
}
