// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.openapi.components.Service
import org.jetbrains.idea.devkit.inspections.ServiceLevelExtractor
import org.jetbrains.idea.devkit.inspections.toLevel
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

private class ServiceLevelExtractorForKotlin : ServiceLevelExtractor {
  override fun extractLevels(attributeValue: JvmAnnotationArrayValue): Collection<Service.Level> {
    return attributeValue.values
      .asSequence()
      .filterIsInstance<JvmAnnotationConstantValue>()
      .map { it.constantValue }
      .filterIsInstance<Pair<ClassId, Name>>()
      .filter { (classId, _) -> classId.asFqNameString() == Service.Level::class.java.canonicalName }
      .mapNotNull { (_, name) -> toLevel(name.toString()) }
      .toList()
  }
}
