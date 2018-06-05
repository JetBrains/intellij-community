// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration

val VARIABLE_MODIFIERS: Set<String> = setOf(GrModifier.DEF, GrModifier.FINAL)

internal fun checkVariableModifiers(holder: AnnotationHolder, variableDeclaration: GrVariableDeclaration) {
  val modifierList = variableDeclaration.modifierList
  for (modifier in GrModifier.GROOVY_MODIFIERS) {
    if (modifier in VARIABLE_MODIFIERS) continue
    checkModifierIsNotAllowed(modifierList, modifier, GroovyBundle.message("variable.cannot.be", modifier), holder)
  }
}

internal fun checkModifierIsNotAllowed(modifierList: GrModifierList,
                                       @GrModifierConstant modifier: String,
                                       holder: AnnotationHolder) {
  checkModifierIsNotAllowed(modifierList, modifier, GroovyBundle.message("modifier.0.not.allowed", modifier), holder)
}

internal fun checkModifierIsNotAllowed(modifierList: GrModifierList,
                                       @GrModifierConstant modifier: String,
                                       message: String?,
                                       holder: AnnotationHolder) {
  val modifierElement = modifierList.getModifier(modifier) ?: return
  val annotation = holder.createErrorAnnotation(modifierElement, message)
  val fix = GrRemoveModifierFix(modifier, GroovyBundle.message("remove.modifier", modifier))
  registerFix(annotation, fix, modifierElement)
}

internal fun registerFix(annotation: Annotation, fix: LocalQuickFix, place: PsiElement) {
  val manager = InspectionManager.getInstance(place.project)
  assert(!place.textRange.isEmpty) { place.containingFile.name }
  val descriptor = manager.createProblemDescriptor(place, place, annotation.message, annotation.highlightType, true)
  val range = TextRange.create(annotation.startOffset, annotation.endOffset)
  annotation.registerFix(fix, range, null, descriptor)
}

internal fun Annotation.createDescriptor(element: PsiElement): ProblemDescriptor {
  return InspectionManager.getInstance(element.project).createProblemDescriptor(element, element, message, highlightType, true)
}

internal fun Annotation.registerFix(fix: LocalQuickFix, descriptor: ProblemDescriptor): Unit = registerFix(fix, null, null, descriptor)
