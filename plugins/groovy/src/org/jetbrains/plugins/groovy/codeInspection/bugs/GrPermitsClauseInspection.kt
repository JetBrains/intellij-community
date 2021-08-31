// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.siyeh.ig.BaseInspection
import com.siyeh.ig.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrPermitsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type

class GrPermitsClauseInspection : BaseInspection() {
  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitElement(element: PsiElement) {
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
          registerError(subclassReferenceElement, subclassDefinition.name, owner.name)
        }
      }
    }
  }

  override fun buildErrorString(vararg infos: Any): String = GroovyBundle.message(
    "inspection.message.invalid.permits.clause.must.directly.extend", infos[0], infos[1])


}