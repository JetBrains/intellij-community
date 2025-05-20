// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt(writeIntent = true)
class RegisterFileSetByConditionTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = WorkspaceFileIndex.getInstance(projectModel.project)

  private lateinit var module: Module
  private lateinit var excludedRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
    excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("root/exc")
    ModuleRootModificationUtil.addContentRoot(module, excludedRoot.parent)
    PsiTestUtil.addExcludedRoot(module, excludedRoot)
  }

  @Test
  fun `register file set by condition`(@TestDisposable testDisposable: Disposable) {
    val file = projectModel.baseProjectDir.newVirtualFile("root/a.txt")
    val fileTxt = projectModel.baseProjectDir.newVirtualFile("root/exc/root/b.txt")
    val fileJava = projectModel.baseProjectDir.newVirtualFile("root/exc/root/b.java")
    val fileExe = projectModel.baseProjectDir.newVirtualFile("root/exc/root/b.exe")
    val excludedRoot = fileTxt.parent
    assertTrue(fileIndex.isInContent(file))
    assertFalse(fileIndex.isInContent(excludedRoot))
    assertFalse(fileIndex.isInContent(fileTxt))
    assertFalse(fileIndex.isInContent(fileJava))
    assertFalse(fileIndex.isInContent(fileExe))

    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(FileByConditionContributor(), testDisposable)
    assertTrue(fileIndex.isInContent(file))
    assertFalse(fileIndex.isInContent(excludedRoot))
    assertTrue(fileIndex.isInContent(fileTxt))
    assertTrue(fileIndex.isInContent(fileJava))
    assertFalse(fileIndex.isInContent(fileExe))
  }

  private class FileByConditionContributor : WorkspaceFileIndexContributor<ExcludeUrlEntity> {
    override val entityClass: Class<ExcludeUrlEntity>
      get() = ExcludeUrlEntity::class.java

    override fun registerFileSets(entity: ExcludeUrlEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerFileSetByCondition(entity.url.append("root"), WorkspaceFileKind.CONTENT, entity, null) { file ->
        file.extension == "txt" || file.extension == "java"
      }
    }
  }
}