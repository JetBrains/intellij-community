// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.model.project.repository.FileRepositoryData
import com.intellij.openapi.externalSystem.model.project.repository.ProjectRepositoryData
import com.intellij.openapi.externalSystem.model.project.repository.UrlRepositoryData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

class GradleProjectRepositoriesImportingTest : GradleImportingTestCase() {

  private companion object {
    const val MAVEN_REPO_NAME = "mavenRepositoryWithoutAuthentication"
    const val IVY_REPO_NAME = "ivyRepository"
    const val MAVEN_CENTRAL_REPO_NAME = "MavenRepo"
    const val FLAT_FILE_REPO_NAME = "flatFileRepository"
    const val IVY_REPO_WITHOUT_URL_NAME = "ivyRepositoryWithoutUrl"

    const val MAVEN_REPO_DECLARATION = """
      maven { 
        name = '$MAVEN_REPO_NAME'
        url = file('$MAVEN_REPO_NAME') 
      }
      """
    const val IVY_REPO_DECLARATION = """
      ivy { 
        name = '$IVY_REPO_NAME'
        url = file('$IVY_REPO_NAME') 
      } 
      """
    const val FLAT_FILE_REPO_DECLARATION = """
      flatDir {       
        name = '$FLAT_FILE_REPO_NAME'
        dirs '$FLAT_FILE_REPO_NAME'
      }
      """
    const val IVY_REPO_WITHOUT_URL_NAME_DECLARATION = """
      ivy {
        name = "$IVY_REPO_WITHOUT_URL_NAME"
        artifactPattern '[organisation]/[artifact]-[revision](-[classifier]).[ext]'
        ivyPattern '[organisation]/[module]-[revision].xml'
        content {
          includeGroup 'org.example'
        }
      }
      """
    const val MAVEN_CENTRAL_REPO_DECLARATION = "mavenCentral()"
  }

  @Test
  @TargetVersions("5.6+")
  fun testUrlProjectRepositoriesWithoutUrlAreRecognised() {
    createBuildFile {
      withJavaPlugin()
      addRepository(IVY_REPO_WITHOUT_URL_NAME_DECLARATION)
    }

    importProject()

    val repositories = ExternalSystemApiUtil.getChildren(
      ExternalSystemApiUtil.findProjectNode(myProject, GradleConstants.SYSTEM_ID, projectPath)!!,
      ProjectRepositoryData.KEY
    )
      .map { it.data }
      .associateBy { it.name }
    assertEquals(1, repositories.size)

    repositories.getUrlRepository(IVY_REPO_WITHOUT_URL_NAME).run {
      assertEquals(UrlRepositoryData.Type.IVY, type)
      assertNull(url)
    }
  }

  @Test
  fun testAllProjectRepositoriesAreRecognised() {
    createBuildFile {
      withJavaPlugin()
      addRepository(MAVEN_REPO_DECLARATION)
      addRepository(IVY_REPO_DECLARATION)
      addRepository(FLAT_FILE_REPO_DECLARATION)
      addRepository(MAVEN_CENTRAL_REPO_DECLARATION)
    }

    importProject()

    val repositories = ExternalSystemApiUtil.getChildren(
      ExternalSystemApiUtil.findProjectNode(myProject, GradleConstants.SYSTEM_ID, projectPath)!!,
      ProjectRepositoryData.KEY
    )
      .map { it.data }
      .associateBy { it.name }
    assertEquals(4, repositories.size)

    repositories.getUrlRepository(MAVEN_REPO_NAME).run {
      assertEquals(UrlRepositoryData.Type.MAVEN, type)
      assertRepositoryPath()
    }

    repositories.getUrlRepository(IVY_REPO_NAME).run {
      assertEquals(UrlRepositoryData.Type.IVY, type)
      assertRepositoryPath()
    }

    repositories.getUrlRepository(MAVEN_CENTRAL_REPO_NAME).run {
      assertEquals(UrlRepositoryData.Type.MAVEN, type)
    }

    repositories.getFileRepository(FLAT_FILE_REPO_NAME).run {
      assertEquals(1, files.size)
      assertTrue(files.contains("$projectPath/$FLAT_FILE_REPO_NAME"))
    }
  }

  private fun UrlRepositoryData.assertRepositoryPath() {
    assertEquals("file:$projectPath/$name", url)
  }

  private fun Map<String, ProjectRepositoryData>.getUrlRepository(name: String): UrlRepositoryData {
    val repository = get(name)
    assertInstanceOf(repository, UrlRepositoryData::class.java)
    return repository as UrlRepositoryData
  }

  private fun Map<String, ProjectRepositoryData>.getFileRepository(name: String): FileRepositoryData {
    val repository = get(name)
    assertInstanceOf(repository, FileRepositoryData::class.java)
    return repository as FileRepositoryData
  }
}