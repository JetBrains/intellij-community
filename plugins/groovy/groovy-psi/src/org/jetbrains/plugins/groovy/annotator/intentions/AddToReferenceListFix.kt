// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiReferenceList.Role
import com.intellij.psi.PsiReferenceList.Role.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement

sealed class AddToReferenceListFix(private val role: Role, private val addedName: String, private val hostClassName : String) : LocalQuickFix {

  override fun getName(): String = GroovyBundle.message("intention.name.add.to.clause", addedName, role.getRepresentation(), hostClassName)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val (dependentClass, listToInsert) = generateNewList(descriptor) ?: return
    appendReferenceList(dependentClass, listToInsert)
  }

  private fun generateNewList(descriptor: ProblemDescriptor) : Pair<PsiClass, GrReferenceList>? {
    val element = descriptor.psiElement?.asSafely<GrCodeReferenceElement>() ?: return null
    val referencedClass = element.parentOfType<PsiClass>() ?: return null
    val dependentClass = element.resolve()?.asSafely<PsiClass>() ?: return null
    val targetReferenceList = getReplacedList(dependentClass)
    val referencesRepresentation = if (targetReferenceList == null || targetReferenceList.referenceElements.isEmpty()) {
      role.getRepresentation() + " " + referencedClass.qualifiedName
    }
    else {
      targetReferenceList.text + ", " + referencedClass.qualifiedName
    }
    val factory = GroovyPsiElementFactory.getInstance(referencedClass.project)
    val typeDefinition = factory.createTypeDefinition("class __Dummy $referencesRepresentation {}")
    return dependentClass to (getReplacedList(typeDefinition).asSafely<GrReferenceList>() ?: return null)
  }

  private fun getReplacedList(dependentClass: PsiClass) = when (role) {
    EXTENDS_LIST -> dependentClass.extendsList
    IMPLEMENTS_LIST -> dependentClass.implementsList
    PERMITS_LIST -> dependentClass.permitsList
    else -> error("Unexpected")
  }

  private fun appendReferenceList(dependentClass : PsiClass, listToInsert: GrReferenceList) {
    val listToReplace = getReplacedList(dependentClass)
    if (listToReplace == null) {
      dependentClass.addAfter(listToInsert, dependentClass.nameIdentifier)
    }
    else {
      listToReplace.replace(listToInsert)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val (dependentClass, newList) = generateNewList(previewDescriptor) ?: return IntentionPreviewInfo.EMPTY
    val copy = dependentClass.copy() as PsiClass
    appendReferenceList(copy, newList)
    return IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, dependentClass.text, copy.text)
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