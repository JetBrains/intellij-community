// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrPermitsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList

sealed class AddToReferenceListFix(classToAdd: PsiClass,
                                     referenceList: GrReferenceList) :
  LocalQuickFixAndIntentionActionOnPsiElement(referenceList) {
  protected val className = classToAdd.name

  private val classToAddPointer = SmartPointerManager.createPointer(classToAdd)

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, referenceList: PsiElement, endElement: PsiElement) {
    if (referenceList !is GrReferenceList) return
    val classToAdd = classToAddPointer.element ?: return
    val referencesRepresentation = if (referenceList.keyword == null || referenceList.referenceElementsGroovy.isEmpty()) {
      val keyword = when (referenceList.role) {
        PsiReferenceList.Role.EXTENDS_LIST -> "extends"
        PsiReferenceList.Role.IMPLEMENTS_LIST -> "implements"
        PsiReferenceList.Role.PERMITS_LIST -> "permits"
        else -> error("Unsupported")
      }
      keyword + " " + classToAdd.name
    }
    else {
      referenceList.text + ", " + classToAdd.name
    }
    val factory = GroovyPsiElementFactory.getInstance(classToAdd.project)
    val typeDefinition = factory.createTypeDefinition("class __Dummy $referencesRepresentation {}")
    val newElement = when (referenceList.role) {
      PsiReferenceList.Role.EXTENDS_LIST -> typeDefinition.extendsClause!!
      PsiReferenceList.Role.IMPLEMENTS_LIST -> typeDefinition.implementsClause!!
      PsiReferenceList.Role.PERMITS_LIST -> typeDefinition.permitsClause!!
      else -> error("Unsupported")
    }
    referenceList.replace(newElement)
  }

  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.add.class.to.clause")
}

class AddToPermitsList(classToAdd: PsiClass, permitsList: GrPermitsClause) : AddToReferenceListFix(classToAdd, permitsList) {
  override fun getText(): String = GroovyBundle.message("intention.name.add.to.permits.clause", className)
}

class AddToExtendsList(classToAdd: PsiClass, permitsList: GrExtendsClause) : AddToReferenceListFix(classToAdd, permitsList) {
  override fun getText(): String = GroovyBundle.message("intention.name.add.to.extends.clause", className)
}

class AddToImplementsList(classToAdd: PsiClass, permitsList: GrImplementsClause) : AddToReferenceListFix(classToAdd, permitsList) {
  override fun getText(): String = GroovyBundle.message("intention.name.add.to.implements.clause", className)
}