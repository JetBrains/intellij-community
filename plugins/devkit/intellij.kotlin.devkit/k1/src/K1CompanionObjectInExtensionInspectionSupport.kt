// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.k1

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.refactoring.ConflictsDialogBase
import com.intellij.refactoring.RefactoringUiService
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport.CreateObjectAndMoveProhibitedDeclarationsQuickFixBase
import org.jetbrains.idea.devkit.kotlin.inspections.CompanionObjectInExtensionInspectionSupport.MoveProhibitedDeclarationsToTopLevelFixBase
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringEventListener
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.MoveKotlinDeclarationsProcessor
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.*

class K1CompanionObjectInExtensionInspectionSupport : CompanionObjectInExtensionInspectionSupport() {
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

  /**
   *  1. Creates a new standalone object.
   *  2. Moves prohibited declarations from the companion object to the created object.
   *  3. Invokes `rename fix` on the created object.
   */
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
        moveTarget = createdObject,
        preprocessDeclaration = { removeProtectedModifierIfPresent(it) },
        // since there is no easy way to detect, whether the conflicts in the view
        // were accepted or rejected and the created object needs to be renamed/deleted
        canShowConflictsInView = false,
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

  private fun createObject(project: Project, companionObject: KtObjectDeclaration): KtObjectDeclaration {
    val nameSuggestion = CompanionObjectInExtensionInspectionSupport.getInstance().suggestNameForObjectInstance(companionObject)

    val psiFactory = KtPsiFactory(project)

    return WriteAction.compute<KtObjectDeclaration, Throwable> {
      val createdObject = psiFactory.createObject("object $nameSuggestion {\n}")
      companionObject.parent.addBefore(createdObject, companionObject) as KtObjectDeclaration
    }
  }
}

private class MoveProhibitedDeclarationsToTopLevelFix(companionObject: KtObjectDeclaration)
  : MoveProhibitedDeclarationsToTopLevelFixBase(companionObject) {

  /**
   * Moves prohibited declarations from the companion object to top-level.
   */
  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val companionObject = startElement as KtObjectDeclaration
    val declarationsToMove = CompanionObjectInExtensionInspectionSupport.getInstance().getProhibitedDeclarations(companionObject)
    if (declarationsToMove.isEmpty()) return

    moveDeclarationsOutOfCompanionObject(
      project,
      declarationsToMove = declarationsToMove,
      companionObject = companionObject,
      moveTarget = companionObject.containingKtFile,
      preprocessDeclaration = {
        removeProtectedModifierIfPresent(it)
        removeJvmStaticAnnotationIfPresent(it)
      }
    )
  }
}

private fun moveDeclarationsOutOfCompanionObject(
  project: Project,
  declarationsToMove: List<KtNamedDeclaration>,
  companionObject: KtObjectDeclaration,
  moveTarget: KtElement,
  preprocessDeclaration: (KtNamedDeclaration) -> KtNamedDeclaration = { it },
  canShowConflictsInView: Boolean = true,
  onRefactoringExit: () -> Unit = { },
) {
  val connection = project.messageBus.connect()
  connection.subscribe(
    KotlinRefactoringEventListener.EVENT_TOPIC,
    object : KotlinRefactoringEventListener {
      override fun onRefactoringExit(refactoringId: String) {
        if (refactoringId == MoveKotlinDeclarationsProcessor.REFACTORING_ID) {
          try {
            if (companionObject.isEmpty) {
              WriteAction.run<Throwable> { companionObject.delete() }
            }
            onRefactoringExit()
          }
          finally {
            connection.disconnect()
          }
        }
      }
    }
  )

  val moveDescriptor = MoveDeclarationsDescriptor(
    project = project,
    moveSource = KotlinMoveSource(declarationsToMove),
    moveTarget = KotlinMoveTarget.ExistingElement(moveTarget),
    delegate = KotlinMoveDeclarationDelegate.NestedClass()
  )

  val mover = object : KotlinMover {
    override fun invoke(originalElement: KtNamedDeclaration, targetContainer: KtElement): KtNamedDeclaration {
      return KotlinMover.Default(preprocessDeclaration(originalElement), targetContainer)
    }
  }

  val moveProcessor = MoveKotlinDeclarationsWithConflictsProcessor(
    descriptor = moveDescriptor,
    mover = mover,
    canShowConflictsInView = canShowConflictsInView
  )

  moveProcessor.run()
}

private val KtObjectDeclaration.isEmpty: Boolean
  get() = this.declarations.isEmpty()

private fun removeProtectedModifierIfPresent(declaration: KtNamedDeclaration): KtNamedDeclaration {
  return declaration.apply {
    removeModifier(KtTokens.PROTECTED_KEYWORD)
    if (this is KtProperty) this.setter?.removeModifier(KtTokens.PROTECTED_KEYWORD)
  }
}

private fun removeJvmStaticAnnotationIfPresent(declaration: KtNamedDeclaration): KtNamedDeclaration {
  return declaration.apply {
    findAnnotation(JvmStandardClassIds.Annotations.JvmStatic)?.delete()
  }
}

/**
 * [MoveKotlinDeclarationsProcessor] with customized conflicts' showing.
 */
private class MoveKotlinDeclarationsWithConflictsProcessor(
  descriptor: MoveDeclarationsDescriptor,
  mover: KotlinMover = KotlinMover.Default,
  private val canShowConflictsInView: Boolean = true,
) : MoveKotlinDeclarationsProcessor(descriptor, mover) {

  init {
    // allows showing the conflicts' dialog
    this.setPrepareSuccessfulSwingThreadCallback(EmptyRunnable.INSTANCE)
  }

  override fun createConflictsDialog(conflicts: MultiMap<PsiElement, String>, usages: Array<out UsageInfo>?): ConflictsDialogBase {
    val doRefactoringRunnable = if (usages != null) Runnable { execute(usages) } else null
    return RefactoringUiService.getInstance().createConflictsDialog(
      myProject,
      conflicts,
      doRefactoringRunnable,
      false,
      canShowConflictsInView,
    )
  }
}
