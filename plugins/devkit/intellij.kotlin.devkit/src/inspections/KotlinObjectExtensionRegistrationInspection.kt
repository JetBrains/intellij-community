// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.With
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import java.util.function.Predicate

class KotlinObjectExtensionRegistrationInspection : DevKitPluginXmlInspectionBase() {

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    val extension = element as? Extension ?: return
    if (extension.isAllowed()) return
    for (classNameDomValue in extension.getClassNameDomValues()) {
      if (classNameDomValue.isKotlinObjectReference()) {
        holder.createProblem(classNameDomValue, DevKitKotlinBundle.message("inspections.plugin.extension.registers.kotlin.object"))
      }
    }
  }
}

class KotlinObjectRegisteredAsExtensionInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!DevKitInspectionUtil.isAllowed(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return object : KtVisitorVoid() {
      override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        val ktLightClass = declaration.toLightClass() ?: return
        if (isInstantiatedExtension(ktLightClass)) {
          val objectKeyword = declaration.nameIdentifier ?: declaration.getObjectKeyword() ?: return
          holder.registerProblem(objectKeyword, DevKitKotlinBundle.message("inspections.plugin.kotlin.object.registered.as.extension"))
        }
      }
    }
  }

  private fun isInstantiatedExtension(ktLightClass: KtLightClass): Boolean {
    for (candidate in locateExtensionsByPsiClass(ktLightClass)) {
      val extension = DomUtil.findDomElement(candidate.pointer.element, Extension::class.java, false) ?: continue
      if (extension.isAllowed()) continue
      val classNameDomValues = extension.getClassNameDomValues()
      if (classNameDomValues.any { getNormalizedClassName(it.stringValue) == ktLightClass.qualifiedName }) {
        return true
      }
    }
    return false
  }
}

private fun Extension.isAllowed(): Boolean {
  return allowedObjectRules.any { it.test(this) }
}

private fun Extension.getClassNameDomValues(): List<GenericDomValue<*>> {
  return listOfNotNull(this.getExplicitInstantiatedClassElement()) + this.implicitClassNameDomValues()
}

private fun Extension.getExplicitInstantiatedClassElement(): GenericDomValue<*>? {
  val (elementName, isClassDefinedInTag) = this.getInstantiatedClassElementInfo() ?: return null
  val domValue = if (isClassDefinedInTag) {
    DevKitDomUtil.getTag(this, elementName)
  }
  else {
    DevKitDomUtil.getAttribute(this, elementName)
  }
  return domValue?.takeIf { DomUtil.hasXml(it) }
}

private fun Extension.implicitClassNameDomValues(): List<GenericDomValue<*>> {
  val extensionPoint = extensionPoint ?: return emptyList()
  val classNameDomValues = mutableListOf<GenericDomValue<*>>()
  for ((beanClassName, attributeNames) in beanClassNameToInstantiatedClassAttributeNames) {
    if (InheritanceUtil.isInheritor(extensionPoint.beanClass.value, beanClassName)) {
      classNameDomValues.addAll(mapAttributeNamesToExistingDomValues(attributeNames, this))
    }
  }
  // collect specific exception attributes not defined in inherited beanClass:
  val epName = extensionPoint.effectiveQualifiedName
  for ((epNames, classAttributeNames) in implicitInstantiatedClassAttributes) {
    if (epNames.contains(epName)) {
      classNameDomValues.addAll(mapAttributeNamesToExistingDomValues(classAttributeNames, this))
      break
    }
  }
  return classNameDomValues
}

private fun mapAttributeNamesToExistingDomValues(attributeNames: List<String>, extension: Extension): List<GenericDomValue<*>> {
  return attributeNames.mapNotNull { getExistingDomValue(extension, it) }
}

private fun getExistingDomValue(parent: DomElement, attributeName: String): GenericDomValue<*>? {
  return DevKitDomUtil.getAttribute(parent, attributeName)?.takeIf { DomUtil.hasXml(it) }
}

private fun Extension.getInstantiatedClassElementInfo(): Pair<String, Boolean>? {
  val classNameElement = extensionPoint?.extensionPointClassNameElement ?: return null
  if (classNameElement is With) {
    val isTagDefined = DomUtil.hasXml(classNameElement.tag)
    val isAttributeDefined = DomUtil.hasXml(classNameElement.attribute)
    if (!isTagDefined && !isAttributeDefined) {
      return null
    }
    val elementName = (if (isTagDefined) classNameElement.tag.stringValue else classNameElement.attribute.stringValue) ?: ""
    return Pair(elementName, isTagDefined)
  }
  else {
    return Pair(Extension.IMPLEMENTATION_ATTRIBUTE, false)
  }
}

private fun GenericDomValue<*>.isKotlinObjectReference(): Boolean {
  val project = module?.project ?: return false
  val className = stringValue
  if (className.isNullOrBlank()) return false
  val normalizedClassName = getNormalizedClassName(className) ?: return false
  val kotlinAsJavaSupport = project.getService(KotlinAsJavaSupport::class.java) ?: return false
  val classOrObjectDeclarations = kotlinAsJavaSupport.findClassOrObjectDeclarations(FqName(normalizedClassName), this.resolveScope)
  return classOrObjectDeclarations.size == 1 && classOrObjectDeclarations.firstOrNull() is KtObjectDeclaration
}

private fun getNormalizedClassName(className: String?) =
  className?.replace('$', '.')

private val beanClassNameToInstantiatedClassAttributeNames = mapOf(
  "com.intellij.openapi.options.ConfigurableEP" to listOf("implementation", "provider"),
  "com.intellij.openapi.wm.ToolWindowEP" to listOf("conditionClass")
)

// EP qualified names -> instantiated class attribute names
private val implicitInstantiatedClassAttributes = listOf(
  Pair(
    listOf("com.intellij.applicationService",
           "com.intellij.projectService",
           "com.intellij.moduleService"),
    listOf("serviceImplementation",
           "testServiceImplementation",
           "headlessImplementation")),

  Pair(
    listOf("com.intellij.cacheBuilder"),
    listOf("wordsScannerClass")),

  Pair(
    listOf("com.intellij.moduleBuilder"),
    listOf("builderClass")),

  Pair(
    listOf("com.intellij.psi.referenceProvider"),
    listOf("providerClass")),

  Pair(
    listOf("com.intellij.codeInsight.linkHandler"),
    listOf("handlerClass")),

  Pair(
    listOf("com.intellij.vcs"),
    listOf("vcsClass")),

  Pair(
    listOf("com.intellij.library.toolWindow"),
    listOf("librarySearchClass")),

  Pair(
    listOf("com.intellij.changesViewContent"),
    listOf("className",
           "predicateClassName",
           "preloaderClassName",
           "displayNameSupplierClassName")),

  Pair(
    listOf("com.intellij.rd.extListener"),
    listOf("handler"))
)

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
