// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset

abstract class CompanionObjectInExtensionInspectionSupport {

  companion object {
    fun getInstance(): CompanionObjectInExtensionInspectionSupport = service()
  }

  abstract fun createRemoveEmptyCompanionObjectFix(companionObject: KtObjectDeclaration): LocalQuickFix
  abstract fun createMoveProhibitedDeclarationsToTopLevelFix(companionObject: KtObjectDeclaration): LocalQuickFix
  abstract fun createCreateObjectAndMoveProhibitedDeclarationsQuickFix(companionObject: KtObjectDeclaration): LocalQuickFix

  fun getProhibitedDeclarations(objectDeclaration: KtObjectDeclaration): List<KtNamedDeclaration> {
    return namedDeclarations(objectDeclaration).filterNot { it.isAllowedInsideCompanionObject() }
  }

  private val LOGGER_CLASS_ID = Logger::class.qualifiedName?.let { ClassId.topLevel(FqName(it)) }
                                ?: error("Logger class must have qualified name")

  /**
   * This property can be called from the EDT by fixes created by [createCreateObjectAndMoveProhibitedDeclarationsQuickFix],
   * so we have to resort to [allowAnalysisOnEdt].
   */
  @OptIn(KaAllowAnalysisOnEdt::class)
  private fun isLoggerInstance(property: KtProperty): Boolean {
    return allowAnalysisOnEdt {
      analyze(property) {
        val propertyReturnType = property.returnType.withNullability(KaTypeNullability.NON_NULLABLE)

        // FIXME: buildClassType(LOGGER_CLASS_ID) should also work, does not work in tests for some reason
        val loggerType = findClass(LOGGER_CLASS_ID)?.let(::buildClassType)

        if (propertyReturnType !is KaClassType || loggerType !is KaClassType) return false

        propertyReturnType.isSubtypeOf(loggerType)
      }
    }
  }

  private fun namedDeclarations(objectDeclaration: KtObjectDeclaration): List<KtNamedDeclaration> {
    return objectDeclaration.declarations.filterIsInstance<KtNamedDeclaration>()
  }

  private fun KtDeclaration.isAllowedInsideCompanionObject() = this is KtProperty && (isConstVal(this) || isLoggerInstance(this))

  private fun isConstVal(property: KtProperty): Boolean {
    return property.hasModifier(KtTokens.CONST_KEYWORD) && property.valOrVarKeyword.node.elementType == KtTokens.VAL_KEYWORD
  }

  private val DEFAULT_OBJECT_NAME = "Util"

  @OptIn(KaAllowAnalysisOnEdt::class)
  fun suggestNameForObjectInstance(companionObject: KtObjectDeclaration): String {
    val containingClass = companionObject.containingClass()!!
    allowAnalysisOnEdt {
      analyze(containingClass) {
        return KotlinNameSuggester.suggestNameByName(
          DEFAULT_OBJECT_NAME,
          KotlinNameValidatorProvider.getInstance().createNameValidator(containingClass, KotlinNameSuggestionProvider.ValidatorTarget.CLASS)
        )
      }
    }
  }

  fun invokeRenameFix(project: Project, file: PsiFile, editor: Editor?, createdObject: KtObjectDeclaration) {
    if (editor == null) return
    val nameIdentifier = createdObject.nameIdentifier ?: return
    editor.caretModel.moveToOffset(nameIdentifier.endOffset)

    val renameFix = QuickFixFactory.getInstance().createRenameFix(createdObject)
    if (renameFix == null || !renameFix.isAvailable(project, editor, file)) return
    renameFix.invoke(project, editor, file)
  }

  class RemoveEmptyCompanionObjectFix(companionObject: KtObjectDeclaration) : LocalQuickFixOnPsiElement(companionObject) {
    override fun getFamilyName(): String = DevKitKotlinBundle.message("inspections.remove.empty.companion.object.fix.text")
    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
      val objectDeclaration = startElement as KtObjectDeclaration
      if (objectDeclaration.isCompanion() && objectDeclaration.declarations.isEmpty()) {
        objectDeclaration.delete()
      }
    }
  }

  abstract class CreateObjectAndMoveProhibitedDeclarationsQuickFixBase(companionObject: KtObjectDeclaration)
    : LocalQuickFixOnPsiElement(companionObject) {
    override fun getFamilyName(): String = DevKitKotlinBundle.message("inspections.create.object.and.move.prohibited.declarations.fix.text")
    override fun getText(): String = familyName
    override fun startInWriteAction(): Boolean = false
    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
  }

  abstract class MoveProhibitedDeclarationsToTopLevelFixBase(companionObject: KtObjectDeclaration)
    : LocalQuickFixOnPsiElement(companionObject), HighPriorityAction {
    override fun getFamilyName(): String = DevKitKotlinBundle.message("inspections.move.prohibited.declarations.to.top.level.fix.text")
    override fun getText(): String = familyName
    override fun startInWriteAction(): Boolean = false
    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
  }

}
