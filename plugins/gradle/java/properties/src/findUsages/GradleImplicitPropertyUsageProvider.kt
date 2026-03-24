// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.properties.findUsages

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.util.asSafely
import com.intellij.gradle.java.properties.util.gradlePropertiesStream

class GradleImplicitPropertyUsageProvider : ImplicitPropertyUsageProvider {

  override fun isUsed(property: Property): Boolean {
    val containingFile = property.containingFile.asSafely<PropertiesFile>() ?: return false
    val propertiesFiles = gradlePropertiesStream(property)
    if (containingFile !in propertiesFiles) {
      return false
    }
    val propertyName = property.name ?: return false
    return propertyName.startsWith("org.gradle") || propertyName.startsWith("systemProp")
  }

}