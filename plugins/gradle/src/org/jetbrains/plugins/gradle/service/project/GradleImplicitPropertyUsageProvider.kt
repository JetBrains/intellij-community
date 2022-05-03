// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.Property

class GradleImplicitPropertyUsageProvider: ImplicitPropertyUsageProvider {
  override fun isUsed(property: Property): Boolean {
    return property.containingFile.name == "gradle.properties"
  }
}
