// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ServiceUtil")

package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmAnnotation
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.uast.*

@IntellijInternalApi
enum class LevelType {
  APP,
  PROJECT,
  MODULE,
  APP_AND_PROJECT,

  /**
   * An example of a service with no specified level:
   *
   * // MyService.kt
   * @Service(value = [])
   * class MyService
   */
  NOT_SPECIFIED,
  NOT_REGISTERED;

  fun isApp(): Boolean {
    return this == APP || this == APP_AND_PROJECT
  }

  fun isProject(): Boolean {
    return this == PROJECT || this == APP_AND_PROJECT
  }
}

@IntellijInternalApi
fun getLevelType(annotation: JvmAnnotation, language: Language): LevelType {
  val levels = when (val attributeValue = annotation.findAttribute(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)?.attributeValue) {
    is JvmAnnotationArrayValue -> {
      val serviceLevelExtractor = getProvider(ServiceLevelExtractors, language) ?: return LevelType.NOT_SPECIFIED
      serviceLevelExtractor.extractLevels(attributeValue)
    }
    is JvmAnnotationEnumFieldValue -> getLevels(attributeValue)
    else -> setOf(Service.Level.APP)
  }
  return toLevelType(levels)
}

@IntellijInternalApi
fun getLevelType(project: Project, uClass: UClass): LevelType {
  val serviceAnnotation = uClass.findAnnotation(Service::class.java.canonicalName)
  if (serviceAnnotation != null) return getLevelType(serviceAnnotation)
  val javaPsi = uClass.javaPsi
  val domManager = DomManager.getDomManager(project)
  var isModuleService = false
  val levels = HashSet<Service.Level>()
  for (candidate in locateExtensionsByPsiClass(javaPsi)) {
    val tag = candidate.pointer.element ?: continue
    val element = domManager.getDomElement(tag) ?: continue
    if (element is Extension && ExtensionUtil.hasServiceBeanFqn(element)) {
      when (element.extensionPoint?.effectiveQualifiedName) {
        "com.intellij.applicationService" -> levels.add(Service.Level.APP)
        "com.intellij.projectService" -> levels.add(Service.Level.PROJECT)
        "com.intellij.moduleService" -> isModuleService = true
        else -> {}
      }
    }
  }
  if (levels.isEmpty()) {
    return if (isModuleService) LevelType.MODULE else LevelType.NOT_REGISTERED
  }
  return toLevelType(levels)
}

@IntellijInternalApi
fun getLevels(attributeValue: JvmAnnotationEnumFieldValue): Collection<Service.Level> {
  if (attributeValue.containingClassName != Service.Level::class.java.canonicalName) return emptySet()
  val fieldName = attributeValue.fieldName ?: return emptySet()
  val level = toLevel(fieldName) ?: return emptySet()
  return setOf(level)
}

private fun getLevelType(annotation: UAnnotation): LevelType {
  val levels = when (val value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
    is UCallExpression ->
      value.valueArguments
        .mapNotNull { it.tryResolve() }
        .filterIsInstance<PsiField>()
        .filter { it.containingClass?.qualifiedName == Service.Level::class.java.canonicalName }
        .mapNotNull { toLevel(it.name) }

    is UReferenceExpression ->
      value.tryResolve()
        ?.let { it as PsiField }
        ?.takeIf { it.containingClass?.qualifiedName == Service.Level::class.java.canonicalName }
        ?.name
        ?.let {
          val level = toLevel(it)
          if (level == null) emptySet() else setOf(level)
        }
      ?: emptySet()

    else -> emptySet()
  }
  return toLevelType(levels)
}

private fun toLevelType(levels: Collection<Service.Level>): LevelType {
  return when {
    levels.containsAll(setOf(Service.Level.APP, Service.Level.PROJECT)) -> LevelType.APP_AND_PROJECT
    levels.contains(Service.Level.APP) -> LevelType.APP
    levels.contains(Service.Level.PROJECT) -> LevelType.PROJECT
    else -> LevelType.NOT_SPECIFIED
  }
}

@IntellijInternalApi
fun toLevel(name: String): Service.Level? {
  return try {
    Service.Level.valueOf(name)
  }
  catch (_: IllegalArgumentException) {
    null
  }
}

internal fun isService(uClass: UClass): Boolean {
  if (!ExtensionUtil.isExtensionPointImplementationCandidate(uClass.javaPsi)) return false
  return isLightService(uClass.javaPsi) || isServiceRegisteredInXml(uClass)
}

internal fun isServiceRegisteredInXml(uClass: UClass): Boolean {
  val project = uClass.sourcePsi?.project ?: return false
  val domManager = DomManager.getDomManager(project)
  val psiClass = uClass.javaPsi
  for (candidate in locateExtensionsByPsiClass(psiClass)) {
    val tag = candidate.pointer.element ?: continue
    val element = domManager.getDomElement(tag) ?: continue
    if (element is Extension) {
      if (ExtensionUtil.hasServiceBeanFqn(element)) {
        return true
      }
    }
  }
  return false
}

internal fun isLightService(psiClass: PsiClass) : Boolean {
  return psiClass.hasAnnotation(Service::class.java.canonicalName)
}

fun getProjectLevelFQN(): String = "${Service.Level::class.java.canonicalName}.${Service.Level.PROJECT}"
