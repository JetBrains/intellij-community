// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.With
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass

object ExtensionUtil {

  @JvmStatic
  fun isExtensionPointImplementationCandidate(psiClass: PsiClass): Boolean {
    return psiClass.classKind == JvmClassKind.CLASS &&
           !PsiUtil.isInnerClass(psiClass) &&
           !PsiUtil.isLocalOrAnonymousClass(psiClass) &&
           !PsiUtil.isAbstractClass(psiClass)
  }

  /**
   * Returns `true` if the [extensionClass] is registered as a plugin extension and
   * the predicate [shouldSkip] returns `false` on this extension.
   */
  fun isInstantiatedExtension(extensionClass: PsiClass, shouldSkip: (Extension) -> Boolean): Boolean {
    for (candidate in locateExtensionsByPsiClass(extensionClass)) {
      val extension = DomUtil.findDomElement(candidate.pointer.element, Extension::class.java, false) ?: continue
      if (shouldSkip(extension)) continue
      val classNameDomValues = extension.getClassNameDomValues()
      if (classNameDomValues.any { getNormalizedClassName(it.stringValue) == extensionClass.qualifiedName }) {
        return true
      }
    }
    return false
  }

  /**
   * Returns list of [GenericDomValue]'s that register classes as extensions.
   */
  fun Extension.getClassNameDomValues(): List<GenericDomValue<*>> {
    return listOfNotNull(this.getExplicitInstantiatedClassElement()) + this.implicitClassNameDomValues()
  }

  /**
   * Returns the normalized class name with dollar signs ('$') replaced by dots ('.'),
   * or null if the input class name is null.
   */
  fun getNormalizedClassName(className: String?) = className?.replace('$', '.')

  fun hasServiceBeanFqn(extension: Extension): Boolean {
    return extension.extensionPoint?.beanClass?.stringValue == ServiceDescriptor::class.java.canonicalName
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
      listOf("listener")),

    Pair(
      listOf("com.intellij.rd.solutionExtListener"),
      listOf("listener")),

    Pair(
      listOf("com.intellij.rd.rootExtListener"),
      listOf("listener"))
  )
}
