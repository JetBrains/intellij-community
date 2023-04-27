// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.ExtensionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.typeUtil.supertypes

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
          val problemDeclarations = declaration.declarations.filterNot { it is KtProperty && (it.isConstVal() || it.isLoggerInstance()) }
          if (problemDeclarations.isNotEmpty()) {
            val anchor = declaration.modifierList?.getModifier(KtTokens.COMPANION_KEYWORD) ?: return
            holder.registerProblem(
              anchor,
              DevKitKotlinBundle.message("inspections.companion.object.in.extension.message")
            )
          }
        }
      }

    }
  }

  private fun isRegisteredExtension(ktLightClass: KtLightClass): Boolean {
    for (candidate in locateExtensionsByPsiClass(ktLightClass)) {
      val tag = candidate.pointer.element ?: continue
      val extension = DomUtil.findDomElement(tag, Extension::class.java, false) ?: continue
      // found an extension that is not a registered service
      if (!ExtensionUtil.hasServiceBeanFqn(extension)) return true
    }
    return false
  }

  private fun KtProperty.isLoggerInstance(): Boolean {
    val ktType = (this.resolveToDescriptorIfAny() as? PropertyDescriptor)?.type ?: return false
    return ktType.supertypes().plus(ktType).any { it.fqName.toString() == Logger::class.qualifiedName }
  }

  private fun KtProperty.isConstVal(): Boolean {
    return this.hasModifier(KtTokens.CONST_KEYWORD) && this.valOrVarKeyword.node.elementType == KtTokens.VAL_KEYWORD
  }

}
