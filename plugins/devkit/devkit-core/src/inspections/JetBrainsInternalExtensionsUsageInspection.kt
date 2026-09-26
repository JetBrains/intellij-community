// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.psi.PsiField
import com.intellij.psi.search.GlobalSearchScope.projectScope
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint

/**
 * Reports an extension that uses a JetBrains internal extension point or a JetBrains internal bean attribute.
 *
 * The IntelliJ Platform project declares the internal API itself, so the inspection reports nothing there.
 *
 * This inspection is the plugin descriptor counterpart of [JetBrainsInternalApiUsageInspection].
 */
internal class JetBrainsInternalExtensionsUsageInspection : DevKitPluginXmlInspectionBase() {

  @Suppress("MemberVisibilityCanBePrivate")
  var ignoreApiDeclaredInThisProject: Boolean = true

  override fun getOptionsPane(): OptPane {
    return pane(
      checkbox("ignoreApiDeclaredInThisProject",
               DevKitBundle.message("devkit.internal.api.usage.ignore.declared.inside.this.project"))
    )
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is Extension) return
    if (!isAllowed(holder)) return

    val module = element.module ?: return
    if (isIntelliJPlatformProject(module.project)) return

    val extensionPoint = element.extensionPoint ?: return
    if (extensionPoint.extensionPointStatus.kind == ExtensionPoint.Status.Kind.INTERNAL_API
        && !isIgnored(extensionPoint, element.module)) {
      highlightInternal(element, holder)
    }

    for (attributeDescription in element.genericInfo.attributeChildrenDescriptions) {
      val attributeValue = attributeDescription.getDomAttributeValue(element) ?: continue
      if (!DomUtil.hasXml(attributeValue)) continue

      val field = attributeDescription.getDeclaration(element.manager.project) as? PsiField ?: continue
      if (isInternal(field)) {
        highlightInternal(attributeValue, holder)
      }
    }
  }

  /**
   * The deprecated status and the experimental status take precedence.
   * Another inspection reports the field in these two cases.
   */
  private fun isInternal(field: PsiField): Boolean {
    if (field.isDeprecated) return false
    if (field.hasAnnotation(ApiStatus.Experimental::class.java.canonicalName)) return false
    return field.hasAnnotation(ApiStatus.Internal::class.java.canonicalName)
  }

  private fun isIgnored(extensionPoint: ExtensionPoint, module: Module?): Boolean {
    if (!ignoreApiDeclaredInThisProject || module == null) return false

    val file = extensionPoint.effectiveClass?.containingFile?.virtualFile ?: return false
    return projectScope(module.project).contains(file)
  }

  private fun highlightInternal(element: DomElement, holder: DomElementAnnotationHolder) {
    val message = DevKitBundle.message("inspections.plugin.xml.usage.of.internal.api", ApiStatus.Internal::class.java.canonicalName)
    holder.createProblem(element, message).highlightWholeElement()
  }
}
