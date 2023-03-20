// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.properties

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.Property

internal class JUnitImplicitPropertyUsageProvider : ImplicitPropertyUsageProvider {
  override fun isUsed(property: Property): Boolean {
    val file = property.containingFile
    if (file?.name != JUNIT_PLATFORM_PROPERTIES_CONFIG) return false

    return getJUnitPlatformProperties(file).containsKey(property.key)
  }
}