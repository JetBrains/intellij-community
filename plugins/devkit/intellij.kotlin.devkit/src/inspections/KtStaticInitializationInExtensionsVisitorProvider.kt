// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.ExtensionUtil
import org.jetbrains.idea.devkit.inspections.StaticInitializationInExtensionsVisitorProvider
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType


internal class KtStaticInitializationInExtensionsVisitorProvider : StaticInitializationInExtensionsVisitorProvider {

  override fun getVisitor(holder: ProblemsHolder): PsiElementVisitor {
    return object : KtVisitorVoid() {
      override fun visitClassInitializer(initializer: KtClassInitializer) {
        val containingClassOrObject = initializer.containingClassOrObject
        if (containingClassOrObject !is KtObjectDeclaration || !containingClassOrObject.isCompanion()) return

        val ktLightClass = containingClassOrObject.getStrictParentOfType<KtClass>()?.toLightClass() ?: return

        if (!ExtensionUtil.isExtensionPointImplementationCandidate(ktLightClass)) return
        if (!ExtensionUtil.isInstantiatedExtension(ktLightClass) { ExtensionUtil.hasServiceBeanFqn(it) }) return

        holder.registerProblem(
          initializer.initKeyword,
          DevKitBundle.message("inspections.static.initialization.in.extensions.message"),
        )
      }
    }
  }
}