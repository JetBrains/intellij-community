// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CachedSingletonsRegistry
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.inline.InlineConstantFieldProcessor
import com.intellij.refactoring.util.InlineUtil
import com.intellij.util.containers.MultiMap
import com.siyeh.ig.psiutils.CommentTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import java.util.function.Supplier


private val EP_NAME = ExtensionPointName.create<AppServiceAsStaticFinalFieldOrPropertyFixProvider>(
  "DevKit.lang.appServiceAsStaticFinalFieldOrPropertyFixProvider"
)

internal object AppServiceAsStaticFinalFieldOrPropertyFixProviders :
  LanguageExtension<AppServiceAsStaticFinalFieldOrPropertyFixProvider>(EP_NAME.name)

@IntellijInternalApi
@ApiStatus.Internal
interface AppServiceAsStaticFinalFieldOrPropertyFixProvider {
  fun getFixes(sourcePsi: PsiElement): List<LocalQuickFix>

}



internal class JavaAppServiceAsStaticFinalFieldOrPropertyFixProvider : AppServiceAsStaticFinalFieldOrPropertyFixProvider {

  override fun getFixes(sourcePsi: PsiElement): List<LocalQuickFix> {
    return if (sourcePsi is PsiField) listOf(JavaWrapInSupplierQuickFix(sourcePsi)) else emptyList()
  }

}

@IntellijInternalApi
@ApiStatus.Internal
abstract class WrapInSupplierQuickFix<T : PsiNamedElement>(elementToWrap: T) : LocalQuickFixOnPsiElement(elementToWrap) {

  override fun getFamilyName(): String = DevKitBundle.message("inspections.wrap.application.service.in.supplier.quick.fix.message")

  override fun getText(): String = familyName

  override fun startInWriteAction() = false

  protected abstract fun findElement(startElement: PsiElement): T

  override fun generatePreview(project: Project, problemDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val element = findElement(problemDescriptor.startElement)
    createSupplierElement(project, element)
    element.delete()
    return IntentionPreviewInfo.DIFF
  }

  /**
   * Creates a new supplier element (field/property), wrapping the application service element (field/property) in a [Supplier],
   * and updates all its references by changing the initializer of the application service element to a [Supplier.get] call
   * and performing 'Inline' refactoring on it, therefore deleting the application service element.
   */
  override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    CommandProcessor.getInstance().runUndoTransparentAction {
      val element = findElement(startElement)

      if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return@runUndoTransparentAction

      ApplicationManager.getApplication().runWriteAction {
        val supplierField = createSupplierElement(project, element)
        changeElementInitializerToSupplierCall(project, element, supplierField)
      }

      inlineElement(project, element)
    }
  }

  protected abstract fun createSupplierElement(project: Project, element: T): T

  protected fun defaultSupplierElementName(element: T): String = "${element.name}Supplier"

  protected fun copyComments(sourceElement: T, targetElement: T, excludeElement: PsiElement? = null) {
    val commentTracker = CommentTracker()
    // to not grab the comments inside this element
    excludeElement?.let { commentTracker.markUnchanged(it) }
    commentTracker.grabComments(sourceElement)
    commentTracker.insertCommentsBefore(targetElement)
  }

  protected abstract fun changeElementInitializerToSupplierCall(project: Project, element: T, supplierElement: T)

  protected abstract fun inlineElement(project: Project, element: T)

}


private class JavaWrapInSupplierQuickFix(psiField: PsiField) : WrapInSupplierQuickFix<PsiField>(psiField) {

  override fun findElement(startElement: PsiElement): PsiField {
    return startElement.parentOfType<PsiField>(withSelf = true)!!
  }

  override fun createSupplierElement(project: Project, element: PsiField): PsiField {
    val supplierFieldName = suggestSupplierFieldName(project, element)

    val supplierField = PsiElementFactory.getInstance(project).createFieldFromText(
      // modifiers
      element.modifierList?.text.let { "$it " } +
      // type
      "${Supplier::class.java.canonicalName}<${element.type.canonicalText}> " +
      // name
      "$supplierFieldName = " +
      // initializer
      "${CachedSingletonsRegistry::class.java.canonicalName}.lazy(() -> ${element.initializer!!.text});",
      null
    )

    return (element.parent!!.addBefore(supplierField, element) as PsiField).also {
      copyComments(element, it, element.initializer)
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(it)
    }
  }

  private fun suggestSupplierFieldName(project: Project, psiField: PsiField): String {
    val name = defaultSupplierElementName(psiField)
    return if (psiField.containingClass!!.findFieldByName(name, true) != null) {
      val codeStyleManager = JavaCodeStyleManager.getInstance(project)
      codeStyleManager.suggestUniqueVariableName(name, psiField.containingClass, true)
    }
    else name
  }

  override fun changeElementInitializerToSupplierCall(project: Project, element: PsiField, supplierElement: PsiField) {
    val supplierGetCall = PsiElementFactory.getInstance(project).createExpressionFromText(
      "${supplierElement.containingClass!!.qualifiedName}.${supplierElement.name}.get()",
      null
    )
    element.initializer = supplierGetCall
  }

  /**
   * Runs `Inline Field` refactoring as it's done inside the [com.intellij.refactoring.inline.InlineConstantFieldHandler]
   * by running [com.intellij.refactoring.inline.InlineConstantFieldProcessor] directly to avoid showing the dialog;
   * all the necessary requirements (e.g. having an initializer or being final) are either checked before
   * or are being checked here.
   */
  override fun inlineElement(project: Project, element: PsiField) {
    val conflicts = MultiMap<PsiElement, String>()
    InlineUtil.checkChangedBeforeLastAccessConflicts(conflicts, element.initializer!!, element)
    if (!BaseRefactoringProcessor.processConflicts(project, conflicts)) return

    InlineConstantFieldProcessor(
      element,
      project,
      null,
      false,
      false,
      false,
      true
    ).run()
  }
}
