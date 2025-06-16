// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.Disposable
import com.intellij.util.lang.JavaVersion
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import java.nio.file.Path

object GradleDaemonJvmCriteriaViewFactory {

  @JvmStatic
  fun createView(externalProjectPath: Path, gradleVersion: GradleVersion, disposable: Disposable): GradleDaemonJvmCriteriaView {
    val daemonJvmProperties = GradleDaemonJvmPropertiesFile.getProperties(externalProjectPath)
    return GradleDaemonJvmCriteriaView(
      criteria = daemonJvmProperties.criteria,
      versionsDropdownList = getSuggestedVersions(),
      vendorDropdownList = getSuggestedVendors(),
      displayAdvancedSettings = GradleDaemonJvmHelper.isDamonJvmVendorCriteriaSupported(gradleVersion),
      disposable = disposable
    )
  }

  private fun getSuggestedVersions() =
    GradleJvmSupportMatrix.getAllSupportedJavaVersionsByIdea().map(JavaVersion::feature)

  private fun getSuggestedVendors() =
    JvmVendor.KnownJvmVendor.entries.filter { it != JvmVendor.KnownJvmVendor.UNKNOWN }

}