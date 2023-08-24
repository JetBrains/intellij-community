// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.*
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.refactoring.ConflictsDialogBase
import com.intellij.refactoring.RefactoringUiService
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.ExtensionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringEventListener
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.MoveKotlinDeclarationsProcessor
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class CompanionObjectInExtensionInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {

      override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        if (!declaration.isCompanion()) return

        val ktLightClass = declaration.getStrictParentOfType<KtClass>()?.toLightClass() ?: return

        if (!ExtensionUtil.isExtensionPointImplementationCandidate(ktLightClass)) return
        if (!ExtensionUtil.isInstantiatedExtension(ktLightClass) { ExtensionUtil.hasServiceBeanFqn(it) }) return

        val anchor = declaration.modifierList?.getModifier(KtTokens.COMPANION_KEYWORD) ?: return

        if (declaration.isEmpty) {
          holder.registerProblem(
            anchor,
            DevKitKotlinBundle.message("inspections.empty.companion.object.in.extension.message"),
            ProblemHighlightType.WARNING,
            RemoveEmptyCompanionObjectFix(declaration)
          )
        }
        else {
          val prohibitedDeclarations = declaration.namedDeclarations.filterNot { it.isAllowedInsideCompanionObject() }
          if (prohibitedDeclarations.isEmpty()) return

          holder.registerProblem(
            anchor,
            DevKitKotlinBundle.message("inspections.companion.object.in.extension.message"),
            ProblemHighlightType.WARNING,
            MoveProhibitedDeclarationsToTopLevelFix(declaration),
            CreateObjectAndMoveProhibitedDeclarationsQuickFix(declaration)
          )
        }
      }
    }
  }
}

private val KtObjectDeclaration.namedDeclarations
  get() = declarations.filterIsInstance<KtNamedDeclaration>()

private fun KtDeclaration.isAllowedInsideCompanionObject() = this is KtProperty && (this.isConstVal || this.isLoggerInstance)

private val LOGGER_CLASS_ID = Logger::class.qualifiedName?.let { ClassId.topLevel(FqName(it)) }
                              ?: error("Logger class must have qualified name")

/**
 * This property can be called from the EDT by [CreateObjectAndMoveProhibitedDeclarationsQuickFix],
 * so we have to resort to [allowAnalysisOnEdt].
 */
@OptIn(KtAllowAnalysisOnEdt::class)
private val KtProperty.isLoggerInstance: Boolean
  get() = allowAnalysisOnEdt {
    val property = this

    analyze(property) {
      val propertyReturnType = property.getReturnKtType().withNullability(KtTypeNullability.NON_NULLABLE).expandedClassSymbol
                               ?: return false
      val loggerType = getClassOrObjectSymbolByClassId(LOGGER_CLASS_ID) ?: return false
      propertyReturnType.isSubClassOf(loggerType)
    }
  }

private val KtProperty.isConstVal: Boolean
  get() = this.hasModifier(KtTokens.CONST_KEYWORD) && this.valOrVarKeyword.node.elementType == KtTokens.VAL_KEYWORD

private val KtObjectDeclaration.isEmpty: Boolean
  get() = this.declarations.isEmpty()


private const val DEFAULT_OBJECT_NAME = "Util"


private class CreateObjectAndMoveProhibitedDeclarationsQuickFix(
  companionObject: KtObjectDeclaration
) : LocalQuickFixOnPsiElement(companionObject) {

  override fun getFamilyName(): String = DevKitKotlinBundle.message("inspections.create.object.and.move.prohibited.declarations.fix.text")

  override fun getText(): String = familyName

  override fun startInWriteAction() = false

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  /**
   *  1. Creates a new standalone object.
   *  2. Moves prohibited declarations from the companion object to the created object.
   *  3. Invokes `rename fix` on the created object.
   */
  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    CommandProcessor.getInstance().runUndoTransparentAction {
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return@runUndoTransparentAction

      val companionObject = startElement as KtObjectDeclaration

      val declarationsToMove = companionObject.namedDeclarations.filterNot { it.isAllowedInsideCompanionObject() }
      if (declarationsToMove.isEmpty()) return@runUndoTransparentAction

      val createdObject = createObject(project, companionObject)

      moveDeclarationsOutOfCompanionObject(
        project = project,
        declarationsToMove = declarationsToMove,
        companionObject = companionObject,
        moveTarget = createdObject,
        preprocessDeclaration = { it.removeProtectedModifierIfPresent() },
        // since there is no easy way to detect, whether the conflicts in the view
        // were accepted or rejected and the created object needs to be renamed/deleted
        canShowConflictsInView = false,
      ) {
        if (createdObject.declarations.isEmpty()) {
          WriteAction.run<Throwable> { createdObject.delete() }
        }
        else {
          invokeRenameFix(project, file, PsiEditorUtil.findEditor(createdObject), createdObject)
        }
      }
    }
  }

  private fun createObject(project: Project, companionObject: KtObjectDeclaration): KtObjectDeclaration {
    val nameSuggestion = suggestNameForObjectInstance(companionObject)

    val psiFactory = KtPsiFactory(project)

    return WriteAction.compute<KtObjectDeclaration, Throwable> {
      val createdObject = psiFactory.createObject("object $nameSuggestion {\n}")
      companionObject.parent.addBefore(createdObject, companionObject) as KtObjectDeclaration
    }
  }

  private fun suggestNameForObjectInstance(companionObject: KtObjectDeclaration): String {
    val containingClass = companionObject.containingClass()!!
    analyze(containingClass) {
      return KotlinNameSuggester.suggestNameByName(
        DEFAULT_OBJECT_NAME,
        Fe10KotlinNewDeclarationNameValidator(containingClass, null, KotlinNameSuggestionProvider.ValidatorTarget.CLASS)
      )
    }
  }

  private fun invokeRenameFix(project: Project, file: PsiFile, editor: Editor?, createdObject: KtObjectDeclaration) {
    if (editor == null) return
    val nameIdentifier = createdObject.nameIdentifier ?: return
    editor.caretModel.moveToOffset(nameIdentifier.endOffset)

    val renameFix = QuickFixFactory.getInstance().createRenameFix(createdObject)
    if (renameFix == null || !renameFix.isAvailable(project, editor, file)) return
    renameFix.invoke(project, editor, file)
  }
}

private class MoveProhibitedDeclarationsToTopLevelFix(companionObject: KtObjectDeclaration)
  : LocalQuickFixOnPsiElement(companionObject), HighPriorityAction {

  override fun getFamilyName(): String = DevKitKotlinBundle.message("inspections.move.prohibited.declarations.to.top.level.fix.text")
  override fun getText(): String = familyName

  override fun startInWriteAction(): Boolean = false

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.EMPTY
  }

  /**
   * Moves prohibited declarations from the companion object to top-level.
   */
  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val companionObject = startElement as KtObjectDeclaration

    val declarationsToMove = companionObject.namedDeclarations.filterNot { it.isAllowedInsideCompanionObject() }
    if (declarationsToMove.isEmpty()) return

    moveDeclarationsOutOfCompanionObject(
      project = project,
      declarationsToMove = declarationsToMove,
      companionObject = companionObject,
      moveTarget = companionObject.containingKtFile,
      preprocessDeclaration = {
        it.removeProtectedModifierIfPresent()
        it.removeJvmStaticAnnotationIfPresent()
      }
    )
  }
}

private class RemoveEmptyCompanionObjectFix(companionObject: KtObjectDeclaration) : LocalQuickFixOnPsiElement(companionObject) {
  override fun getFamilyName(): String = DevKitKotlinBundle.message("inspections.remove.empty.companion.object.fix.text")
  override fun getText(): String = familyName

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val objectDeclaration = startElement as KtObjectDeclaration
    if (objectDeclaration.isCompanion() && objectDeclaration.isEmpty) {
      objectDeclaration.delete()
    }
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

private fun KtNamedDeclaration.removeProtectedModifierIfPresent(): KtNamedDeclaration {
  return apply {
    removeModifier(KtTokens.PROTECTED_KEYWORD)
    if (this is KtProperty) this.setter?.removeModifier(KtTokens.PROTECTED_KEYWORD)
  }
}

private fun KtNamedDeclaration.removeJvmStaticAnnotationIfPresent(): KtNamedDeclaration {
  return apply {
    findAnnotation(JvmStandardClassIds.Annotations.JvmStatic)?.delete()
  }
}

/**
 * Moves [declarationsToMove] out of [companionObject] to the [moveTarget],
 * calling [preprocessDeclaration] on every declaration right before moving.
 *
 * Calls [onRefactoringExit] when refactoring is finished, both in cases when it was successfully performed or canceled,
 * and, if [canShowConflictsInView] is true, when conflicts were open in the view but the refactoring can be started over or canceled.
 *
 * Setting the [canShowConflictsInView] parameter helps to track the way the refactoring exited in
 * and perform subsequent actions.
 */
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
