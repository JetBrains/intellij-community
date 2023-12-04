// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList

class GrChangeModifiersFix(@FileModifier.SafeFieldForPreview private val modifiersToRemove: List<String>,
                           private val modifierToInsert: String?,
                           @Nls private val textRepresentation: String,
                           private val removeModifierUnderCaret : Boolean = false)
  : PsiUpdateModCommandAction<PsiElement>(PsiElement::class.java) {

  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.replace.modifiers")

  override fun getPresentation(context: ActionContext, element: PsiElement): Presentation {
    return Presentation.of(textRepresentation)
  }

  override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
    val owner = element.parentOfType<PsiModifierListOwner>() ?: return
    val elementUnderCaretRepresentation = element.text
    val modifiers = owner.modifierList?.asSafely<GrModifierList>()?.modifiers ?: return

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
      }
      else {
        owner.modifierList?.addAnnotation(modifierToInsert)
      }
    }
  }
}