// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val EXTEND_WITH_FQN = "org.junit.jupiter.api.extension.ExtendWith"

private val DEBUG_ONLY_EXTENSIONS = mapOf(
  "com.intellij.ide.starter.junit5.RemoteDevRun" to "env.REMOTE_DEV_RUN",
  "com.intellij.ide.starter.extended.engine.junit5.UseInstaller" to "env.JUNIT_RUNNER_USE_INSTALLER",
)

@VisibleForTesting
@ApiStatus.Internal
class DebugOnlyTestExtensionInspection : DevKitUastInspectionBase(UClass::class.java, UMethod::class.java) {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isClassAvailable(holder, EXTEND_WITH_FQN) &&
           DevKitInspectionUtil.isAllowedIncludingTestSources(holder.file)
  }

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    return check(aClass.uAnnotations, manager, isOnTheFly)
  }

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    return check(method.uAnnotations, manager, isOnTheFly)
  }

  private fun check(annotations: List<UAnnotation>, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    var holder: ProblemsHolder? = null
    for (annotation in annotations) {
      if (annotation.qualifiedName != EXTEND_WITH_FQN) continue
      val valueExpression = annotation.findDeclaredAttributeValue("value") ?: continue
      for (classLiteral in collectClassLiterals(valueExpression)) {
        val typeFqn = classLiteral.type?.canonicalText ?: continue
        if (typeFqn !in DEBUG_ONLY_EXTENSIONS) continue
        val simpleName = typeFqn.substringAfterLast('.')
        val targetPsi = classLiteral.sourcePsi ?: continue
        val problemsHolder = holder ?: ProblemsHolder(manager, targetPsi.containingFile, isOnTheFly).also { holder = it }
        problemsHolder.registerProblem(
          targetPsi,
          DevKitBundle.message("inspections.debug.only.test.extension.message", simpleName, DEBUG_ONLY_EXTENSIONS[typeFqn]),
          ProblemHighlightType.ERROR,
          RemoveDebugOnlyExtensionFix(targetPsi, simpleName),
          IgnoreDebugOnlyExtensionFix(targetPsi),
        )
      }
    }
    return holder?.resultsArray
  }
}

private fun collectClassLiterals(expression: UExpression): List<UClassLiteralExpression> {
  val result = mutableListOf<UClassLiteralExpression>()
  expression.accept(object : AbstractUastVisitor() {
    override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
      result += node
      return false
    }
  })
  return result
}

private class RemoveDebugOnlyExtensionFix(
  classLiteralPsi: PsiElement,
  private val simpleName: String,
) : LocalQuickFix {

  private val pointer: SmartPsiElementPointer<PsiElement> = SmartPointerManager.createPointer(classLiteralPsi)

  override fun getFamilyName(): String =
    DevKitBundle.message("inspections.debug.only.test.extension.fix.family.name")

  override fun getName(): String =
    DevKitBundle.message("inspections.debug.only.test.extension.fix.name", simpleName)

  override fun startInWriteAction(): Boolean = false

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val classLiteralPsi = previewDescriptor.psiElement ?: return IntentionPreviewInfo.EMPTY
    removeFromAnnotation(classLiteralPsi)
    return IntentionPreviewInfo.DIFF
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val classLiteralPsi = pointer.element ?: return
    val containingFile = classLiteralPsi.containingFile ?: return
    if (!FileModificationService.getInstance().preparePsiElementForWrite(classLiteralPsi)) return

    WriteCommandAction.runWriteCommandAction(project, getName(), null, Runnable {
      removeFromAnnotation(classLiteralPsi)
    }, containingFile)

    OptimizeImportsProcessor(project, containingFile).run()
  }

  private fun removeFromAnnotation(classLiteralPsi: PsiElement) {
    val uClassLiteral = classLiteralPsi.toUElementOfType<UClassLiteralExpression>() ?: return
    val uAnnotation = uClassLiteral.getParentOfType<UAnnotation>(strict = true) ?: return
    val annotationPsi = uAnnotation.sourcePsi ?: return

    val valueExpression = uAnnotation.findDeclaredAttributeValue("value")
                          ?: uAnnotation.findAttributeValue("value")
    val literalCount = if (valueExpression != null) collectClassLiterals(valueExpression).size else 1

    if (literalCount <= 1) {
      annotationPsi.delete()
      return
    }

    val toDelete =
      if (classLiteralPsi.language.id == "kotlin") classLiteralPsi.parent ?: classLiteralPsi
      else classLiteralPsi
    deleteWithCommaSeparator(toDelete)
  }

  private fun deleteWithCommaSeparator(element: PsiElement) {
    val parent = element.parent ?: return element.delete()

    findCommaSeparatorEdge(element, forward = true)?.let { trailingEnd ->
      parent.deleteChildRange(element, trailingEnd)
      return
    }
    findCommaSeparatorEdge(element, forward = false)?.let { leadingStart ->
      parent.deleteChildRange(leadingStart, element)
      return
    }
    element.delete()
  }

  private fun findCommaSeparatorEdge(element: PsiElement, forward: Boolean): PsiElement? {
    val step: (PsiElement) -> PsiElement? = if (forward) PsiElement::getNextSibling else PsiElement::getPrevSibling

    var sep: PsiElement? = step(element)
    while (sep != null && (sep is PsiWhiteSpace || sep.text.isBlank())) sep = step(sep)
    if (sep?.text != ",") return null

    var edge: PsiElement = sep
    var beyond: PsiElement? = step(sep)
    while (beyond is PsiWhiteSpace) {
      edge = beyond
      beyond = step(beyond)
    }
    return edge
  }
}

private class IgnoreDebugOnlyExtensionFix(targetElement: PsiElement) : LocalQuickFix, PriorityAction {

  private val pointer: SmartPsiElementPointer<PsiElement> = SmartPointerManager.createPointer(targetElement)

  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.LOW

  override fun getFamilyName(): String =
    DevKitBundle.message(
      "inspections.debug.only.test.extension.ignore.fix.family.name",
      DevKitBundle.message("inspections.debug.only.test.extension.display.name"),
    )

  override fun startInWriteAction(): Boolean = false

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    val element = previewDescriptor.psiElement ?: return IntentionPreviewInfo.EMPTY
    val classPsi = findEnclosingClassPsi(element) ?: return IntentionPreviewInfo.EMPTY
    val containingFile = classPsi.containingFile ?: return IntentionPreviewInfo.EMPTY

    val originalText = containingFile.text
    val classOffset = classPsi.textRange.startOffset
    val newText = originalText.substring(0, classOffset) +
                  annotationText(classPsi) +
                  originalText.substring(classOffset)
    return IntentionPreviewInfo.CustomDiff(containingFile.fileType, originalText, newText)
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = pointer.element ?: return
    val containingFile = element.containingFile ?: return
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return

    WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, Runnable {
      addSuppressAnnotation(project, element)
    }, containingFile)
  }

  private fun addSuppressAnnotation(project: Project, element: PsiElement) {
    val classPsi = findEnclosingClassPsi(element) ?: return
    val containingFile = classPsi.containingFile ?: return

    val docManager = PsiDocumentManager.getInstance(project)
    val document = docManager.getDocument(containingFile) ?: return
    document.insertString(classPsi.textRange.startOffset, annotationText(classPsi))
    docManager.commitDocument(document)
  }

  private fun annotationText(classPsi: PsiElement): String =
    if (classPsi.language.id == "kotlin") "@Suppress(\"$INSPECTION_ID\")\n"
    else "@SuppressWarnings(\"$INSPECTION_ID\")\n"

  private fun findEnclosingClassPsi(element: PsiElement): PsiElement? {
    var current: PsiElement? = element
    while (current != null) {
      if (current.toUElement() is UClass) return current
      current = current.parent
    }
    return null
  }

  companion object {
    private const val INSPECTION_ID = "DebugOnlyTestExtension"
  }
}
