// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.metaInformation

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonElementVisitor
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils
import org.jetbrains.idea.devkit.util.locateExtensionsByExtensionPoint


class UnknownIdInMetaInformationInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!isAllowed(holder)) return PsiElementVisitor.EMPTY_VISITOR
    val knownIds = collectKnownIds(holder).toSet()
    return object : JsonElementVisitor() {
      override fun visitObject(o: JsonObject) {
        val idProperty = o.findProperty("id") ?: return
        val valueElement = idProperty.value as? JsonStringLiteral
        val id = valueElement?.value ?: return

        if (id !in knownIds) {
          holder.registerProblem(
            valueElement,
            DevKitBundle.message("inspections.meta.information.unknown.inspection.id", id),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          )
        }
      }
    }
  }

  fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isAllowed(holder.file) && isMetaInformationFile(holder.file)
  }

  private fun collectKnownIds(holder: ProblemsHolder): List<String> {
    return collectKnownIds(holder, "com.intellij.localInspection") +
           collectKnownIds(holder, "com.intellij.globalInspection")
  }

  private fun collectKnownIds(holder: ProblemsHolder, epName: String): List<String> {
    val project = holder.project
    val extensionPoint = ExtensionPointIndex.findExtensionPoint(
      project,
      PluginRelatedLocatorsUtils.getCandidatesScope(project),
      epName
    ) ?: return emptyList()

    val manager = DomManager.getDomManager(project)

    return locateExtensionsByExtensionPoint(extensionPoint).mapNotNull { candidate ->
      val element = candidate.pointer.getElement() ?: return@mapNotNull null
      val domElement = manager.getDomElement(element) ?: return@mapNotNull null
      if (domElement is Extension) {
        getShortName(domElement)
      }
      else null
    }
  }

  private fun getShortName(domElement: DomElement): String? {
    val shortNameAttr = DevKitDomUtil.getAttribute(domElement, "shortName")
    if (shortNameAttr != null && shortNameAttr.value != null) return shortNameAttr.rawText
    val implementationClass = DevKitDomUtil.getAttribute(domElement, "implementationClass")?.rawText

    if (implementationClass.isNullOrEmpty()) return null
    return getShortName(StringUtil.getShortName(implementationClass))
  }
}
