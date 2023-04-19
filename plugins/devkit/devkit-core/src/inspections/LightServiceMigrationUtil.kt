// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.util.JvmInheritanceUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiClass
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.PluginPlatformInfo
import org.jetbrains.idea.devkit.util.PsiUtil

internal object LightServiceMigrationUtil {

  enum class Level {
    PROJECT, APPLICATION
  }

  data class ServiceInfo(val aClass: PsiClass, val level: Level)

  fun canBeLightService(jvmClass: JvmClass): Boolean {
    return jvmClass.hasModifier(JvmModifier.FINAL) &&
           !JvmInheritanceUtil.isInheritor(jvmClass, PersistentStateComponent::class.java.canonicalName)
  }

  fun getServiceImplementation(extension: Extension): ServiceInfo? {
    val level = when (extension.extensionPoint?.effectiveQualifiedName) {
      "com.intellij.projectService" -> Level.PROJECT
      "com.intellij.applicationService" -> Level.APPLICATION
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
    return ServiceInfo(aClass, level)
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  fun getMessage(level: Level): String {
    return when (level) {
      Level.APPLICATION -> DevKitBundle.message("inspection.light.service.migration.app.level.message")
      Level.PROJECT -> DevKitBundle.message("inspection.light.service.migration.project.level.message")
    }
  }

  fun isVersion193OrHigher(element: DomElement): Boolean {
    if (PsiUtil.isIdeaProject(element.module?.project)) return true
    val buildNumber = PluginPlatformInfo.forDomElement(element).sinceBuildNumber
    return buildNumber != null && buildNumber.baselineVersion >= 193
  }

  fun isVersion193OrHigher(aClass: PsiClass): Boolean {
    if (PsiUtil.isIdeaProject(aClass.project)) return true
    val module = ModuleUtilCore.findModuleForPsiElement(aClass) ?: return false
    val buildNumber = PluginPlatformInfo.forModule(module).sinceBuildNumber
    return buildNumber != null && buildNumber.baselineVersion >= 193
  }
}