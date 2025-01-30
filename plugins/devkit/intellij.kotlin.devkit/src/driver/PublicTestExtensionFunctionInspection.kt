// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.driver

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier

class PublicTestExtensionFunctionInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : KtVisitorVoid() {
      override fun visitNamedFunction(function: KtNamedFunction) {
        if (!isModuleWithDriverTests(function.containingFile)) return
        if (!isInValidScope(function)) return
        if (!isPublicExtensionFunction(function)) return

        val nameIdentifier = function.nameIdentifier ?: return
        holder.registerProblem(
          nameIdentifier,
          DevKitKotlinBundle.message("inspection.public.extension.function.in.test.display.name")
        )
      }
    }
  }

  private fun isModuleWithDriverTests(file: PsiFile): Boolean {
    return ModuleUtilCore.findModuleForFile(file)?.name == "intellij.driver.tests"
  }

  private fun isInValidScope(function: KtNamedFunction): Boolean {
    val containingClass = function.containingClassOrObject
    //don't report from class, since it has limited scope
    if (containingClass is KtClass) return false
    //don't report from private/protected companion objects
    val visibility = containingClass?.visibilityModifier()?.text
    return visibility == null || visibility == "public"
  }

  private fun isPublicExtensionFunction(function: KtNamedFunction): Boolean {
    // Check if it's an extension function
    val receiverTypeRef = function.receiverTypeReference ?: return false

    // Skip local functions (functions defined inside other functions)
    if (function.isLocal) return false

    //optimization to avoid resolve
    if (receiverTypeRef.text != "Driver" && receiverTypeRef.text != "Finder" && receiverTypeRef.text != "IdeaFrameUI") return false

    // Check if it's public (no visibility modifier means public in Kotlin)
    val visibilityModifier = function.visibilityModifier()?.text
    return visibilityModifier == null || visibilityModifier == "public"
  }
}