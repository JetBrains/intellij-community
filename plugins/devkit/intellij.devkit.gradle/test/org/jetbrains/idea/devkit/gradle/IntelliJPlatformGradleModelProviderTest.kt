// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.devkit.gradle.tooling.IntelliJPlatformGradleModel
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.replaceService
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Files

internal class IntelliJPlatformGradleModelProviderTest : LightJavaCodeInsightFixtureTestCase() {

  private lateinit var provider: IntelliJPlatformGradleModelProviderImpl

  override fun setUp() {
    super.setUp()
    provider = IntelliJPlatformGradleModelProviderImpl()
    project.replaceService(IntelliJPlatformGradleModelProvider::class.java, provider, testRootDisposable)
  }

  fun testImportsDataForModule() {
    val file = myFixture.addFileToProject("project/build.gradle.kts", "")
    val projectPath = file.virtualFile.parent.path
    val data = gradleData("2026.1")

    updateProjectData(projectPath, projectPath to data)

    assertSame(data, provider.getModel(file))
  }

  fun testUsesClosestModulePath() {
    val file = myFixture.addFileToProject("project/nested/build.gradle.kts", "")
    val nestedProjectPath = file.virtualFile.parent.path
    val projectPath = file.virtualFile.parent.parent.path
    val rootData = gradleData("2025.3")
    val nestedData = gradleData("2026.1")

    updateProjectData(projectPath, projectPath to rootData, nestedProjectPath to nestedData)

    assertSame(nestedData, provider.getModel(file))
  }

  fun testEmptyImportClearsLinkedProjectData() {
    val file = myFixture.addFileToProject("project/build.gradle.kts", "")
    val projectPath = file.virtualFile.parent.path
    updateProjectData(projectPath, projectPath to gradleData("2026.1"))

    updateProjectData(projectPath)

    assertNull(provider.getModel(file))
  }

  fun testUsesCachedExternalProjectDataBeforeCurrentSync() {
    val file = myFixture.addFileToProject("project/build.gradle.kts", "")
    val projectPath = file.virtualFile.parent.path
    val data = gradleData("2026.1")
    val projectNode = DataNode(
      ProjectKeys.PROJECT,
      ProjectData(GradleConstants.SYSTEM_ID, "project", projectPath, projectPath),
      null,
    )
    val moduleNode = projectNode.createChild(
      ProjectKeys.MODULE,
      ModuleData("project", GradleConstants.SYSTEM_ID, "", "project", projectPath, projectPath),
    )
    moduleNode.createChild(IntelliJPlatformGradleData.KEY, data)
    val externalProjectsManager = ExternalProjectsManagerImpl.getInstance(project)
    externalProjectsManager.updateExternalProjectData(
      InternalExternalProjectInfo(GradleConstants.SYSTEM_ID, projectPath, projectNode),
    )

    try {
      assertEquals(data, provider.getModel(file))
    }
    finally {
      externalProjectsManager.forgetExternalProjectData(GradleConstants.SYSTEM_ID, projectPath)
    }
  }

  fun testImportsDataProducedByModelFetch() {
    val file = myFixture.addFileToProject("project/build.gradle.kts", "")
    val projectPath = file.virtualFile.parent.path
    val releasesFile = Files.createTempFile("product-releases", ".txt")
    try {
      Files.writeString(releasesFile, "IU\t2026.1\tRELEASE\n")
      val model = object : IntelliJPlatformGradleModel {
        override fun getDependencyHelperProductCodes() = mapOf("intellijIdea" to "IU")
        override fun getProductReleasesFile() = releasesFile.toString()
        override fun getBundledPluginsFile(): String? = null
        override fun getBundledModulesFile(): String? = null
        override fun getCurrentPluginVersion() = "2.14.0"
        override fun getLatestPluginVersion() = "2.19.0"
      }

      assertEquals(1, provider.importProjectModels(projectPath, mapOf(projectPath to model)))

      assertEquals(
        gradleData("2026.1").copy(
          currentPluginVersion = "2.14.0",
          latestPluginVersion = "2.19.0",
        ),
        provider.getModel(file),
      )
    }
    finally {
      Files.deleteIfExists(releasesFile)
    }
  }

  fun testImportsDataWithoutProductReleases() {
    val file = myFixture.addFileToProject("project/build.gradle.kts", "")
    val projectPath = file.virtualFile.parent.path
    val model = object : IntelliJPlatformGradleModel {
      override fun getDependencyHelperProductCodes() = emptyMap<String, String>()
      override fun getProductReleasesFile(): String? = null
      override fun getBundledPluginsFile(): String? = null
      override fun getBundledModulesFile(): String? = null
      override fun getCurrentPluginVersion() = "2.17.0"
      override fun getLatestPluginVersion() = "2.20.0"
    }

    assertEquals(1, provider.importProjectModels(projectPath, mapOf(projectPath to model)))

    assertEquals(
      IntelliJPlatformGradleData(
        currentPluginVersion = "2.17.0",
        latestPluginVersion = "2.20.0",
      ),
      provider.getModel(file),
    )
  }

  fun testImportsBundledPluginsAndModulesData() {
    val file = myFixture.addFileToProject("project/build.gradle.kts", "")
    val projectPath = file.virtualFile.parent.path
    val pluginsFile = Files.createTempFile("bundled-plugins", ".txt")
    val modulesFile = Files.createTempFile("bundled-modules", ".txt")
    try {
      Files.writeString(pluginsFile, "com.intellij.java\tJava\n")
      Files.writeString(modulesFile, "intellij.platform.vcs.impl\tVCS Implementation\n")
      val model = object : IntelliJPlatformGradleModel {
        override fun getDependencyHelperProductCodes() = emptyMap<String, String>()
        override fun getProductReleasesFile(): String? = null
        override fun getBundledPluginsFile() = pluginsFile.toString()
        override fun getBundledModulesFile() = modulesFile.toString()
        override fun getCurrentPluginVersion() = "0.0.0"
        override fun getLatestPluginVersion() = "0.0.0"
      }

      assertEquals(1, provider.importProjectModels(projectPath, mapOf(projectPath to model)))

      assertEquals(
        IntelliJPlatformGradleData(
          bundledPlugins = listOf(IntelliJPlatformBundledArtifact("com.intellij.java", "Java")),
          bundledModules = listOf(IntelliJPlatformBundledArtifact("intellij.platform.vcs.impl", "VCS Implementation")),
        ),
        provider.getModel(file),
      )
    }
    finally {
      Files.deleteIfExists(pluginsFile)
      Files.deleteIfExists(modulesFile)
    }
  }

  private fun updateProjectData(projectPath: String, vararg moduleData: Pair<String, IntelliJPlatformGradleData>) {
    provider.replaceProjectData(projectPath, moduleData.toMap())
  }

  private fun gradleData(version: String) = IntelliJPlatformGradleData(
    dependencyHelperProductCodes = mapOf("intellijIdea" to "IU"),
    productReleases = mapOf("IU" to listOf(IntelliJPlatformProductRelease(version, "RELEASE"))),
  )
}
