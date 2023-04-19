// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension

internal class LightServiceMigrationXMLInspection : DevKitPluginXmlInspectionBase() {
  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element !is Extension) return
    if (LightServiceMigrationUtil.isVersion193OrHigher(element) ||
        ApplicationManager.getApplication().isUnitTestMode) {
      val (aClass, level) = LightServiceMigrationUtil.getServiceImplementation(element) ?: return
      if (!aClass.isWritable || !LightServiceMigrationUtil.canBeLightService(aClass)) return
      if (aClass.hasAnnotation(Service::class.java.canonicalName)) {
        holder.createProblem(element, DevKitBundle.message("inspection.light.service.migration.already.annotated.message"))
      }
      else {
        val message = LightServiceMigrationUtil.getMessage(level)
        holder.createProblem(element, message)
      }
    }
  }
}