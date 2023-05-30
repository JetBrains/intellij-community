// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiEditorUtil
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.ExtensionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringEventListener
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveDeclarationDelegate
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveSource
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveTarget
import org.jetbrains.kotlin.idea.refactoring.move.MoveDeclarationsDescriptor
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.MoveKotlinDeclarationsProcessor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAbstract

class CompanionObjectInExtensionInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {

      override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        if (!declaration.isCompanion()) return
        val klass = declaration.getStrictParentOfType<KtClass>() ?: return

        if (klass.isAbstract() ||
            klass.isInterface() ||
            klass.isInner() ||
            klass.isLocal ||
            klass.isEnum()) return

        val ktLightClass = klass.toLightClass() ?: return

        if (!ExtensionUtil.isInstantiatedExtension(ktLightClass) { ExtensionUtil.hasServiceBeanFqn(it) }) return

        val prohibitedDeclarations = declaration.declarations.filterNot { it.isAllowedInsideACompanionObject() }
        if (prohibitedDeclarations.isEmpty()) return

        val anchor = declaration.modifierList?.getModifier(KtTokens.COMPANION_KEYWORD) ?: return
        holder.registerProblem(
          anchor,
          DevKitKotlinBundle.message("inspections.companion.object.in.extension.message"),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          CreateObjectAndMoveProhibitedDeclarationsQuickFix(declaration)
        )
      }

    }
  }
}

private fun KtDeclaration.isAllowedInsideACompanionObject() = this is KtProperty && (this.isConstVal || this.isLoggerInstance)

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
      val propertyReturnType = property.getReturnKtType().expandedClassSymbol ?: return false
      val loggerType = getClassOrObjectSymbolByClassId(LOGGER_CLASS_ID) ?: return false

      propertyReturnType.isSubClassOf(loggerType)
    }
  }

private val KtProperty.isConstVal: Boolean
  get() = this.hasModifier(KtTokens.CONST_KEYWORD) && this.valOrVarKeyword.node.elementType == KtTokens.VAL_KEYWORD


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

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    CommandProcessor.getInstance().runUndoTransparentAction {
      val companionObject = startElement as KtObjectDeclaration

      val createdObject = createObject(project, companionObject)
      createdObject?.let {
        moveProhibitedDeclarations(project, companionObject, createdObject)
        invokeRenameFix(project, file, PsiEditorUtil.findEditor(createdObject), createdObject)
      }
    }
  }

  private fun createObject(project: Project, companionObject: KtObjectDeclaration): KtObjectDeclaration? {
    val nameSuggestions = suggestNamesForObjectInstance(companionObject)

    val psiFactory = KtPsiFactory(project)

    if (!FileModificationService.getInstance().prepareFileForWrite(companionObject.containingFile)) return null

    return ApplicationManager.getApplication().runWriteAction(
      Computable {
        val createdObject = psiFactory.createObject("object ${nameSuggestions.first()} {\n}")
        companionObject.parent.addBefore(createdObject, companionObject) as KtObjectDeclaration
      }
    )
  }

  private fun suggestNamesForObjectInstance(companionObject: KtObjectDeclaration): List<String> {
    val containingClass = companionObject.containingClass()!!
    return Fe10KotlinNameSuggester.getCamelNames(
      DEFAULT_OBJECT_NAME,
      Fe10KotlinNewDeclarationNameValidator(containingClass, null, KotlinNameSuggestionProvider.ValidatorTarget.CLASS),
      false
    )
  }

  private fun moveProhibitedDeclarations(project: Project, companionObject: KtObjectDeclaration, createdObject: KtObjectDeclaration) {
    val declarationsToMove = companionObject.namedDeclarations.filterNot { it.isAllowedInsideACompanionObject() }

    val moveDescriptor = MoveDeclarationsDescriptor(
      project,
      KotlinMoveSource(declarationsToMove),
      KotlinMoveTarget.ExistingElement(createdObject),
      KotlinMoveDeclarationDelegate.NestedClass()
    )

    val connection = project.messageBus.connect()

    connection.subscribe(
      KotlinRefactoringEventListener.EVENT_TOPIC,
      object : KotlinRefactoringEventListener {
        override fun onRefactoringExit(refactoringId: String) {
          if (refactoringId == MoveKotlinDeclarationsProcessor.REFACTORING_ID) {
            try {
              if (createdObject.declarations.isEmpty()) {
                createdObject.delete()
              }
            }
            finally {
              connection.disconnect()
            }
          }
        }
      }
    )

    MoveKotlinDeclarationsProcessor(moveDescriptor).run()
  }

  private fun invokeRenameFix(project: Project, file: PsiFile, editor: Editor?, createdObject: KtObjectDeclaration) {
    if (editor == null) return
    val nameIdentifier = createdObject.nameIdentifier ?: return
    editor.caretModel.moveToOffset(nameIdentifier.endOffset)

    val renameFix = QuickFixFactory.getInstance().createRenameFix(createdObject)
    if (renameFix == null || !renameFix.isAvailable(project, editor, file)) return
    renameFix.invoke(project, editor, file)
  }

  private val KtObjectDeclaration.namedDeclarations
    get() = declarations.filterIsInstance<KtNamedDeclaration>()

}
