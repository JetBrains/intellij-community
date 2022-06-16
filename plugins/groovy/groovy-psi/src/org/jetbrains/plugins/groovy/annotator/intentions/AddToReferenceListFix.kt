// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiReferenceList.Role
import com.intellij.psi.PsiReferenceList.Role.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement

sealed class AddToReferenceListFix(private val role: Role, private val addedName: String, private val hostClassName : String) : LocalQuickFix {

  override fun getName(): String = GroovyBundle.message("intention.name.add.to.clause", addedName, role.getRepresentation(), hostClassName)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement?.castSafelyTo<GrCodeReferenceElement>() ?: return
    val referencedClass = element.parentOfType<PsiClass>() ?: return
    val dependentClass = element.resolve()?.castSafelyTo<PsiClass>() ?: return
    val targetReferenceList = when (role) {
      EXTENDS_LIST -> dependentClass.extendsList
      IMPLEMENTS_LIST -> dependentClass.implementsList
      PERMITS_LIST -> dependentClass.permitsList
      else -> error("Unexpected")
    }
    val referencesRepresentation = if (targetReferenceList == null || targetReferenceList.referenceElements.isEmpty()) {
      role.getRepresentation() + " " + referencedClass.qualifiedName
    }
    else {
      targetReferenceList.text + ", " + referencedClass.qualifiedName
    }
    val factory = GroovyPsiElementFactory.getInstance(referencedClass.project)
    val typeDefinition = factory.createTypeDefinition("class __Dummy $referencesRepresentation {}")
    val newElement = when (role) {
      EXTENDS_LIST -> typeDefinition.extendsClause!!
      IMPLEMENTS_LIST -> typeDefinition.implementsClause!!
      PERMITS_LIST -> typeDefinition.permitsClause!!
      else -> error("Unsupported")
    }
    if (targetReferenceList == null) {
      dependentClass.addAfter(newElement, dependentClass.nameIdentifier)
    }
    else {
      targetReferenceList.replace(newElement)
    }
  }

  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.add.class.to.clause")
}

private fun Role.getRepresentation(): String {
  return when (this) {
    EXTENDS_LIST -> "extends"
    IMPLEMENTS_LIST -> "implements"
    PERMITS_LIST -> "permits"
    else -> error("Unexpected")
  }

}

class AddToPermitsList(addedName: String, hostClassName: String) : AddToReferenceListFix(PERMITS_LIST, addedName, hostClassName)

class AddToExtendsList(addedName: String, hostClassName: String) : AddToReferenceListFix(EXTENDS_LIST, addedName, hostClassName)

class AddToImplementsList(addedName: String, hostClassName: String) : AddToReferenceListFix(IMPLEMENTS_LIST, addedName, hostClassName)