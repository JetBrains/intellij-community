// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

val VARIABLE_MODIFIERS: Set<@NlsSafe String> = setOf(GrModifier.DEF, GrModifier.FINAL)

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
                                       @InspectionMessage message: String,
                                       holder: AnnotationHolder) {
  val modifierElement = modifierList.getModifier(modifier) ?: return
  var builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifierElement)
  val fix = GrRemoveModifierFix(modifier, GroovyBundle.message("remove.modifier", modifier))
  builder = registerLocalFix(builder, fix, modifierElement, message, ProblemHighlightType.ERROR, modifierElement.textRange)
  builder.create()
}

internal fun checkTupleVariableIsNotAllowed(variableDeclaration: GrVariableDeclaration,
                                            holder: AnnotationHolder,
                                            @InspectionMessage errorMessage: String,
                                            allowedTokens: Set<IElementType>) {
  if (!variableDeclaration.isTuple) return
  val list = variableDeclaration.modifierList

  val last = PsiUtil.skipWhitespacesAndComments(list.getLastChild(), false)
  if (last != null) {
    val type = last.getNode().getElementType()
    if (type !in allowedTokens) {
      holder.newAnnotation(HighlightSeverity.ERROR, errorMessage).range(list).create()
    }
  } else {
    holder.newAnnotation(HighlightSeverity.ERROR, errorMessage).range(list).create()
  }
}

internal fun registerLocalFix(annotationBuilder: AnnotationBuilder,
                              fix: LocalQuickFix,
                              place: PsiElement,
                              @InspectionMessage message: String,
                              problemHighlightType: ProblemHighlightType,
                              range: TextRange): AnnotationBuilder {
  val manager = InspectionManager.getInstance(place.project)
  assert(!place.textRange.isEmpty) { place.containingFile.name }
  val descriptor = manager.createProblemDescriptor(place, place, message, problemHighlightType, true)
  return annotationBuilder.newLocalQuickFix(fix, descriptor).range(range).registerFix()
}

internal fun Annotation.createDescriptor(element: PsiElement): ProblemDescriptor {
  return InspectionManager.getInstance(element.project).createProblemDescriptor(element, element, message, highlightType, true)
}

