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
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.processExtensionDeclarations
import java.util.concurrent.atomic.AtomicBoolean

internal class UnknownIdInMetaInformationInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!isAllowed(holder)) return PsiElementVisitor.EMPTY_VISITOR
    return object : JsonElementVisitor() {
      override fun visitObject(o: JsonObject) {
        val idProperty = o.findProperty("id") ?: return
        val valueElement = idProperty.value as? JsonStringLiteral
        val id = valueElement?.value ?: return
        if (!isKnownId(id, holder)) {
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

  private fun isKnownId(id: String, holder: ProblemsHolder): Boolean {
    val className = getShortName(id) + "Inspection"
    return isKnownId(id, id, holder) || isKnownId(id, className, holder)
  }

  private fun isKnownId(id: String, textToSearch: String, holder: ProblemsHolder): Boolean {
    val project = holder.project
    val manager = DomManager.getDomManager(project)
    val found = AtomicBoolean(false)

    processExtensionDeclarations(textToSearch, project, false) { extension, tag ->
      val extensionName = extension.extensionPoint?.effectiveQualifiedName
      if (extensionName != "com.intellij.localInspection" && extensionName != "com.intellij.globalInspection") {
        return@processExtensionDeclarations true
      }
      val domElement = manager.getDomElement(tag) ?: return@processExtensionDeclarations true
      if (id == getShortNameByElement(domElement)) {
        found.set(true)
        return@processExtensionDeclarations false
      }
      return@processExtensionDeclarations true
    }

    return found.get()
  }

  private fun getShortNameByElement(domElement: DomElement): String? {
    val shortNameAttr = DevKitDomUtil.getAttribute(domElement, "shortName")
    if (shortNameAttr != null && shortNameAttr.value != null) return shortNameAttr.rawText
    val implementationClass = DevKitDomUtil.getAttribute(domElement, "implementationClass")?.rawText

    if (implementationClass.isNullOrEmpty()) return null
    return getShortName(StringUtil.getShortName(implementationClass))
  }
}
