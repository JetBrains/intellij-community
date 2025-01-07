// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.k2

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiEditorUtil
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport.CreateObjectAndMoveProhibitedDeclarationsQuickFixBase
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport.MoveProhibitedDeclarationsToTopLevelFixBase
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.ClassOrObject
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.Declaration
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClass

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

    CommandProcessor.getInstance().runUndoTransparentAction {
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return@runUndoTransparentAction

      val companionObject = startElement as KtObjectDeclaration

      val companionObjectInExtensionInspectionSupport = CompanionObjectInExtensionInspectionSupport.getInstance()
      val declarationsToMove = companionObjectInExtensionInspectionSupport.getProhibitedDeclarations(companionObject)
      if (declarationsToMove.isEmpty()) return@runUndoTransparentAction

      val createdObject = createObject(project, companionObject)

      moveDeclarationsOutOfCompanionObject(
        project,
        declarationsToMove = declarationsToMove,
        companionObject = companionObject,
        moveTarget = ClassOrObject(createdObject),
        preprocessDeclaration = { removeProtectedModifierIfPresent(it) }
      ) {
        if (createdObject.declarations.isEmpty()) {
          WriteAction.run<Throwable> { createdObject.delete() }
        }
        else {
          companionObjectInExtensionInspectionSupport.invokeRenameFix(project, file, PsiEditorUtil.findEditor(createdObject), createdObject)
        }
      }
    }
  }
}

private fun removeJvmStaticAnnotationIfPresent(declaration: KtNamedDeclaration): KtNamedDeclaration {
  val annotationToSearch = JvmStandardClassIds.Annotations.JvmStatic.asSingleFqName()
  val targetShortName = annotationToSearch.shortName().asString()
  val targetAliasName = declaration.containingKtFile.findAliasByFqName(annotationToSearch)?.name
  declaration.annotationEntries.firstOrNull { annotationEntry ->
    val annotationShortName = annotationEntry.shortName?.asString() ?: return@firstOrNull false
    annotationShortName == targetShortName || annotationShortName == targetAliasName
  }?.delete()
  return declaration
}

private fun removeProtectedModifierIfPresent(declaration: KtNamedDeclaration): KtNamedDeclaration {
  return declaration.apply {
    removeModifier(KtTokens.PROTECTED_KEYWORD)
    if (this is KtProperty) this.setter?.removeModifier(KtTokens.PROTECTED_KEYWORD)
  }
}

private fun moveDeclarationsOutOfCompanionObject(
  project: Project,
  declarationsToMove: List<KtNamedDeclaration>,
  companionObject: KtObjectDeclaration,
  moveTarget: Declaration<*>,
  preprocessDeclaration: (KtNamedDeclaration) -> KtNamedDeclaration = { it },
  onRefactoringExit: () -> Unit = { },
) {
  if (companionObject.containingClass() == null) return

  val moveDescriptor = K2MoveDescriptor.Declarations(
    project = project,
    source = K2MoveSourceDescriptor.ElementSource(declarationsToMove),
    target = moveTarget
  )

  val descriptor = K2MoveOperationDescriptor.Declarations(
    project,
    listOf(moveDescriptor),
    searchForText = false,
    searchInComments = false,
    searchReferences = true,
    dirStructureMatchesPkg = false,
    moveCallBack = {
      if (companionObject.isValid && companionObject.declarations.isEmpty()) {
        WriteAction.run<Throwable> { companionObject.delete() }
        onRefactoringExit()
      }
    }
  )

  val processor = object : K2MoveDeclarationsRefactoringProcessor(descriptor) {
    override fun preDeclarationMoved(declaration: KtNamedDeclaration) {
      preprocessDeclaration(declaration)
    }
  }

  // Need to set this for the conflict dialog to be shown
  processor.setPrepareSuccessfulSwingThreadCallback { }
  processor.run()
}

private fun createObject(project: Project, companionObject: KtObjectDeclaration): KtObjectDeclaration {
  val nameSuggestion = CompanionObjectInExtensionInspectionSupport.getInstance().suggestNameForObjectInstance(companionObject)

  val psiFactory = KtPsiFactory(project)

  return WriteAction.compute<KtObjectDeclaration, Throwable> {
    val createdObject = psiFactory.createObject("object $nameSuggestion {\n}")
    companionObject.parent.addBefore(createdObject, companionObject) as KtObjectDeclaration
  }
}

private class MoveProhibitedDeclarationsToTopLevelFix(companionObject: KtObjectDeclaration)
  : MoveProhibitedDeclarationsToTopLevelFixBase(companionObject) {
  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val companionObject = startElement as KtObjectDeclaration
    val declarationsToMove = CompanionObjectInExtensionInspectionSupport.getInstance().getProhibitedDeclarations(companionObject)
    if (declarationsToMove.isEmpty()) return

    moveDeclarationsOutOfCompanionObject(
      project,
      declarationsToMove = declarationsToMove,
      companionObject = companionObject,
      moveTarget = K2MoveTargetDescriptor.File(companionObject.containingKtFile),
      preprocessDeclaration = {
        removeProtectedModifierIfPresent(it)
        removeJvmStaticAnnotationIfPresent(it)
      }
    )
  }
}
