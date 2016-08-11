/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
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

val VARIABLE_MODIFIERS = setOf(GrModifier.DEF, GrModifier.FINAL)

internal fun checkVariableModifiers(holder: AnnotationHolder, variableDeclaration: GrVariableDeclaration) {
  val modifierList = variableDeclaration.modifierList
  for (modifier in GrModifier.GROOVY_MODIFIERS) {
    if (modifier in VARIABLE_MODIFIERS) continue
    checkModifierIsNotAllowed(modifierList, modifier, GroovyBundle.message("variable.cannot.be", modifier), holder)
  }
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