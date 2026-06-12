// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.service.project.GradleModuleDataIndexTestCase
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPath
import org.jetbrains.plugins.gradle.util.gradlePath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class GradleBuildScriptQualifiedNameProviderTest : GradleModuleDataIndexTestCase() {

  private val provider = GradleBuildScriptQualifiedNameProvider()

  @Test
  fun `copy reference uses root identity from synced module`(): Unit = runBlocking {
    assertQualifiedName(Path.of(projectPath), "root", "root", ":") {
      it.gradleIdentityPath = ":"
    }
  }

  @Test
  fun `copy reference uses nested identity from synced module`(): Unit = runBlocking {
    assertQualifiedName(Path.of(projectPath, "foo", "bar"), ":foo:bar", "foo.bar", ":foo:bar") {
      it.gradleIdentityPath = ":foo:bar"
    }
  }

  @Test
  fun `copy reference falls back to gradle path from synced module`(): Unit = runBlocking {
    assertQualifiedName(Path.of(projectPath, "sub"), "m", "m", ":sub") {
      it.gradlePath = ":sub"
    }
  }

  @Test
  fun `copy reference trims trailing colon from synced identity`(): Unit = runBlocking {
    assertQualifiedName(Path.of(projectPath, "trimmed"), ":a:b", "trimmed", ":a:b") {
      it.gradleIdentityPath = ":a:b:"
    }
  }

  @Test
  fun `fallback for custom build script file name`(): Unit = runBlocking {
    assertQualifiedName(Path.of(projectPath, "cust"), ":lib", "lib", ":lib", "my-library.gradle") {
      it.gradleIdentityPath = ":lib"
    }
  }

  @Test
  fun `copy reference uses gradle path for synced build script`(): Unit = runBlocking {
    assertQualifiedName(Path.of(projectPath, "sub"), ":sub", "sub", ":sub") {
      it.gradleIdentityPath = ":sub"
    }
  }

  @Test
  fun `no synced gradle module for script`(): Unit = runBlocking {
    installGradleSettings()
    val orphan = Path.of(projectPath, "orphan").also { Files.createDirectories(it) }
    val buildFile = orphan.resolve("build.gradle.kts").also { Files.writeString(it, "//") }
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(buildFile)!!
    val qualifiedName = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(vf)!!
      provider.getQualifiedName(psiFile)
    }
    assertNull(qualifiedName)
  }

  private suspend fun CoroutineScope.assertQualifiedName(
    modulePath: Path,
    moduleDataId: String,
    moduleName: String,
    expectedQualifiedName: String,
    scriptName: String = "build.gradle.kts",
    configureModuleData: (ModuleData) -> Unit,
  ) {
    installGradleSettings()
    val vf = createGradleScript(modulePath, scriptName)
    val moduleData = createModuleData(modulePath, moduleDataId, moduleName).also(configureModuleData)
    registerModuleData(moduleData)
    val qualifiedName = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(vf)!!
      provider.getQualifiedName(psiFile)
    }
    assertEquals(expectedQualifiedName, qualifiedName)
  }

  private fun createGradleScript(modulePath: Path, scriptName: String) =
    modulePath.also { Files.createDirectories(it) }
      .resolve(scriptName)
      .also { Files.writeString(it, "//") }
      .let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it)!! }

  private fun createModuleData(modulePath: Path, moduleDataId: String, moduleName: String): ModuleData {
    val modulePathStr = ExternalSystemApiUtil.toCanonicalPath(modulePath.toString())
    return ModuleData(moduleDataId, GradleConstants.SYSTEM_ID, "JAVA_MODULE", moduleName, modulePathStr, modulePathStr)
  }

  private fun CoroutineScope.registerModuleData(moduleData: ModuleData) {
    ExternalSystemManager.EP_NAME.point.registerExtension(createManager(GradleConstants.SYSTEM_ID, projectPath), asDisposable())

    val projectData = ProjectData(GradleConstants.SYSTEM_ID, "root", projectPath, projectPath)
    val projectNode = DataNode(ProjectKeys.PROJECT, projectData, null)
    projectNode.createChild(ProjectKeys.MODULE, moduleData)

    val dataStorage = mock<ExternalProjectsDataStorage>()
    whenever(dataStorage.list(GradleConstants.SYSTEM_ID)).thenReturn(
      listOf(InternalExternalProjectInfo(GradleConstants.SYSTEM_ID, projectPath, projectNode))
    )
    project.replaceService(ExternalProjectsDataStorage::class.java, dataStorage, asDisposable())
  }

  private fun CoroutineScope.installGradleSettings() {
    project.replaceService(GradleSettings::class.java, GradleSettings(project), asDisposable())
  }
}
