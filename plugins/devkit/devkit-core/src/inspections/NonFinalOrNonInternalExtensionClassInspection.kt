// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens.INTERNAL_KEYWORD
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UastVisibility
import org.jetbrains.uast.getAnchorPsi
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class NonFinalOrNonInternalExtensionClassInspection : DevKitUastInspectionBase() {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = UastHintedVisitorAdapter.create(
    holder.file.language, object : AbstractUastNonRecursiveVisitor() {
    override fun visitClass(node: UClass): Boolean {
      val elementToHighlight = node.getAnchorPsi() ?: return true
      val javaPsi = node.javaPsi
      if (javaPsi.classKind != JvmClassKind.CLASS || PsiUtil.isInnerClass(javaPsi) || PsiUtil.isLocalOrAnonymousClass(
          javaPsi) || PsiUtil.isAbstractClass(javaPsi)) {
        return true
      }
      val shouldMakePackageLocal = node.lang == JavaLanguage.INSTANCE && node.visibility != UastVisibility.PACKAGE_LOCAL
      val shouldMakeInternal = node.lang == KotlinLanguage.INSTANCE && !isInternal(javaPsi)
      if (node.isFinal && !shouldMakePackageLocal && !shouldMakeInternal) return true
      if (locateExtensionsByPsiClass(javaPsi).isEmpty()) return true
      if (!node.isFinal) {
        val actions = createModifierActions(node, modifierRequest(JvmModifier.FINAL, true))
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)
        holder.registerProblem(elementToHighlight, DevKitBundle.message("inspection.extension.class.should.be.final"), *fixes)
      }
      if (shouldMakePackageLocal) {
        val actions = createModifierActions(node, modifierRequest(JvmModifier.PACKAGE_LOCAL, true))
        val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), holder.file)
        holder.registerProblem(elementToHighlight, DevKitBundle.message("inspection.extension.class.must.be.package.private"), *fixes)
      }
      else if (shouldMakeInternal) {
        val fix = MakeClassInternalFix()
        holder.registerProblem(elementToHighlight, DevKitBundle.message("inspection.extension.class.must.be.internal"), fix)
      }
      return true
    }
  }, arrayOf(UClass::class.java))


  fun isInternal(aClass: PsiClass): Boolean {
    val lightElement = aClass as? KtLightElement<*, *> ?: return false
    val modifierListOwner = lightElement.kotlinOrigin as? KtModifierListOwner ?: return false
    return modifierListOwner.hasModifier(INTERNAL_KEYWORD)
  }

  class MakeClassInternalFix : LocalQuickFix {
    override fun getFamilyName(): String = DevKitBundle.message("inspection.extension.class.make.internal")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val modifierListOwner = descriptor.psiElement.getParentOfType<KtModifierListOwner>(true) ?: return
      modifierListOwner.addModifier(INTERNAL_KEYWORD)
    }
  }
}