// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.inspections.ExtensionUtil
import org.jetbrains.idea.devkit.inspections.ExtensionUtil.getClassNameDomValues
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import java.util.function.Predicate

internal class KotlinObjectExtensionRegistrationInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    val extension = element as? Extension ?: return
    if (!isAllowed(holder)) return

    if (extension.isAllowed()) return
    for (classNameDomValue in extension.getClassNameDomValues()) {
      if (classNameDomValue.isKotlinObjectReference()) {
        holder.createProblem(classNameDomValue, DevKitKotlinBundle.message("inspections.plugin.extension.registers.kotlin.object"))
      }
    }
  }

  private fun GenericDomValue<*>.isKotlinObjectReference(): Boolean {
    val project = module?.project ?: return false
    val className = stringValue
    if (className.isNullOrBlank()) return false
    val normalizedClassName = ExtensionUtil.getNormalizedClassName(className) ?: return false
    val kotlinAsJavaSupport = project.getService(KotlinAsJavaSupport::class.java) ?: return false
    val classOrObjectDeclarations = kotlinAsJavaSupport.findClassOrObjectDeclarations(FqName(normalizedClassName), this.resolveScope)
    return classOrObjectDeclarations.size == 1 && classOrObjectDeclarations.firstOrNull() is KtObjectDeclaration
  }
}

@VisibleForTesting
class KotlinObjectRegisteredAsExtensionInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return object : KtVisitorVoid() {
      override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        val ktLightClass = declaration.toLightClass() ?: return
        if (ExtensionUtil.isInstantiatedExtension(ktLightClass, Extension::isAllowed)) {
          val objectKeyword = declaration.nameIdentifier ?: declaration.getObjectKeyword() ?: return
          holder.registerProblem(objectKeyword, DevKitKotlinBundle.message("inspections.plugin.kotlin.object.registered.as.extension"))
        }
      }
    }
  }
}

private fun Extension.isAllowed(): Boolean {
  return allowedObjectRules.any { it.test(this) }
         || this.extensionPoint?.effectiveQualifiedName == "com.intellij.statistics.counterUsagesCollector"
}

private val allowedObjectRules = listOf(
  ObjectAllowedWhenAttributeDefinedRule("com.intellij.fileType", "fieldName"),
  ObjectAllowedWhenAttributeDefinedRule("com.intellij.serverFileType", "fieldName")
)

private class ObjectAllowedWhenAttributeDefinedRule(private val epQualifiedName: String,
                                                    private val attributeName: String) : Predicate<Extension> {
  override fun test(extension: Extension): Boolean {
    if (extension.extensionPoint?.effectiveQualifiedName == epQualifiedName) {
      val attribute = DevKitDomUtil.getAttribute(extension, attributeName)
      return attribute != null && DomUtil.hasXml(attribute)
    }
    return false
  }
}
