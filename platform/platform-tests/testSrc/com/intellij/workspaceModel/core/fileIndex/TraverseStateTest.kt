// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.impl.TraverseState
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceExcludeFileSet
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString

@TestApplication
internal class TraverseStateTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex get() = WorkspaceFileIndexEx.getInstance(projectModel.project)

  private val disposable get() = projectModel.disposableRule.disposable

  private lateinit var module: Module
  private lateinit var contentRoot: VirtualFile
  private lateinit var excludedRoot: VirtualFile

  @BeforeEach
  fun setUp() = runBlocking {
    edtWriteAction {
      module = projectModel.createModule()
      contentRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
      excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("root/exc")
      projectModel.baseProjectDir.newVirtualFile("root/src/a.txt")
      projectModel.baseProjectDir.newVirtualFile("root/exc/b.txt")
      ModuleRootModificationUtil.addContentRoot(module, contentRoot)
      PsiTestUtil.addExcludedRoot(module, excludedRoot)
    }
  }

  @Test
  fun `state of cached files matches isIndexable`() = runBlocking {
    edtWriteAction {
      projectModel.baseProjectDir.newVirtualFile("root/src/b.tmp")
      projectModel.baseProjectDir.newVirtualFile("root/out.tmp/c.txt")
      projectModel.baseProjectDir.newVirtualFile("root/src/nested/d.tmp")
      projectModel.baseProjectDir.newVirtualFile("root/exc/inner/e.txt")
      projectModel.baseProjectDir.newVirtualFile("root/exc/inner/f.tmp")
      ModuleRootModificationUtil.updateModel(module) { it.contentEntries.single().addExcludePattern("*.tmp") }
      val nestedModule = projectModel.createModule("nested")
      ModuleRootModificationUtil.addContentRoot(nestedModule, contentRoot.findFileByRelativePath("src/nested")!!)
      ModuleRootModificationUtil.addContentRoot(nestedModule, contentRoot.findFileByRelativePath("exc/inner")!!)
    }
    readAction {
      val rootState = TraverseState.getTraverseState(contentRoot, projectModel.project)!!
      assertTrue(rootState.isIndexable)
      assertTrue(rootState.isIndexableRecursive)
      assertStatesMatchIndex(contentRoot, rootState)
    }
  }

  private fun assertStatesMatchIndex(file: VirtualFile, state: TraverseState) {
    assertEquals(fileIndex.isIndexable(file), state.isIndexable, "state of $file")
    val computedState = TraverseState.getTraverseState(file, projectModel.project)
    if (fileIndex.isIndexable(file)) {
      assertNotNull(computedState, "computed state of $file")
      assertEquals(fileIndex.isIndexable(file), computedState!!.isIndexable, "computed state of $file")
    } else {
      // Non-indexable files may return a state (scoped exclusion) or null (unscoped exclusion).
      if (computedState != null) {
        assertEquals(fileIndex.isIndexable(file), computedState.isIndexable, "computed state of $file")
      }
    }
    for (child in file.children) {
      val childState = state.goDownByUrl(child)
      if (childState != null) assertStatesMatchIndex(child, childState)
    }
  }

  private fun assertStateEquals(expected: TraverseState?, actual: TraverseState?) {
    assertEquals(expected?.mask, actual?.mask)
    assertEquals(expected?.maskRecursive, actual?.maskRecursive)
    assertEquals(expected?.excludes, actual?.excludes)
  }

  private fun assertStatesMatchUrl(file: VirtualFile, state: TraverseState) {
    val byUrl = stateOf(file.url)
    if (byUrl != null) {
      assertEquals(byUrl.mask, state.mask, "mask of $file")
      assertEquals(byUrl.maskRecursive, state.maskRecursive, "recursive mask of $file")
      assertEquals(byUrl.excludes, state.excludes, "exclusions of $file")
    }
    for (child in file.children) {
      val childState = state.goDownByUrl(child)
      if (childState != null) assertStatesMatchUrl(child, childState)
    }
  }

  @Test
  fun `goDown preserves state when nothing changes`() = runBlocking {
    readAction {
      val srcState = TraverseState.getTraverseState(contentRoot.findChild("src")!!, projectModel.project)!!
      assertStateEquals(srcState, srcState.goDownByUrl(contentRoot.findFileByRelativePath("src/a.txt")!!))
    }
  }

  @Test
  fun `file sets at a cached root`() = runBlocking {
    readAction {
      val atContentRoot = fileIndex.getFileSetsAt(virtualFileUrl(contentRoot.url))
      assertTrue(atContentRoot.mask != 0)
      assertEquals(atContentRoot.mask, atContentRoot.maskRecursive)
      assertTrue(atContentRoot.excludes.isEmpty())

      val atExcludedRoot = fileIndex.getFileSetsAt(virtualFileUrl(excludedRoot.url))
      assertEquals(0, atExcludedRoot.mask)
      assertTrue(atExcludedRoot.excludes.single() is WorkspaceExcludeFileSet.ByFileKind)
    }
  }

  @Test
  fun `transient files below content and excluded roots`() = runBlocking {
    createOnDisk("root/newDir/sub/c.txt")
    createOnDisk("root/exc/newDir/d.txt")
    readAction {
      assertTransient("root/newDir/sub/c.txt")
      assertTrue(stateOf(urlOf("root/newDir/sub/c.txt"))!!.isIndexable)
      assertTrue(stateOf(urlOf("root/newDir"))!!.isIndexable)
      val excState = stateOf(urlOf("root/exc/newDir/d.txt"))
      if (excState != null) assertFalse(excState.isIndexable)
    }
  }

  @Test
  fun `cache avoiding files are rejected`() = runBlocking<Unit> {
    createOnDisk("root/newDir/a.txt")
    readAction {
      assertTransient("root/newDir")
      val file = VirtualFileManager.getInstance().findFileByUrlWithoutCaching(urlOf("root/newDir"))!!
      assertThrows<IllegalArgumentException> { TraverseState.getTraverseState(file, projectModel.project) }
      assertTransient("root/newDir")
      val cachedFile = VirtualFileManager.getInstance().findFileByUrlWithoutCaching(contentRoot.url)!!
      assertTrue(cachedFile is CacheAvoidingVirtualFile && cachedFile.isCached)
      assertThrows<IllegalArgumentException> { TraverseState.getTraverseState(cachedFile, projectModel.project) }
    }
  }

  @Test
  fun `transient content root below excluded root`() = runBlocking {
    val nestedUrl = urlOf("root/exc/nested")
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(projectModel.createModule("nested")) { it.addContentEntry(nestedUrl) }
    }
    createOnDisk("root/exc/nested/deep/e.txt")
    readAction {
      assertTransient("root/exc/nested")
      assertTrue(fileIndex.getFileSetsAt(virtualFileUrl(nestedUrl)).mask != 0)
      val excState2 = stateOf(urlOf("root/exc"))
      if (excState2 != null) assertFalse(excState2.isIndexable)
      assertTrue(stateOf(nestedUrl)!!.isIndexable)
      assertTrue(stateOf(urlOf("root/exc/nested/deep/e.txt"))!!.isIndexable)
    }
  }

  @Test
  fun `transient excluded root below content root`() = runBlocking {
    val excludedUrl = urlOf("root/newExc")
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(module) { it.contentEntries.single().addExcludeFolder(excludedUrl) }
    }
    createOnDisk("root/newExc/f.txt")
    readAction {
      assertTransient("root/newExc")
      assertTrue(fileIndex.getFileSetsAt(virtualFileUrl(excludedUrl)).excludes.single() is WorkspaceExcludeFileSet.ByFileKind)
      val excludedState2 = stateOf(excludedUrl)
      if (excludedState2 != null) assertFalse(excludedState2.isIndexable)
      val excFState = stateOf(urlOf("root/newExc/f.txt"))
      if (excFState != null) assertFalse(excFState.isIndexable)
    }
  }

  @Test
  fun `exclusion patterns apply to transient files`() = runBlocking {
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(module) { it.contentEntries.single().addExcludePattern("*.tmp") }
    }
    createOnDisk("root/newDir/g.txt")
    createOnDisk("root/newDir/h.tmp")
    createOnDisk("root/newDir/out.tmp/i.txt")
    readAction {
      assertTrue(stateOf(urlOf("root/newDir/g.txt"))!!.isIndexable)
      val hState = stateOf(urlOf("root/newDir/h.tmp"))
      if (hState != null) assertFalse(hState.isIndexable)
      val iState = stateOf(urlOf("root/newDir/out.tmp/i.txt"))
      if (iState != null) assertFalse(iState.isIndexable)
    }
  }

  @Test
  fun `nested content root restores files excluded by pattern`() = runBlocking {
    val nestedUrl = urlOf("root/out.tmp/nested")
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(module) { it.contentEntries.single().addExcludePattern("*.tmp") }
      ModuleRootModificationUtil.updateModel(projectModel.createModule("nested")) { it.addContentEntry(nestedUrl) }
    }
    createOnDisk("root/out.tmp/nested/j.tmp/k.txt")
    readAction {
      val outState = stateOf(urlOf("root/out.tmp"))
      if (outState != null) assertFalse(outState.isIndexable)
      assertTrue(stateOf(nestedUrl)!!.isIndexable)
      // The pattern of the outer content root doesn't apply inside the nested content root.
      assertTrue(stateOf(urlOf("root/out.tmp/nested/j.tmp/k.txt"))!!.isIndexable)
    }
  }

  @Test
  fun `directory-only excluded root allows nested content roots`() = runBlocking {
    edtWriteAction {
      projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/nested/a.txt")
      val singleFile = projectModel.baseProjectDir.newVirtualFile("root/$EXCLUDED_DIR_NAME/single.txt")
      // A directory-only exclusion doesn't apply to a regular file with the same name.
      projectModel.baseProjectDir.newVirtualFile("root/src/$EXCLUDED_DIR_NAME")
      val nestedModule = projectModel.createModule("nested")
      ModuleRootModificationUtil.addContentRoot(nestedModule, contentRoot.findFileByRelativePath("$EXCLUDED_DIR_NAME/nested")!!)
      ModuleRootModificationUtil.addContentRoot(nestedModule, singleFile)
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(DirectoryOnlyExcludedRootContributor(), disposable)
    }
    readAction {
      assertTrue(stateOf(urlOf("root/$EXCLUDED_DIR_NAME/nested/a.txt"))!!.isIndexable)
      assertTrue(stateOf(urlOf("root/$EXCLUDED_DIR_NAME/single.txt"))!!.isIndexable)
      assertTrue(stateOf(urlOf("root/src/$EXCLUDED_DIR_NAME"))!!.isIndexable)
      val rootState = TraverseState.getTraverseState(contentRoot, projectModel.project)!!
      assertStatesMatchIndex(contentRoot, rootState)
    }
  }

  @Test
  fun `nested root resets only its kinds`() = runBlocking {
    edtWriteAction {
      projectModel.baseProjectDir.newVirtualFile("root/src/nested/a.tmp")
      projectModel.baseProjectDir.newVirtualFile("root/src/nested/sub/b.txt")
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(TestContributor(contentRoot.url) { entity, registrar ->
        registrar.registerFileSet(entity.url, WorkspaceFileKind.EXTERNAL, entity, null)
        registrar.registerExclusionPatterns(entity.url, listOf("*.tmp"), entity)
        registrar.registerFileSet(entity.url.append("src/nested"), WorkspaceFileKind.CONTENT, entity, null)
      }, disposable)
    }
    readAction {
      fun fileInfoKinds(
        file: VirtualFile,
        includeContentSets: Boolean = true,
        includeExternalSets: Boolean = true,
      ): Set<WorkspaceFileKind> {
        return fileIndex.getFileInfo(
          file,
          honorExclusion = true,
          includeContentSets = includeContentSets,
          includeContentNonIndexableSets = false,
          includeExternalSets = includeExternalSets,
          includeExternalSourceSets = false,
          includeExternalNonIndexableSets = false,
          includeCustomKindSets = false,
        ).fileSets.map { it.kind }.toSet()
      }

      val rootState = TraverseState.getTraverseState(contentRoot, projectModel.project)!!
      val nested = contentRoot.findFileByRelativePath("src/nested")!!
      val nestedState = rootState.goDownByUrl(nested.parent)!!.goDownByUrl(nested)!!
      val contentKinds = fileIndex.getFileSetsAt(virtualFileUrl(nested.url)).mask
      val allKinds = setOf(WorkspaceFileKind.CONTENT, WorkspaceFileKind.EXTERNAL)
      assertEquals(allKinds, fileInfoKinds(contentRoot))
      val nestedFile = nested.findFileByRelativePath("sub/b.txt")!!
      val matchingFile = nested.findChild("a.tmp")!!
      val contentFileKinds = setOf(WorkspaceFileKind.CONTENT)
      assertEquals(contentFileKinds, fileInfoKinds(nested))
      assertEquals(contentFileKinds, fileInfoKinds(nestedFile))
      assertEquals(contentFileKinds, fileInfoKinds(matchingFile))
      assertEquals(setOf(WorkspaceFileKind.EXTERNAL), fileInfoKinds(nested, includeContentSets = false))
      assertEquals(setOf(WorkspaceFileKind.EXTERNAL), fileInfoKinds(nestedFile, includeContentSets = false))
      assertEquals(emptySet<WorkspaceFileKind>(), fileInfoKinds(matchingFile, includeContentSets = false))
      assertEquals(contentFileKinds, fileInfoKinds(matchingFile, includeExternalSets = false))
      assertTrue(rootState.mask != contentKinds)
      assertEquals(rootState.mask, nestedState!!.mask)
      assertEquals(rootState.mask, nestedState.maskRecursive)
      assertSame(rootState.excludes.single(), nestedState.excludes.single())
      assertStateEquals(nestedState, nestedState.goDownByUrl(nested.findChild("sub")!!))
      val excludedState = nestedState.goDownByUrl(nested.findChild("a.tmp")!!)!!
      assertEquals(contentKinds, excludedState.mask)
      assertEquals(contentKinds, excludedState.maskRecursive)
      assertTrue(excludedState.excludes.isEmpty())
      assertStatesMatchIndex(contentRoot, rootState)
    }
  }

  @Test
  fun `non-recursive root resets only the current file`() = runBlocking {
    edtWriteAction {
      projectModel.baseProjectDir.newVirtualFile("root/src/nonRecursive.tmp/a.txt")
      projectModel.baseProjectDir.newVirtualFile("root/src/nonRecursive/b.tmp")
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(TestContributor(contentRoot.url) { entity, registrar ->
        registrar.registerExclusionPatterns(entity.url, listOf("*.tmp"), entity)
        registrar.registerNonRecursiveFileSet(entity.url.append("src/nonRecursive.tmp"), WorkspaceFileKind.CONTENT, entity, null)
        registrar.registerNonRecursiveFileSet(entity.url.append("src/nonRecursive"), WorkspaceFileKind.CONTENT, entity, null)
      }, disposable)
    }
    readAction {
      val srcState = stateOf(urlOf("root/src"))!!
      val matchingRoot = contentRoot.findFileByRelativePath("src/nonRecursive.tmp")!!
      val matchingState = srcState.goDownByUrl(matchingRoot)!!
      assertEquals(srcState.mask, matchingState.mask)
      assertEquals(0, matchingState.maskRecursive)
      assertTrue(matchingState.excludes.isEmpty())
      assertFalse(matchingState.goDownByUrl(matchingRoot.findChild("a.txt")!!)!!.isIndexable)
      val otherRoot = contentRoot.findFileByRelativePath("src/nonRecursive")!!
      val otherState = srcState.goDownByUrl(otherRoot)!!
      assertEquals(srcState.mask, otherState.maskRecursive)
      assertSame(srcState.excludes.single(), otherState.excludes.single())
      assertFalse(otherState.goDownByUrl(otherRoot.findChild("b.tmp")!!)!!.isIndexable)
      assertStatesMatchIndex(contentRoot, TraverseState.getTraverseState(contentRoot, projectModel.project)!!)
    }
  }

  @Test
  fun `ignored file keeps its registered file sets`() = runBlocking {
    edtWriteAction {
      projectModel.baseProjectDir.newVirtualFile("root/src/.git/a.txt")
      projectModel.baseProjectDir.newVirtualFile("root/other/.git/b.txt")
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(TestContributor(contentRoot.url) { entity, registrar ->
        registrar.registerFileSet(entity.url, WorkspaceFileKind.EXTERNAL, entity, null)
        registrar.registerFileSet(entity.url.append("src/.git"), WorkspaceFileKind.CONTENT, entity, null)
      }, disposable)
    }
    readAction {
      val ignoredRoot = contentRoot.findFileByRelativePath("src/.git")!!
      assertTrue(FileTypeRegistry.getInstance().isFileIgnored(ignoredRoot))
      val srcState = stateOf(urlOf("root/src"))!!
      val ignoredState = srcState.goDownByUrl(ignoredRoot)!!
      val contentKinds = fileIndex.getFileSetsAt(virtualFileUrl(ignoredRoot.url)).mask
      assertTrue(srcState.mask != contentKinds)
      assertEquals(contentKinds, ignoredState.mask)
      assertEquals(contentKinds, ignoredState.maskRecursive)
      assertTrue(ignoredState.goDownByUrl(ignoredRoot.findChild("a.txt")!!)!!.isIndexable)
      val otherGitState = stateOf(urlOf("root/other/.git"))
      if (otherGitState != null) assertFalse(otherGitState.isIndexable)
      val otherGitBState = stateOf(urlOf("root/other/.git/b.txt"))
      if (otherGitBState != null) assertFalse(otherGitBState.isIndexable)
      assertStatesMatchIndex(contentRoot, TraverseState.getTraverseState(contentRoot, projectModel.project)!!)
    }
  }

  @Test
  fun `unscoped condition excludes descendants of a matched parent`() = runBlocking {
    edtWriteAction {
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(TestContributor(contentRoot.url) { entity, registrar ->
        registrar.registerUnscopedExclusionCondition(entity.url, NameExclusionCondition("blocked"), entity)
        registrar.registerFileSet(entity.url.append("src/blocked/nested"), WorkspaceFileKind.CONTENT, entity, null)
      }, disposable)
    }
    createOnDisk("root/src/blocked/nested/a.txt")
    readAction {
      assertTransient("root/src/blocked")
      assertTransient("root/src/blocked/nested")
      val srcState = stateOf(urlOf("root/src"))!!
      val blocked = VirtualFileManager.getInstance().findFileByUrlWithoutCaching(urlOf("root/src/blocked"))!!
      val blockedState = srcState.goDownByUrl(blocked)
      assertNull(blockedState)
      assertStatesMatchUrl(blocked, srcState)
    }
  }

  @Test
  fun `unscoped early return drops local rules for descendants`() = runBlocking {
    edtWriteAction {
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(TestContributor(contentRoot.url) { entity, registrar ->
        registrar.registerUnscopedExclusionCondition(entity.url, NameExclusionCondition("blocked"), entity)
        registrar.registerUnscopedExclusionCondition(entity.url.append("src/blocked"), NameExclusionCondition("deny.txt"), entity)
        registrar.registerFileSet(entity.url.append("src/blocked/nested"), WorkspaceFileKind.CONTENT, entity, null)
      }, disposable)
    }
    createOnDisk("root/src/blocked/nested/deny.txt")
    createOnDisk("root/src/blocked/nested/a.txt")
    readAction {
      assertTransient("root/src/blocked")
      assertTransient("root/src/blocked/nested")
      val srcState = stateOf(urlOf("root/src"))!!
      val blocked = VirtualFileManager.getInstance().findFileByUrlWithoutCaching(urlOf("root/src/blocked"))!!
      val blockedState = srcState.goDownByUrl(blocked)
      assertNull(blockedState)
      assertStatesMatchUrl(blocked, srcState)
    }
  }

  private data class NameExclusionCondition(private val name: String) : WorkspaceFileSetExclusionCondition {
    override fun shouldExclude(file: VirtualFile): Boolean = file.name == name
  }

  private class TestContributor(
    private val rootUrl: String,
    private val register: (ContentRootEntity, WorkspaceFileSetRegistrar) -> Unit,
  ) : WorkspaceFileIndexContributor<ContentRootEntity> {
    override val entityClass: Class<ContentRootEntity>
      get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      if (entity.url.url == rootUrl) register(entity, registrar)
    }
  }

  /** Registers the directory-only excluded roots `<content root>/my-dir` and `<content root>/src/my-dir`. */
  private class DirectoryOnlyExcludedRootContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
    override val entityClass: Class<ContentRootEntity>
      get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerExcludedRoot(entity.url.append(EXCLUDED_DIR_NAME), directoryOnly = true, entity)
      registrar.registerExcludedRoot(entity.url.append("src/$EXCLUDED_DIR_NAME"), directoryOnly = true, entity)
    }
  }

  /** Computes the state of the cached ancestor of [url] and goes down through the transient files to [url]. */
  private fun stateOf(url: String): TraverseState? {
    val transientFiles = ArrayList<VirtualFile>()
    var file = VirtualFileManager.getInstance().findFileByUrlWithoutCaching(url) ?: error("No file for $url")
    while (file is CacheAvoidingVirtualFile && !file.isCached) {
      transientFiles.add(file)
      file = file.parent ?: error("No cached ancestor for $url")
    }
    val cachedFile = if (file is CacheAvoidingVirtualFile) file.asCacheable() ?: error("No cached file for $url") else file
    var state: TraverseState? = TraverseState.getTraverseState(cachedFile, projectModel.project)
    for (transientFile in transientFiles.asReversed()) {
      if (state == null) return null
      state = state.goDownByUrl(transientFile)
    }
    return state
  }

  private fun virtualFileUrl(url: String): VirtualFileUrl {
    return projectModel.project.workspaceModel.getVirtualFileUrlManager().get(url) ?: error("No VirtualFileUrl for $url")
  }

  private fun TraverseState.goDownByUrl(file: VirtualFile): TraverseState? {
    val urlManager = projectModel.project.workspaceModel.getVirtualFileUrlManager() as com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
    return goDown(file, urlManager.findNode(file.url), fileIndex)
  }

  private fun urlOf(relativePath: String): String {
    return VfsUtilCore.pathToUrl(projectModel.baseProjectDir.rootPath.resolve(relativePath).invariantSeparatorsPathString)
  }

  private fun createOnDisk(relativePath: String): Path {
    val path = projectModel.baseProjectDir.rootPath.resolve(relativePath)
    path.parent.createDirectories()
    return Files.writeString(path, "")
  }

  private fun assertTransient(relativePath: String) {
    val file = VirtualFileManager.getInstance().findFileByUrlWithoutCaching(urlOf(relativePath))
    assertNotNull(file)
    assertTrue(file is CacheAvoidingVirtualFile && !file.isCached, "$file must not be cached")
  }

  companion object {
    private const val EXCLUDED_DIR_NAME = "my-dir"
  }
}
