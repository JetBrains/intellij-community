// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.psi.PsiElementVisitor
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
 * Reports an extension that uses an experimental extension point or an experimental bean attribute.
 *
 * Such an extension point can change or disappear in a future IDE version.
 */
internal class UnstableExtensionsUsageInspection : DevKitPluginXmlInspectionBase() {

  @Suppress("MemberVisibilityCanBePrivate")
  var ignoreUnstableApiDeclaredInThisProject: Boolean = false

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    if (isIntelliJPlatformProject(holder.project)) return PsiElementVisitor.EMPTY_VISITOR

    return super.buildVisitor(holder, isOnTheFly, session)
  }

  override fun getOptionsPane(): OptPane {
    return pane(
      checkbox("ignoreUnstableApiDeclaredInThisProject",
               DevKitBundle.message("devkit.unstable.api.usage.ignore.declared.inside.this.project"))
    )
  }

  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is Extension) return
    if (!isAllowed(holder)) return

    val extensionPoint = element.extensionPoint ?: return
    if (extensionPoint.extensionPointStatus.kind == ExtensionPoint.Status.Kind.EXPERIMENTAL_API &&
        !isIgnored(extensionPoint, element.module)) {
      highlightExperimental(element, holder)
    }

    for (attributeDescription in element.genericInfo.attributeChildrenDescriptions) {
      val attributeValue = attributeDescription.getDomAttributeValue(element) ?: continue
      if (!DomUtil.hasXml(attributeValue)) continue

      val field = attributeDescription.getDeclaration(element.manager.project) as? PsiField ?: continue
      // the deprecated status takes precedence, the Plugin.xml validity inspection reports it
      if (!field.isDeprecated && field.hasAnnotation(ApiStatus.Experimental::class.java.canonicalName)) {
        highlightExperimental(attributeValue, holder)
      }
    }
  }

  private fun isIgnored(extensionPoint: ExtensionPoint, module: Module?): Boolean {
    if (!ignoreUnstableApiDeclaredInThisProject || module == null) return false

    val file = extensionPoint.effectiveClass?.containingFile?.virtualFile ?: return false
    return projectScope(module.project).contains(file)
  }

  private fun highlightExperimental(element: DomElement, holder: DomElementAnnotationHolder) {
    val message = DevKitBundle.message("inspections.plugin.xml.usage.of.experimental.api",
                                       ApiStatus.Experimental::class.java.canonicalName)
    holder.createProblem(element, message).highlightWholeElement()
  }
}
