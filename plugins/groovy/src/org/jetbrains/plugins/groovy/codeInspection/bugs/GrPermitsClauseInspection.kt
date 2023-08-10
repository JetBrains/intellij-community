// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.intentions.AddToExtendsList
import org.jetbrains.plugins.groovy.annotator.intentions.AddToImplementsList
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.SealedHelper.inferReferencedClass
import org.jetbrains.plugins.groovy.lang.psi.util.getAllPermittedClassElements
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class GrPermitsClauseInspection : BaseInspection() {
  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {


    override fun visitTypeDefinition(typeDefinition: GrTypeDefinition) {
      val permittedClassElements = getAllPermittedClassElements(typeDefinition).filter { it !is PsiClass }
      if (permittedClassElements.isNotEmpty()) {
        checkPermittedClasses(typeDefinition, permittedClassElements)
      }
    }

    fun checkPermittedClasses(baseClass: GrTypeDefinition, permittedElements: List<PsiElement>) {
      val ownerType = baseClass.type()
      for (element in permittedElements) {
        val subClass = inferReferencedClass(element) as? GrTypeDefinition ?: continue
        val targetReferenceList =
          when {
            baseClass.isInterface && subClass.isInterface -> subClass.extendsClause
            baseClass.isInterface -> subClass.implementsClause
            else -> subClass.extendsClause
          } ?: continue
        if (ownerType !in targetReferenceList.referencedTypes) {
          registerError(
            element,
            GroovyBundle.message("inspection.message.invalid.permits.clause.must.directly.extend", subClass.name, baseClass.name),
            arrayOf<LocalQuickFix>(
              if (targetReferenceList is GrExtendsClause)
                AddToExtendsList(baseClass.name ?: "", element.text)
              else
                AddToImplementsList(baseClass.name ?: "", element.text)),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
      }
    }
  }

  override fun buildErrorString(vararg infos: Any): String = ""
}