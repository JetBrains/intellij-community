// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

@TestApplication
class AutoExcludeWorkspaceFileIndexContributorTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex get() = WorkspaceFileIndex.getInstance(projectModel.project)
  private lateinit var projectRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    val module = projectModel.createModule()
    PsiTestUtil.addSourceContentToRoots(module, projectRoot)
  }

  @Test
  fun `default excluded directories are not in content after project root entity is added`() = runBlocking {
    projectModel.project.workspaceModel.update("convert module to java") {
      it.modifyModuleEntity(it.entities(ModuleEntity::class.java).first()) {
        type = JAVA_MODULE_ENTITY_TYPE_ID
      }
    }
    val worktreesDir = projectModel.baseProjectDir.newVirtualDirectory("projectRoot/.worktrees")
    val claudeWorktreesDir = projectModel.baseProjectDir.newVirtualDirectory("projectRoot/.claude/worktrees")
    val srcDir = projectModel.baseProjectDir.newVirtualDirectory("projectRoot/src")

    assertTrue(readAction { fileIndex.isInContent(worktreesDir) })
    assertTrue(readAction { fileIndex.isInContent(srcDir) })

    registerProjectRoot(projectModel.project, Path.of(projectRoot.path))

    assertFalse(readAction { fileIndex.isInContent(worktreesDir) })
    assertFalse(readAction { fileIndex.isInContent(claudeWorktreesDir) })
    assertTrue(readAction { fileIndex.isInContent(srcDir) })
  }

  @Test
  fun `default excluded directories are not in content after non-java module is added`() = runBlocking {
    val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory("moduleRoot")
    val worktreesDir = projectModel.baseProjectDir.newVirtualDirectory("moduleRoot/.worktrees")
    val claudeWorktreesDir = projectModel.baseProjectDir.newVirtualDirectory("moduleRoot/.claude/worktrees")
    val srcDir = projectModel.baseProjectDir.newVirtualDirectory("moduleRoot/src")

    assertFalse(readAction { fileIndex.isInContent(worktreesDir) })
    assertFalse(readAction { fileIndex.isInContent(srcDir) })

    val module = projectModel.createModule("nonJavaModule")
    PsiTestUtil.addSourceContentToRoots(module, moduleRoot)

    assertFalse(readAction { fileIndex.isInContent(worktreesDir) })
    assertFalse(readAction { fileIndex.isInContent(claudeWorktreesDir) })
    assertTrue(readAction { fileIndex.isInContent(srcDir) })
  }

  @Test
  @RegistryKey(key = "ide.workspace.model.relative.paths.to.exclude.automatically", value = "custom-dir")
  fun `custom registry value specifies which directories to exclude`() = runBlocking {
    val customDir = projectModel.baseProjectDir.newVirtualDirectory("projectRoot/custom-dir")
    val worktreesDir = projectModel.baseProjectDir.newVirtualDirectory("projectRoot/.worktrees")

    registerProjectRoot(projectModel.project, Path.of(projectRoot.path))

    assertFalse(readAction { fileIndex.isInContent(customDir) })
    assertTrue(readAction { fileIndex.isInContent(worktreesDir) })
  }
}
