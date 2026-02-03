// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.openapi.components.Service
import org.jetbrains.idea.devkit.inspections.ServiceLevelExtractor
import org.jetbrains.idea.devkit.inspections.getLevels
import org.jetbrains.idea.devkit.inspections.toLevel
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal class ServiceLevelExtractorForKotlin : ServiceLevelExtractor {
  override fun extractLevels(attributeValue: JvmAnnotationArrayValue): Collection<Service.Level> {
    return attributeValue.values
      .asSequence()
      .flatMap { value ->
        when (value) {
          is JvmAnnotationConstantValue -> {
            val constantValue = value.constantValue as? Pair<ClassId, Name> ?: return@flatMap emptySet()
            if (constantValue.first.asFqNameString() == Service.Level::class.java.canonicalName) {
              toLevel(constantValue.second.toString())?.let { return@flatMap setOf(it) }
            }
          }
          is JvmAnnotationEnumFieldValue -> {
            return@flatMap getLevels(value)
          }
        }
        return@flatMap emptySet()
      }
      .toList()
  }

  override fun isApplicableForKotlin(): Boolean {
    return true
  }
}
