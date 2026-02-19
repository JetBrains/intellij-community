// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil

@Internal
@IntellijInternalApi
val REQUIRES_BLOCKING_CONTEXT_ANNOTATION: String = RequiresBlockingContext::class.java.canonicalName

@Internal
@IntellijInternalApi
fun isInspectionForBlockingContextAvailable(holder: ProblemsHolder): Boolean =
  DevKitInspectionUtil.isAllowedIncludingTestSources(holder.file) &&
  DevKitInspectionUtil.isClassAvailable(holder, REQUIRES_BLOCKING_CONTEXT_ANNOTATION)

@Internal
@IntellijInternalApi
abstract class QuickFixWithReferenceToElement<T : PsiElement>(
  element: PsiElement, referencedElement: T
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
  init {
    assert(element.containingFile == referencedElement.containingFile)
  }

  @SafeFieldForPreview
  var referencedElement: SmartPsiElementPointer<T> = referencedElement.createSmartPointer()
    private set

  override fun isAvailable(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
    return referencedElement.element != null
  }

  override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
    val clone = super.getFileModifierForPreview(target)
    if (clone !is QuickFixWithReferenceToElement<*>) return clone
    val element = referencedElement.element ?: return null

    val callingMethodInCopy = PsiTreeUtil.findSameElementInCopy(element, target)
    @Suppress("UNCHECKED_CAST")
    (clone as QuickFixWithReferenceToElement<T>).referencedElement = callingMethodInCopy.createSmartPointer()

    return clone
  }
}