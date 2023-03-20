// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAbstract

class CompanionObjectInExtensionInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return object : KtVisitorVoid() {

      override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        if (!declaration.isCompanion()) return
        val klass = declaration.getStrictParentOfType<KtClass>() ?: return

        if (klass.isAbstract() ||
            klass.isInterface() ||
            klass.isInner() ||
            klass.isLocal ||
            klass.isEnum()) return

        val ktLightClass = klass.toLightClass() ?: return

        if (isRegisteredExtension(ktLightClass)) {
          holder.registerProblem(
            declaration,
            DevKitKotlinBundle.message("inspections.companion.object.in.extension.message")
          )
        }
      }

    }
  }

  private fun isRegisteredExtension(ktLightClass: KtLightClass): Boolean {
    for (candidate in locateExtensionsByPsiClass(ktLightClass)) {
      val tag = candidate.pointer.element ?: continue
      val extension = DomUtil.findDomElement(tag, Extension::class.java, false) ?: continue
      // found an extension that is not a registered service
      if (!hasServiceBeanFqn(extension)) return true
    }
    return false
  }

  private fun hasServiceBeanFqn(extension: Extension): Boolean {
    return extension.extensionPoint?.beanClass?.stringValue == ServiceDescriptor::class.java.canonicalName
  }
}
