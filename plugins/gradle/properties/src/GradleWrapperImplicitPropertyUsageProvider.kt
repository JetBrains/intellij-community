// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.properties

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * Provider that defines some properties as implicitly used, such that they don't get
 * flagged by the inspections as unused.
 */
internal class GradleWrapperImplicitPropertyUsageProvider : ImplicitPropertyUsageProvider {
  override fun isUsed(property: Property): Boolean {
    if (GradleSettings.getInstance(property.project).linkedProjectsSettings.isEmpty()) return false

    val file = property.containingFile.virtualFile
    return nameEqual(file, "gradle-wrapper.properties") && nameEqual(file?.parent, "wrapper")
           && nameEqual(file?.parent?.parent, "gradle")
  }

  private fun nameEqual(file: VirtualFile?, name: String): Boolean {
    if (file == null) return false
    return Comparing.equal(file.name, name, file.isCaseSensitive)
  }
}