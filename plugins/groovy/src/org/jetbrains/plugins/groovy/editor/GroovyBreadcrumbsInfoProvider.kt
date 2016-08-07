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
package org.jetbrains.plugins.groovy.editor

import com.intellij.lang.Language
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.RefactoringDescriptionLocation.WITH_PARENT
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

class GroovyBreadcrumbsInfoProvider : BreadcrumbsInfoProvider() {

  private companion object {
    val ourLanguages: Array<Language> = arrayOf(GroovyLanguage.INSTANCE)
  }

  override fun getLanguages() = ourLanguages

  override fun acceptElement(e: PsiElement) = when (e) {
    is GrVariableDeclaration -> e.variables.singleOrNull() is GrField
    is GrField -> e is GrEnumConstant
    is GrClosableBlock -> true
    is GrMember -> e.name != null
    else -> false
  }

  override fun getElementInfo(e: PsiElement) = when (e) {
    is GrVariableDeclaration -> e.variables.single().name
    is GrClosableBlock -> "{}"
    is GrAnonymousClassDefinition -> "new ${e.baseClassReferenceGroovy.referenceName}"
    is GrMethod -> "${e.name}()"
    is GrMember -> e.name!!
    else -> throw RuntimeException()
  }

  override fun getElementTooltip(e: PsiElement) = (if (e is GrVariableDeclaration) e.variables.single() else e).let {
    ElementDescriptionUtil.getElementDescription(it, WITH_PARENT)
  }
}
