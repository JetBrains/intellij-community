// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant

class GrAddMissingCaseSectionsFix(expressions: List<PsiElement>, switch: GrSwitchElement) : ModCommandQuickFix() {
  private val elementsToInsert = expressions.map(SmartPointerManager::createPointer)
  private val switchElement = SmartPointerManager.createPointer(switch)

  private fun getName(element: PsiElement?): String = when (element) {
    null -> "null"
    is PsiClass -> element.name ?: "null"
    is GrEnumConstant -> element.name
    else -> element.text
  }

  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.add.missing.case.branches")

  override fun getName(): String = when (elementsToInsert.size) {
    0 -> GroovyBundle.message("intention.name.insert.default.branch")
    1 -> GroovyBundle.message("intention.name.insert.case.0", getName(elementsToInsert[0].element))
    2 -> GroovyBundle.message("intention.name.insert.case.0.case.1", getName(elementsToInsert[0].element),getName(elementsToInsert[1].element))
    else -> GroovyBundle.message("intention.name.insert.missing.branches")
  }

  override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
    val elements = elementsToInsert.mapNotNull { it.element }
    val namesToInsert = elements.map {
      when (it) {
        is PsiClass -> it.qualifiedName
        is GrEnumConstant -> "${it.containingClass?.qualifiedName}.${it.name}"
        else -> it.text
      }
    }
    val switch = switchElement.element ?: return ModCommand.nop()
    val styleToken = switch.caseSections.firstOrNull()?.colon?.let { ":" } ?: " ->"
    val factory = GroovyPsiElementFactory.getInstance(project)
    val body = "throw new IllegalStateException()"
    val stringsToInsert = when (elements.size) {
      0 -> listOf(factory.createSwitchSection("default$styleToken $body"))
      else -> namesToInsert.map { factory.createSwitchSection("case $it$styleToken $body") }
    }
    return ModCommand.psiUpdate(switch) { s ->
      val anchor = s.lastChild // closing curly bracket
      for (elem in stringsToInsert) {
        s.addBefore(elem, anchor)
      }
    }
  }
}