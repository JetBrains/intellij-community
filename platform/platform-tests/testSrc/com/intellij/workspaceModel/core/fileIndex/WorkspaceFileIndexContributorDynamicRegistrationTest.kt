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

  @Test
  fun `an exclusion condition stops at a nested content root`(@TestDisposable testDisposable: Disposable) {
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("root/$EXCLUDED_DIR_NAME")
    val fileInExcludedDir = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/a.txt")
    val nestedRoot = projectModel.baseProjectDir.newVirtualDirectory("root/$EXCLUDED_DIR_NAME/nested")
    val fileInNestedRoot = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/nested/b.txt")
    ModuleRootModificationUtil.addContentRoot(projectModel.createModule("nested"), nestedRoot)

    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludeSpecialDirContributor(), testDisposable)

    assertFalse(fileIndex.isInContent(excludedDir))
    assertFalse(fileIndex.isInContent(fileInExcludedDir))
    assertTrue(fileIndex.isInContent(nestedRoot))
    assertTrue(fileIndex.isInContent(fileInNestedRoot))
  }

  @Test
  fun `an unscoped exclusion condition reaches a nested content root`(@TestDisposable testDisposable: Disposable) {
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("root/$EXCLUDED_DIR_NAME")
    val nestedRoot = projectModel.baseProjectDir.newVirtualDirectory("root/$EXCLUDED_DIR_NAME/nested")
    val fileInNestedRoot = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/nested/b.txt")
    ModuleRootModificationUtil.addContentRoot(projectModel.createModule("nested"), nestedRoot)

    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludeSpecialDirUnscopedContributor(), testDisposable)

    assertFalse(fileIndex.isInContent(excludedDir))
    assertFalse(fileIndex.isInContent(nestedRoot))
    assertFalse(fileIndex.isInContent(fileInNestedRoot))
  }

  @Test
  fun `an unscoped excluded root reaches a nested content root`(@TestDisposable testDisposable: Disposable) {
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("root/$EXCLUDED_DIR_NAME")
    val fileInExcludedDir = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/a.txt")
    val nestedRoot = projectModel.baseProjectDir.newVirtualDirectory("root/$EXCLUDED_DIR_NAME/nested")
    val fileInNestedRoot = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/nested/b.txt")
    val sibling = projectModel.baseProjectDir.newVirtualFile("root/c.txt")
    ModuleRootModificationUtil.addContentRoot(projectModel.createModule("nested"), nestedRoot)

    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludeSpecialDirRootContributor(directoryOnly = false), testDisposable)

    assertFalse(fileIndex.isInContent(excludedDir))
    assertFalse(fileIndex.isInContent(fileInExcludedDir))
    assertFalse(fileIndex.isInContent(nestedRoot))
    assertFalse(fileIndex.isInContent(fileInNestedRoot))
    assertTrue(fileIndex.isInContent(sibling))
  }

  @Test
  fun `a directory-only unscoped excluded root leaves a file of that name in content`(@TestDisposable testDisposable: Disposable) {
    val fileWithTheName = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME")

    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludeSpecialDirRootContributor(directoryOnly = true), testDisposable)

    assertTrue(fileIndex.isInContent(fileWithTheName))
  }

  @Test
  fun `an unscoped excluded root that does not exist yet excludes the directory once created`(@TestDisposable testDisposable: Disposable) {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludeSpecialDirRootContributor(directoryOnly = true), testDisposable)
    // The index builds its file sets on the first query. The root does not exist at that time.
    assertTrue(fileIndex.isInContent(contentRoot))

    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("root/$EXCLUDED_DIR_NAME")
    val fileInExcludedDir = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/a.txt")

    assertFalse(fileIndex.isInContent(excludedDir))
    assertFalse(fileIndex.isInContent(fileInExcludedDir))
  }

  @Test
  fun `an unscoped excluded root wins over a content root registered for the same directory`(@TestDisposable testDisposable: Disposable) {
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("root/$EXCLUDED_DIR_NAME")
    val fileInExcludedDir = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/a.txt")
    ModuleRootModificationUtil.addContentRoot(projectModel.createModule("same"), excludedDir)

    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludeSpecialDirRootContributor(directoryOnly = true), testDisposable)

    assertFalse(fileIndex.isInContent(excludedDir))
    assertFalse(fileIndex.isInContent(fileInExcludedDir))
  }

  private class ExcludeSpecialFileContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
    override val entityClass: Class<ContentRootEntity>
      get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerExclusionPatterns(entity.url, listOf(EXCLUDED_FILE_NAME), entity)
    }
  }
  
  /** The condition sits on the content root, as the `node_modules` condition does. */
  private class ExcludeSpecialDirContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
    override val entityClass: Class<ContentRootEntity>
      get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerExclusionCondition(entity.url, ExcludeSpecialDirCondition, entity)
    }
  }

  /** The same condition on the same root, registered through the call that a nested file set does not scope out. */
  private class ExcludeSpecialDirUnscopedContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
    override val entityClass: Class<ContentRootEntity>
      get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerUnscopedExclusionCondition(entity.url, ExcludeSpecialDirCondition, entity)
    }
  }

  /** The special directory below each content root as an excluded root by URL, which a nested file set does not scope out. */
  private class ExcludeSpecialDirRootContributor(private val directoryOnly: Boolean) : WorkspaceFileIndexContributor<ContentRootEntity> {
    override val entityClass: Class<ContentRootEntity>
      get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerUnscopedExcludedRoot(entity.url.append(EXCLUDED_DIR_NAME), directoryOnly, entity)
    }
  }

  private object ExcludeSpecialDirCondition : WorkspaceFileSetExclusionCondition {
    override fun shouldExclude(file: VirtualFile): Boolean = file.isDirectory && file.name == EXCLUDED_DIR_NAME

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = EXCLUDED_DIR_NAME.hashCode()
  }

  companion object {
    private const val EXCLUDED_FILE_NAME = "my-excluded-file.txt"
    private const val EXCLUDED_DIR_NAME = "my-excluded-dir"
  }
}