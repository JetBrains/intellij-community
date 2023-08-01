// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt(writeIntent = true)
class NonRecursiveWorkspaceFileSetTest {
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
  fun `non-recursive file set`(@TestDisposable testDisposable: Disposable) {
    val file = projectModel.baseProjectDir.newVirtualFile("root/a.txt")
    val file2 = projectModel.baseProjectDir.newVirtualDirectory("root/exc/$NON_RECURSIVE_DIR_NAME/b.txt")
    val nonRecursiveDir = file2.parent
    assertTrue(fileIndex.isInContent(file))
    assertFalse(fileIndex.isInContent(nonRecursiveDir))
    assertFalse(fileIndex.isInContent(file2))
    
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), testDisposable)
    assertTrue(fileIndex.isInContent(file))
    assertTrue(fileIndex.isInContent(nonRecursiveDir))
    assertFalse(fileIndex.isInContent(file2))
  }
  
  private class NonRecursiveFileSetContributor : WorkspaceFileIndexContributor<ExcludeUrlEntity> {
    override val entityClass: Class<ExcludeUrlEntity>
      get() = ExcludeUrlEntity::class.java

    override fun registerFileSets(entity: ExcludeUrlEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerNonRecursiveFileSet(entity.url.append(NON_RECURSIVE_DIR_NAME), WorkspaceFileKind.CONTENT, entity, null)
    }
  }
  
  companion object {
    private const val NON_RECURSIVE_DIR_NAME = "non-recursive-dir"
  }
}