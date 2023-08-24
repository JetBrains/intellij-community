// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiUtil.findModifierInList
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class StaticInitializationInExtensionsInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    // UAST doesn't work with Kotlin class initializers (they are UMethod's, not UClassInitializer's),
    // so it's easier to provide a separate visitor
    val visitorProvider = StaticInitializationInExtensionsVisitorProviders.forLanguage(holder.file.language)
    return visitorProvider?.getVisitor(holder) ?: getUastVisitor(holder)
  }

  private fun getUastVisitor(holder: ProblemsHolder): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {

        override fun visitInitializer(node: UClassInitializer): Boolean {
          if (!node.isStatic) return true

          val psiClass = node.getParentOfType<UClass>()?.javaPsi ?: return true
          if (!ExtensionUtil.isExtensionPointImplementationCandidate(psiClass)) return true
          if (!ExtensionUtil.isInstantiatedExtension(psiClass) { ExtensionUtil.hasServiceBeanFqn(it) }) return true

          // using 'static' modifier as anchor
          val modifierList = (node.javaPsi as PsiModifierListOwner).modifierList!!
          val anchor = findModifierInList(modifierList, PsiModifier.STATIC)!!

          holder.registerProblem(
            anchor,
            DevKitBundle.message("inspections.static.initialization.in.extensions.message"),
          )
          return true
        }

      },
      arrayOf(UClassInitializer::class.java)
    )
  }
}

private val EP_NAME = ExtensionPointName.create<StaticInitializationInExtensionsVisitorProvider>(
  "DevKit.lang.staticInitializationInExtensionsVisitorProvider"
)

internal object StaticInitializationInExtensionsVisitorProviders :
  LanguageExtension<StaticInitializationInExtensionsVisitorProvider>(EP_NAME.name)

@IntellijInternalApi
@ApiStatus.Internal
interface StaticInitializationInExtensionsVisitorProvider {
  fun getVisitor(holder: ProblemsHolder): PsiElementVisitor

}
