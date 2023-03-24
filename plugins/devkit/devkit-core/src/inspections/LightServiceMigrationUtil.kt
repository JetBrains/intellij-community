// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.util.JvmInheritanceUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.psi.PsiClass
import com.intellij.util.xml.DomUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.inspections.LightServiceMigrationUtil.Companion.Level.APPLICATION
import org.jetbrains.idea.devkit.inspections.LightServiceMigrationUtil.Companion.Level.PROJECT
import org.jetbrains.idea.devkit.util.DevKitDomUtil

internal class LightServiceMigrationUtil private constructor() {

  companion object {

    enum class Level {
      PROJECT,
      APPLICATION
    }

    data class Service(val aClass: PsiClass, val level: Level)

    fun canBeLightService(jvmClass: JvmClass): Boolean {
      return jvmClass.hasModifier(JvmModifier.FINAL) &&
             !JvmInheritanceUtil.isInheritor(jvmClass, PersistentStateComponent::class.java.canonicalName)
    }

    fun getServiceImplementation(extension: Extension): Service? {
      val level = when (extension.xmlElementName) {
        "projectService" -> PROJECT
        "applicationService" -> APPLICATION
        else -> return null
      }
      val extensionPoint = extension.extensionPoint
      if (extensionPoint == null || !DomUtil.hasXml(extensionPoint.beanClass)) return null
      if (ServiceDescriptor::class.java.name != extensionPoint.beanClass.stringValue) return null
      val serviceInterface = DevKitDomUtil.getAttribute(extension, "serviceInterface")
      if (serviceInterface != null && DomUtil.hasXml(serviceInterface)) return null
      val preload = DevKitDomUtil.getAttribute(extension, "preload")
      if (preload != null && DomUtil.hasXml(preload)) return null
      val serviceImplementation = DevKitDomUtil.getAttribute(extension, "serviceImplementation") ?: return null
      if (!DomUtil.hasXml(serviceImplementation)) return null
      val aClass = serviceImplementation.value as? PsiClass ?: return null
      return Service(aClass, level)
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    fun getMessage(level: Level): String {
      return when (level) {
        APPLICATION -> DevKitBundle.message("inspection.light.service.migration.app.level.message")
        PROJECT -> DevKitBundle.message("inspection.light.service.migration.project.level.message")
      }
    }
  }
}