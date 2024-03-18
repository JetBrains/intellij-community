// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("LightServiceMigrationUtil")

package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiClass
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.DevKitDomUtil
import org.jetbrains.idea.devkit.util.PluginPlatformInfo
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastVisitor

internal data class ServiceInfo(val aClass: PsiClass, val level: Service.Level)

internal fun getServiceImplementation(extension: Extension): ServiceInfo? {
  val level = when (extension.extensionPoint?.effectiveQualifiedName) {
    "com.intellij.projectService" -> Service.Level.PROJECT
    "com.intellij.applicationService" -> Service.Level.APP
    else -> return null
  }
  val extensionPoint = extension.extensionPoint
  if (extensionPoint == null || !DomUtil.hasXml(extensionPoint.beanClass)) return null
  if (ServiceDescriptor::class.java.name != extensionPoint.beanClass.stringValue) return null
  if (hasDisallowedAttributes(extension)) return null
  val serviceImplementation = DevKitDomUtil.getAttribute(extension, "serviceImplementation") ?: return null
  if (!DomUtil.hasXml(serviceImplementation)) return null
  val aClass = serviceImplementation.value as? PsiClass ?: return null
  return ServiceInfo(aClass, level)
}

private fun hasDisallowedAttributes(extension: Extension): Boolean {
  for (attributeName in disallowedAttributes) {
    val attribute = DevKitDomUtil.getAttribute(extension, attributeName)
    if (attribute != null && DomUtil.hasXml(attribute)) return true
  }
  return false
}

internal fun isVersion193OrHigher(element: DomElement): Boolean {
  if (PsiUtil.isIdeaProject(element.module?.project)) return true
  val buildNumber = PluginPlatformInfo.forDomElement(element).sinceBuildNumber
  return buildNumber != null && buildNumber.baselineVersion >= 193
}

internal fun isVersion193OrHigher(aClass: PsiClass): Boolean {
  if (PsiUtil.isIdeaProject(aClass.project)) return true
  val module = ModuleUtilCore.findModuleForPsiElement(aClass) ?: return false
  val buildNumber = PluginPlatformInfo.forModule(module).sinceBuildNumber
  return buildNumber != null && buildNumber.baselineVersion >= 193
}

internal fun containsUnitTestOrHeadlessModeCheck(aClass: UClass): Boolean {
  var result = false

  aClass.accept(object : AbstractUastVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (IS_UNIT_TEST_OR_HEADLESS_MODE.uCallMatches(node)) {
        result = true
        return false
      }
      return super.visitCallExpression(node)
    }
  })

  return result
}

private val disallowedAttributes = setOf("serviceInterface", "os", "client", "overrides", "id", "preload")

private val IS_UNIT_TEST_OR_HEADLESS_MODE = CallMatcher.anyOf(
  CallMatcher.instanceCall(Application::class.java.canonicalName, "isUnitTestMode", "isHeadlessEnvironment"),
  CallMatcher.staticCall("org.jetbrains.kotlin.idea.util.application.ApplicationUtilsKt", "isUnitTestMode", "isHeadlessEnvironment"),
)
