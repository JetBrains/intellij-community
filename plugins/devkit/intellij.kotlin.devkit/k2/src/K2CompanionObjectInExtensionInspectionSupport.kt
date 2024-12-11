// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.k2

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport.CreateObjectAndMoveProhibitedDeclarationsQuickFixBase
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport.MoveProhibitedDeclarationsToTopLevelFixBase
import org.jetbrains.kotlin.psi.KtObjectDeclaration

class K2CompanionObjectInExtensionInspectionSupport : CompanionObjectInExtensionInspectionSupport() {
  override fun createRemoveEmptyCompanionObjectFix(companionObject: KtObjectDeclaration): LocalQuickFix {
    return RemoveEmptyCompanionObjectFix(companionObject)
  }

  override fun createMoveProhibitedDeclarationsToTopLevelFix(companionObject: KtObjectDeclaration): LocalQuickFix {
    return MoveProhibitedDeclarationsToTopLevelFix(companionObject)
  }

  override fun createCreateObjectAndMoveProhibitedDeclarationsQuickFix(companionObject: KtObjectDeclaration): LocalQuickFix {
    return CreateObjectAndMoveProhibitedDeclarationsQuickFix(companionObject)
  }
}

private class CreateObjectAndMoveProhibitedDeclarationsQuickFix(companionObject: KtObjectDeclaration)
  : CreateObjectAndMoveProhibitedDeclarationsQuickFixBase(companionObject) {
  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    // TODO
  }
}

private class MoveProhibitedDeclarationsToTopLevelFix(companionObject: KtObjectDeclaration)
  : MoveProhibitedDeclarationsToTopLevelFixBase(companionObject) {
  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    // TODO
  }
}
