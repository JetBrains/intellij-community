// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.service.project.GradleModuleDataIndexTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPath
import org.jetbrains.plugins.gradle.util.gradlePath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files

@TestApplication
class GradleBuildScriptQualifiedNameProviderTest : GradleModuleDataIndexTestCase() {

  private val provider = GradleBuildScriptQualifiedNameProvider()

  @Test
  fun `gradle project path root identity`() {
    val moduleData = ModuleData("root", GradleConstants.SYSTEM_ID, "JAVA_MODULE", "root", projectPath, projectPath)
    moduleData.gradleIdentityPath = ":"
    assertEquals(":", gradleProjectReferencePath(moduleData))
  }

  @Test
  fun `gradle project path nested identity`() {
    val moduleData = ModuleData(":foo:bar", GradleConstants.SYSTEM_ID, "JAVA_MODULE", "foo.bar", "$projectPath/foo/bar", "$projectPath/foo/bar")
    moduleData.gradleIdentityPath = ":foo:bar"
    assertEquals(":foo:bar", gradleProjectReferencePath(moduleData))
  }

  @Test
  fun `gradle project path falls back to gradle path`() {
    val moduleData = ModuleData("m", GradleConstants.SYSTEM_ID, "JAVA_MODULE", "m", projectPath, projectPath)
    moduleData.gradlePath = ":sub"
    assertEquals(":sub", gradleProjectReferencePath(moduleData))
  }

  @Test
  fun `format identity trims trailing colon only`() {
    assertEquals(":", formatGradleIdentityForReference(":"))
    assertEquals(":a:b", formatGradleIdentityForReference(":a:b"))
    assertEquals(":a:b", formatGradleIdentityForReference(":a:b:"))
  }

  @Test
  fun `fallback for custom build script file name`(): Unit = runBlocking {
    val subPath = java.nio.file.Path.of(projectPath, "cust").also { Files.createDirectories(it) }
    val buildFile = subPath.resolve("my-library.gradle").also { Files.writeString(it, "//") }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(buildFile)!!

    val subPathStr = ExternalSystemApiUtil.toCanonicalPath(subPath.toString())
    val projectData = ProjectData(GradleConstants.SYSTEM_ID, "root", projectPath, projectPath)
    val projectNode = DataNode(ProjectKeys.PROJECT, projectData, null)
    val moduleData = ModuleData(":lib", GradleConstants.SYSTEM_ID, "JAVA_MODULE", "lib", subPathStr, subPathStr)
    moduleData.gradleIdentityPath = ":lib"
    projectNode.createChild(ProjectKeys.MODULE, moduleData)

    val dataStorage = mock<ExternalProjectsDataStorage>()
    whenever(dataStorage.list(GradleConstants.SYSTEM_ID)).thenReturn(
      listOf(InternalExternalProjectInfo(GradleConstants.SYSTEM_ID, projectPath, projectNode))
    )
    project.replaceService(ExternalProjectsDataStorage::class.java, dataStorage, asDisposable())

    val vf = LocalFileSystem.getInstance().findFileByNioFile(buildFile)!!
    val psiFile = PsiManager.getInstance(project).findFile(vf)!!

    assertEquals(":lib", provider.getQualifiedName(psiFile))
  }

  @Test
  fun `copy reference uses gradle path for synced build script`(): Unit = runBlocking {
    val subPath = java.nio.file.Path.of(projectPath, "sub").also { Files.createDirectories(it) }
    val buildFile = subPath.resolve("build.gradle.kts").also { Files.writeString(it, "//") }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(buildFile)!!

    val subPathStr = ExternalSystemApiUtil.toCanonicalPath(subPath.toString())
    val projectData = ProjectData(GradleConstants.SYSTEM_ID, "root", projectPath, projectPath)
    val projectNode = DataNode(ProjectKeys.PROJECT, projectData, null)
    val moduleData = ModuleData(":sub", GradleConstants.SYSTEM_ID, "JAVA_MODULE", "sub", subPathStr, subPathStr)
    moduleData.gradleIdentityPath = ":sub"
    projectNode.createChild(ProjectKeys.MODULE, moduleData)

    val dataStorage = mock<ExternalProjectsDataStorage>()
    whenever(dataStorage.list(GradleConstants.SYSTEM_ID)).thenReturn(
      listOf(InternalExternalProjectInfo(GradleConstants.SYSTEM_ID, projectPath, projectNode))
    )
    project.replaceService(ExternalProjectsDataStorage::class.java, dataStorage, asDisposable())

    val vf = LocalFileSystem.getInstance().findFileByNioFile(buildFile)!!
    val psiFile = PsiManager.getInstance(project).findFile(vf)!!

    assertEquals(":sub", provider.getQualifiedName(psiFile))
  }

  @Test
  fun `no synced gradle module for script`(): Unit = runBlocking {
    val orphan = java.nio.file.Path.of(projectPath, "orphan").also { Files.createDirectories(it) }
    val buildFile = orphan.resolve("build.gradle.kts").also { Files.writeString(it, "//") }
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(buildFile)!!
    val psiFile = PsiManager.getInstance(project).findFile(vf)!!
    assertNull(provider.getQualifiedName(psiFile))
  }
}
