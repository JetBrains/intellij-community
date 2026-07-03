// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.processing

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter

/**
 * Returns the list of classes that are *registered* by the extension.
 * The classes that are *referenced* from the extension declaration (e.g., in `serviceInterface` or `forClass` attribute) are not included.
 */
internal fun collectClassesRegisteredInExtension(element: Extension): List<PsiClass> {
  if (!element.xmlTag.getAttributeValue("language").isNullOrEmpty()) {
    val beanClass = element.extensionPoint?.beanClass?.value
    if (beanClass != null && InheritanceUtil.isInheritor(beanClass, "com.intellij.lang.LanguageExtensionPoint")) {
      return emptyList()
    }
  }

  if (!element.xmlTag.getAttributeValue("filetype").isNullOrEmpty()) {
    val beanClass = element.extensionPoint?.beanClass?.value
    if (beanClass != null && InheritanceUtil.isInheritor(beanClass, "com.intellij.openapi.fileTypes.FileTypeExtensionPoint")) {
      return emptyList()
    }
  }

  val result = mutableListOf<PsiClass>()
  for (attributeDescription in element.genericInfo.attributeChildrenDescriptions) {
    val attributeName = attributeDescription.name
    if (attributeName == "forClass") continue

    if (attributeName == "serviceInterface") continue

    val attributeValue = attributeDescription.getDomAttributeValue(element)
    if (attributeValue == null || !DomUtil.hasXml(attributeValue)) continue

    if (attributeValue.converter is PluginPsiClassConverter) {
      val psiClass = attributeValue.value as PsiClass? ?: continue

      result.add(psiClass)
    }
  }

  for (childDescription in element.genericInfo.fixedChildrenDescriptions) {
    val domElement = childDescription.getValues(element).firstOrNull() ?: continue
    val tag = domElement.xmlTag ?: continue
    val project = tag.project
    val psiClass = JavaPsiFacade.getInstance(project).findClass(tag.value.text, GlobalSearchScope.projectScope(project))
    if (psiClass != null) {
      result.add(psiClass)
    }
  }

  return result
}
