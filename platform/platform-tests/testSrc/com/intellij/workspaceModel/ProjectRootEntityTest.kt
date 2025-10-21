// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.impl.ProjectRootsSynchronizer
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.ide.ProjectRootEntity
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Test
import kotlin.io.path.createDirectories

class ProjectRootEntityTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Test
  fun `test correct root is added if it contains space symbol`() {
    runBlocking {
      val name = "new project"
      val projectFile = TemporaryDirectory.generateTemporaryPath(name)
      projectFile.createDirectories()
      val options = createTestOpenProjectOptions().copy(projectName = name, projectRootDir = projectFile)
      ProjectUtil.openOrImportAsync(projectFile, options)!!.useProjectAsync { project ->
        ProjectRootsSynchronizer().execute(project) // background startup activities are not executed in unit tests on project open
        val roots = project.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()
        assertThat(roots.map { it.root }).isEqualTo(listOf(projectFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())))
      }
    }
  }
}