// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.AppServiceAsStaticFinalFieldQuickFixProviders
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor


class ApplicationServiceAsStaticFinalFieldInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {

    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {

        override fun visitField(node: UField): Boolean {
          if (!(node.isStatic && node.isFinal)) return true

          if (isExplicitConstructorCall(node)) return true

          val fieldTypeUClass = PsiTypesUtil.getPsiClass(node.type).toUElementOfType<UClass>() ?: return true
          val serviceLevel = getLevelType(holder.project, fieldTypeUClass)
          if (!serviceLevel.isApp()) return true

          val sourcePsi = node.sourcePsi ?: return true
          val fixes = AppServiceAsStaticFinalFieldQuickFixProviders.forLanguage(holder.file.language)?.getFixes(sourcePsi) ?: emptyList()
          holder.registerUProblem(
            node,
            DevKitBundle.message("inspections.application.service.as.static.final.field.message"),
            *fixes.toTypedArray()
          )
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
