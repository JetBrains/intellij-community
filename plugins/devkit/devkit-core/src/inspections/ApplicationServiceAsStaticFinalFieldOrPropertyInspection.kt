// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.inspections.quickfix.AppServiceAsStaticFinalFieldOrPropertyProviders
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor


class ApplicationServiceAsStaticFinalFieldOrPropertyInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {

    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {

        override fun visitField(node: UField): Boolean {
          if (!(node.isStatic && node.isFinal)) return true

          if (isExplicitConstructorCall(node)) return true

          val fieldTypeUClass = PsiTypesUtil.getPsiClass(node.type).toUElementOfType<UClass>() ?: return true
          val serviceLevel = getLevelType(holder.project, fieldTypeUClass)
          if (serviceLevel == null || !serviceLevel.isApp()) return true

          val sourcePsi = node.sourcePsi ?: return true
          val anchor = node.uastAnchor?.sourcePsi ?: return true
          val provider = AppServiceAsStaticFinalFieldOrPropertyProviders.forLanguage(holder.file.language) ?: return true

          provider.registerProblem(holder, sourcePsi, anchor)
          return true
        }

      },
      arrayOf(UField::class.java)
    )
  }

  private fun isExplicitConstructorCall(field: UField): Boolean {
    val initializer = field.uastInitializer
    return initializer is UCallExpression && initializer.hasKind(UastCallKind.CONSTRUCTOR_CALL)
  }
}
