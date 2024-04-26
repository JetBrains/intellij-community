// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.quickfix.AppServiceAsStaticFinalFieldOrPropertyFixProviders
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor


internal class ApplicationServiceAsStaticFinalFieldOrPropertyInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    // Can't easily visit all Kotlin properties via UAST (since some of them are UField,
    // some of them are UMethod), so a separate visitor is needed
    val visitorProvider = AppServiceAsStaticFinalFieldOrPropertyVisitorProviders.forLanguage(holder.file.language)
    return visitorProvider?.getVisitor(holder) ?: getUastVisitor(holder)
  }

  private fun getUastVisitor(holder: ProblemsHolder): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {

        override fun visitField(node: UField): Boolean {
          if (!(node.isStatic && node.isFinal)) return true

          if (isExplicitConstructorCall(node)) return true

          val fieldTypeUClass = PsiTypesUtil.getPsiClass(node.type).toUElementOfType<UClass>() ?: return true
          val serviceLevel = getLevelType(holder.project, fieldTypeUClass)
          if (serviceLevel == LevelType.NOT_REGISTERED || !serviceLevel.isApp()) return true

          val sourcePsi = node.sourcePsi ?: return true
          val fixProvider = AppServiceAsStaticFinalFieldOrPropertyFixProviders.forLanguage(holder.file.language)
          val fixes = fixProvider?.getFixes(sourcePsi) ?: emptyList()

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

private val EP_NAME = ExtensionPointName.create<AppServiceAsStaticFinalFieldOrPropertyVisitorProvider>(
  "DevKit.lang.appServiceAsStaticFinalFieldOrPropertyVisitorProvider"
)

internal object AppServiceAsStaticFinalFieldOrPropertyVisitorProviders :
  LanguageExtension<AppServiceAsStaticFinalFieldOrPropertyVisitorProvider>(EP_NAME.name)

@IntellijInternalApi
@ApiStatus.Internal
interface AppServiceAsStaticFinalFieldOrPropertyVisitorProvider {
  fun getVisitor(holder: ProblemsHolder): PsiElementVisitor

}
