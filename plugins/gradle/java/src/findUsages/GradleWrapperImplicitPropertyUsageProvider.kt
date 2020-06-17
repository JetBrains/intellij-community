// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.findUsages

import com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * Provider which defines some properties as implicitly used, such that they don't get
 * flagged by the inspections as unused.
 */
class GradleWrapperImplicitPropertyUsageProvider : ImplicitPropertyUsageProvider() {
  override fun isUsed(property: Property): Boolean {
    if (GradleSettings.getInstance(property.project).linkedProjectsSettings.isEmpty()) return false;

    val file = property.containingFile.virtualFile
    return nameEqual(file, "gradle-wrapper.properties") && nameEqual(file?.parent, "wrapper")
           && nameEqual(file?.parent?.parent, "gradle");
  }

  private fun nameEqual(file: VirtualFile?, name: String): Boolean {
    if (file == null) return false;
    return Comparing.equal(file.name, name, SystemInfo.isFileSystemCaseSensitive)
  }
}