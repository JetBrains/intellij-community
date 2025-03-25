// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.VfsTestUtil
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

  @Test
  @TargetVersions("8.8+")
  fun testUpdatingDaemonJvmCriteriaWithAlreadyExistingInvalidProperties() {
    createSettingsFile(settingsScript {
      it.withFoojayPlugin()
    })
    importProject()
    VfsTestUtil.createFile(projectRoot, "gradle/gradle-daemon-jvm.properties", """
      toolchainVersion=17
      toolchainVendor=invalid
    """.trimIndent()
    )
    ExternalSystemApiUtil.executeProjectChangeAction(project) {
      ProjectRootManager.getInstance(project).projectSdk = null
    }

    val criteria = GradleDaemonJvmCriteria("17", JETBRAINS.asJvmVendor())
    GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(project, projectRoot.path, criteria)
      .get(1, TimeUnit.MINUTES)

    val properties = GradleDaemonJvmPropertiesFile.getProperties(projectRoot.toNioPath())
    assertNotNull(properties)
    assertEquals(criteria, properties!!.criteria)
  }
}