// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.move

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReference
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.move.moveInner.MoveInnerToUpperHandler
import org.jetbrains.plugins.groovy.GroovyLanguage

class MoveGroovyStaticInnerClassHandler : MoveHandlerDelegate() {

  override fun canMove(elements: Array<out PsiElement>?, targetContainer: PsiElement?, reference: PsiReference?): Boolean {
    elements?.takeIf { it.size == 1 } ?: return false
    return getTargetElement(elements.single()) != null
  }

  companion object {
    private fun getTargetElement(element: PsiElement): PsiClass? =
      element.parentOfType<PsiClass>()?.takeIf { it.hasModifierProperty(PsiModifier.STATIC) && it.parentOfType<PsiClass>() != null }
  }

  override fun tryToMove(element: PsiElement?,
                         project: Project?,
                         dataContext: DataContext?,
                         reference: PsiReference?,
                         editor: Editor?): Boolean {
    project ?: return false
    element ?: return false
    dataContext ?: return false
    val elementToMove = getTargetElement(element) ?: return false
    val targetContainer = LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext)
    val handler = MoveInnerToUpperHandler()
    handler.doMove(project, arrayOf(elementToMove), targetContainer, null)
    return true
  }

  override fun getActionName(elements: Array<out PsiElement>) = JavaRefactoringBundle.message("move.inner.class.action.name")

  override fun supportsLanguage(language: Language): Boolean = language === GroovyLanguage
}
