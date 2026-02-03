// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.editor

import com.intellij.lang.Language
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.RefactoringDescriptionLocation.WITH_PARENT
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

internal class GroovyBreadcrumbsInfoProvider : BreadcrumbsProvider {
  override fun isShownByDefault(): Boolean = false

  override fun getLanguages(): Array<Language> = arrayOf(GroovyLanguage)

  override fun acceptElement(e: PsiElement): Boolean = when (e) {
    is GrVariableDeclaration -> e.variables.singleOrNull() is GrField
    is GrField -> e is GrEnumConstant
    is GrFunctionalExpression -> true
    is GrMember -> e.name != null
    else -> false
  }

  override fun getElementInfo(e: PsiElement): String = when (e) {
    is GrVariableDeclaration -> e.variables.single().name
    is GrFunctionalExpression -> (((e.parent as? GrMethodCall)?.invokedExpression as? GrReferenceExpression)?.referenceName ?: "") + "{}"
    is GrAnonymousClassDefinition -> "new ${e.baseClassReferenceGroovy.referenceName}"
    is GrMethod -> "${e.name}()"
    is GrMember -> e.name!!
    else -> throw RuntimeException()
  }

  override fun getElementTooltip(e: PsiElement): String = (if (e is GrVariableDeclaration) e.variables.single() else e).let {
    ElementDescriptionUtil.getElementDescription(it, WITH_PARENT)
  }
}
