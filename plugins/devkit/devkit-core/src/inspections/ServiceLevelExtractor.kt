// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.LanguageExtension
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

private val EP_NAME: ExtensionPointName<ServiceLevelExtractor> = ExtensionPointName.create("DevKit.lang.serviceLevelExtractor")

internal object ServiceLevelExtractors : LanguageExtension<ServiceLevelExtractor>(EP_NAME.name)

@ApiStatus.Internal
@IntellijInternalApi
interface ServiceLevelExtractor : JvmProvider {
  fun extractLevels(attributeValue: JvmAnnotationArrayValue): Collection<Service.Level>
}

internal class ServiceLevelExtractorForJVM : ServiceLevelExtractor {
  override fun extractLevels(attributeValue: JvmAnnotationArrayValue): Collection<Service.Level> {
    return attributeValue.values
      .filterIsInstance<JvmAnnotationEnumFieldValue>()
      .flatMap { getLevels(it) }
  }

  override fun isApplicableForKotlin(): Boolean {
    return false
  }
}
