// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
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
class WorkspaceFileIndexContributorDynamicRegistrationTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = WorkspaceFileIndex.getInstance(projectModel.project)

  private lateinit var module: Module
  private lateinit var contentRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
    contentRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    ModuleRootModificationUtil.addContentRoot(module, contentRoot)
  }

  @Test
  fun `register and unregister contributor`(@TestDisposable testDisposable: Disposable) {
    val extensionPoint = WorkspaceFileIndexImpl.EP_NAME.point
    assertTrue(extensionPoint.isDynamic)
    val file = projectModel.baseProjectDir.newVirtualFile("root/a.txt")
    val excludedFile = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_FILE_NAME")
    val contributorDisposable = Disposer.newDisposable()
    assertTrue(fileIndex.isInContent(excludedFile))
    Disposer.register(testDisposable, contributorDisposable)

    extensionPoint.registerExtension(ExcludeSpecialFileContributor(), contributorDisposable)
    assertFalse(fileIndex.isInContent(excludedFile))
    assertTrue(fileIndex.isInContent(file))

    Disposer.dispose(contributorDisposable)
    assertTrue(fileIndex.isInContent(excludedFile))
  }
  
  private class ExcludeSpecialFileContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
    override val entityClass: Class<ContentRootEntity>
      get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerExclusionPatterns(entity.url, listOf(EXCLUDED_FILE_NAME), entity)
    }
  }
  
  companion object {
    private const val EXCLUDED_FILE_NAME = "my-excluded-file.txt"
  }
}