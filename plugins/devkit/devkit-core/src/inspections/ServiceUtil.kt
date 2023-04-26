// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.jvm.JvmAnnotation
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiField
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.util.locateExtensionsByPsiClass
import org.jetbrains.uast.*

internal object ServiceUtil {

  enum class LevelType {
    APP, PROJECT, APP_AND_PROJECT, NOT_SPECIFIED;

    fun isApp(): Boolean {
      return this == APP || this == APP_AND_PROJECT
    }

    fun isProject(): Boolean {
      return this == PROJECT || this == APP_AND_PROJECT
    }
  }

  fun getLevelType(annotation: JvmAnnotation, isKotlin: Boolean): LevelType {
    val levels = when (val attributeValue = annotation.findAttribute(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)?.attributeValue) {
      is JvmAnnotationArrayValue -> getLevels(attributeValue, isKotlin)
      is JvmAnnotationEnumFieldValue -> getLevels(attributeValue)
      else -> emptySet()
    }
    return toLevelType(levels)
  }

  fun getLevelType(uClass: UClass, project: Project): LevelType {
    val serviceAnnotation = uClass.findAnnotation(Service::class.java.canonicalName)
    if (serviceAnnotation != null) return getLevelType(serviceAnnotation)
    val javaPsi = uClass.javaPsi
    val domManager = DomManager.getDomManager(project)
    val levels = HashSet<Service.Level>()
    for (candidate in locateExtensionsByPsiClass(javaPsi)) {
      val tag = candidate.pointer.element ?: continue
      val element = domManager.getDomElement(tag) ?: continue
      if (element is Extension && ExtensionUtil.hasServiceBeanFqn(element)) {
        when (element.extensionPoint?.effectiveQualifiedName) {
          "com.intellij.applicationService" -> levels.add(Service.Level.APP)
          "com.intellij.projectService" -> levels.add(Service.Level.PROJECT)
          else -> {}
        }
      }
    }
    return toLevelType(levels)
  }

  private fun getLevels(attributeValue: JvmAnnotationArrayValue, isKotlin: Boolean): Collection<Service.Level> {
    return if (isKotlin) {
      val levelAsClassIdString = Service.Level::class.java.name.replace('.', '/').replace('$', '.')
      attributeValue.values
        .filterIsInstance<JvmAnnotationConstantValue>()
        .map { it.constantValue }
        .filterIsInstance<Pair<*, *>>()
        .filter { (classId, _) -> classId.toString() == levelAsClassIdString }
        .mapNotNull { (_, name) -> toLevel(name.toString()) }
    }
    else {
      attributeValue.values
        .filterIsInstance<JvmAnnotationEnumFieldValue>()
        .flatMap { getLevels(it) }
    }
  }

  private fun getLevels(attributeValue: JvmAnnotationEnumFieldValue): Collection<Service.Level> {
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

  private fun toLevel(name: String): Service.Level? {
    return try {
      Service.Level.valueOf(name)
    }
    catch (_: IllegalArgumentException) {
      null
    }
  }
}