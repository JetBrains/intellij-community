// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor.JETBRAINS
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.util.concurrent.TimeUnit

class GradleDaemonJvmCriteriaTest : GradleImportingTestCase() {

  @Test
  @TargetVersions("8.8+")
  fun testUpdatingDaemonJvmCriteria() {
    createSettingsFile(settingsScript {
      it.withFoojayPlugin()
    })
    importProject()

    val daemonJvmCriteria = GradleDaemonJvmCriteria("17", JETBRAINS.asJvmVendor())
    GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(project, projectRoot.path, daemonJvmCriteria)
      .get(1, TimeUnit.MINUTES)

    assertEquals(daemonJvmCriteria, GradleDaemonJvmPropertiesFile.getProperties(projectRoot.toNioPath()).criteria)
  }
}