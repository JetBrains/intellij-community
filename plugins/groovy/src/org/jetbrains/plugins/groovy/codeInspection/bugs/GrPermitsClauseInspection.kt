// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.intentions.AddToExtendsList
import org.jetbrains.plugins.groovy.annotator.intentions.AddToImplementsList
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrPermitsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class GrPermitsClauseInspection : BaseInspection() {
  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitElement(element: GroovyPsiElement) {
      if (element !is GrPermitsClause) return super.visitElement(element)
      val owner = element.parentOfType<GrTypeDefinition>()?.takeIf { it.permitsClause === element } ?: return
      val ownerType = owner.type()
      for (subclassReferenceElement in element.referenceElementsGroovy) {
        val subclassDefinition = subclassReferenceElement.resolve() as? GrTypeDefinition ?: continue

        val targetReferenceList = when {
          owner.isInterface && subclassDefinition.isInterface -> subclassDefinition.extendsListTypes
          owner.isInterface -> subclassDefinition.implementsListTypes
          else -> subclassDefinition.extendsListTypes
        }
        if (ownerType !in targetReferenceList) {
          registerError(subclassReferenceElement,
                        GroovyBundle.message("inspection.message.invalid.permits.clause.must.directly.extend", subclassDefinition.name,
                                             owner.name),
                        arrayOf<LocalQuickFix>(if (targetReferenceList is GrExtendsClause)
                                                 AddToExtendsList(owner, targetReferenceList)
                                               else
                                                 AddToImplementsList(owner, targetReferenceList as GrImplementsClause)),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
      }
    }
  }

  override fun buildErrorString(vararg infos: Any): String = ""
}