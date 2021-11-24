// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import com.intellij.util.castSafelyTo
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList

class GrChangeModifiersFix(private val modifiersToRemove: List<String>,
                           val modifierToInsert: String?,
                           @Nls private val textRepresentation: String,
                           val removeModifierUnderCaret : Boolean = false)
  : IntentionAction {
  val modifiersToRemoveSet = modifiersToRemove.toSet()

  override fun startInWriteAction(): Boolean = true

  override fun getText(): String {
    return textRepresentation
  }

  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.replace.modifiers")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    editor ?: return false
    file ?: return false
    return true // intended to be invoked alongside with an inspection
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    editor ?: return
    file ?: return
    val elementUnderCaret = file.findElementAt(editor.caretModel.offset) ?: return
    val owner = elementUnderCaret.parentOfType<PsiModifierListOwner>() ?: return
    val elementUnderCaretRepresentation = elementUnderCaret.text
    val modifiers = owner.modifierList?.castSafelyTo<GrModifierList>()?.modifiers ?: return

    var hasRequiredModifier = false

    for (modifier in modifiers) {
      val modifierRepresentation = if (modifier is PsiAnnotation) modifier.qualifiedName else modifier.text
      if (modifierRepresentation == modifierToInsert) {
        hasRequiredModifier = true
      }
      if (!removeModifierUnderCaret && modifierRepresentation == elementUnderCaretRepresentation) {
        continue
      }
      if (modifierRepresentation in modifiersToRemove) {
        modifier.delete()
      }
    }
    if (!hasRequiredModifier && modifierToInsert != null) {
      if (modifierToInsert in GrModifier.GROOVY_MODIFIERS || modifierToInsert in PsiModifier.MODIFIERS) {
        owner.modifierList?.setModifierProperty(modifierToInsert, true)
      } else {
        owner.modifierList?.addAnnotation(modifierToInsert)
      }
    }
  }
}