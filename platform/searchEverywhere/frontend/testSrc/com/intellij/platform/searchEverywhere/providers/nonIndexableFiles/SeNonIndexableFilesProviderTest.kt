// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.nonIndexableFiles

import com.intellij.find.DirectorySearchEngine
import com.intellij.find.DirectorySearchEngine.FileSearchCandidate
import com.intellij.find.FindModel
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeTypeFilterKeys
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.target.SeScopeById
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.utils.vfs.getPsiFile
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveFileSetContributor
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.toPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * [SeNonIndexableFilesProvider] finds the files under the non-indexable roots of the project.
 *
 * Most cases search in the Files tab without a selected scope, so the provider checks no scope.
 */
@TestApplication
@RegistryKey("se.enable.non.indexable.files.contributor", "true")
class SeNonIndexableFilesProviderTest {
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  private val baseDir get() = projectModel.baseProjectDir
  private val project get() = projectModel.project
  private val workspaceModel get() = project.workspaceModel
  private val urlManager get() = workspaceModel.getVirtualFileUrlManager()

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)
  }

  @Test
  fun `name search receives the path pattern`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/sub/file.txt")
    addNonIndexableRoots(root)
    var receivedPattern: String? = null
    registerNameSearchEngine { _, pathPattern, consumer ->
      receivedPattern = pathPattern
      consumer.accept(FileSearchCandidate.fromVirtualFile(file))
    }

    val names = searchNonIndexableFiles("sub\\fi*le").map { it.name }

    assertThat(receivedPattern).isEqualTo("sub/fi*le")
    assertThat(names).containsExactly("file.txt")
  }

  @Test
  fun `name search receives the pattern without a line suffix`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/file.txt")
    addNonIndexableRoots(root)
    var receivedPattern: String? = null
    registerNameSearchEngine { _, pathPattern, consumer ->
      receivedPattern = pathPattern
      consumer.accept(FileSearchCandidate.fromVirtualFile(file))
    }

    val names = searchNonIndexableFiles("file.txt:12").map { it.name }

    assertThat(receivedPattern).isEqualTo("file.txt")
    assertThat(names).containsExactly("file.txt")
  }

  @Test
  fun `name search accepts a path without a cached virtual file`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    addNonIndexableRoots(root)
    val path = Files.createFile(baseDir.rootPath.resolve("root/path-file.txt"))
    registerNameSearchEngine { _, _, consumer -> consumer.accept(FileSearchCandidate.fromPath(path)) }

    val names = searchNonIndexableFiles("path-file").map { it.name }

    assertThat(names).containsExactly("path-file.txt")
  }

  @Test
  fun `name search returns the root and its descendants`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/root-file.txt")
    addNonIndexableRoots(root)
    var searchCalls = 0
    registerNameSearchEngine { directory, _, consumer ->
      searchCalls++
      consumer.accept(FileSearchCandidate.fromVirtualFile(directory))
      consumer.accept(FileSearchCandidate.fromVirtualFile(file))
    }

    val names = searchNonIndexableFiles("root").map { it.name }

    assertThat(names).containsExactlyInAnyOrder("root", "root-file.txt")
    assertThat(searchCalls).isEqualTo(1)
  }

  @Test
  fun `name search returns a nested non-indexable root with its qualified path`(): Unit = timeoutRunBlocking {
    val outer = baseDir.newVirtualDirectory("outer")
    val excluded = baseDir.newVirtualDirectory("outer/excluded")
    val nested = baseDir.newVirtualDirectory("outer/excluded/nested")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(outer.url), NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(nested.url), NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(emptyList(), listOf(urlManager.storeAndGet(excluded.url)), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    registerNameSearchEngine { directory, _, consumer -> consumer.accept(FileSearchCandidate.fromVirtualFile(directory)) }

    val names = searchNonIndexableFiles("outer/excluded/nested").map { it.name }

    assertThat(names).containsExactly("nested")
  }

  @Test
  fun `an unavailable name search engine falls back to the local walk`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    baseDir.newVirtualFile("root/file.txt")
    addNonIndexableRoots(root)
    registerNameSearchEngine(getWeight = { -1 }) { _, _, _ -> error("The engine is unavailable") }

    val names = searchNonIndexableFiles("file").map { it.name }

    assertThat(names).containsExactly("file.txt")
  }

  @Test
  fun `name search falls back to the local walk when its engine fails`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    baseDir.newVirtualFile("root/file.txt")
    addNonIndexableRoots(root)
    val engineCalled = AtomicBoolean()
    registerNameSearchEngine { _, _, _ ->
      engineCalled.set(true)
      throw IOException("Connection refused")
    }

    val names = searchNonIndexableFiles("file").map { it.name }

    assertThat(engineCalled.get()).isTrue()
    assertThat(names).containsExactly("file.txt")
  }

  @Test
  fun `name search uses the engine for one root when another fails`(): Unit = timeoutRunBlocking {
    val failedRoot = baseDir.newVirtualDirectory("dead")
    val healthyRoot = baseDir.newVirtualDirectory("healthy")
    val healthyFile = baseDir.newVirtualFile("healthy/healthy-file.txt")
    addNonIndexableRoots(failedRoot, healthyRoot)
    val failedRootSearched = AtomicBoolean()
    val healthyRootSearched = AtomicBoolean()
    registerNameSearchEngine { directory, _, consumer ->
      if (directory.path == failedRoot.path) {
        failedRootSearched.set(true)
        throw IOException("Connection refused")
      }
      if (directory.path == healthyRoot.path) {
        healthyRootSearched.set(true)
        consumer.accept(FileSearchCandidate.fromVirtualFile(healthyFile))
      }
    }

    val names = searchNonIndexableFiles("file").map { it.name }

    assertThat(failedRootSearched.get()).isTrue()
    assertThat(healthyRootSearched.get()).isTrue()
    assertThat(names).containsExactly("healthy-file.txt")
  }

  @Test
  fun `collector rejection stops name search result delivery`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val first = baseDir.newVirtualFile("root/first-file.txt")
    val second = baseDir.newVirtualFile("root/second-file.txt")
    addNonIndexableRoots(root)
    registerNameSearchEngine { _, _, consumer ->
      consumer.accept(FileSearchCandidate.fromVirtualFile(first))
      consumer.accept(FileSearchCandidate.fromVirtualFile(second))
    }
    var collectorCalls = 0

    collectItems("file") {
      collectorCalls++
      false
    }

    assertThat(collectorCalls).isEqualTo(1)
  }

  @Test
  fun `absolute path pattern resolves the file as an exact match`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/sub/file.txt")
    addNonIndexableRoots(root)

    val found = collectItems("${file.path}:12")

    assertThat(found.map { it.psiItem.virtualFile }).containsExactly(file)
    assertThat(found.single().item.weight()).isEqualTo(DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE)
  }

  @Test
  fun `absolute path pattern resolves a file outside the non-indexable roots`(): Unit = timeoutRunBlocking {
    addNonIndexableRoots(baseDir.newVirtualDirectory("root"))
    val outside = baseDir.newVirtualFile("outside/other.txt")
    VfsTestUtil.syncRefresh()

    assertThat(searchNonIndexableFiles(outside.path).map { it.virtualFile }).containsExactly(outside)
  }

  @Test
  fun `relative path pattern yields the file once`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/sub/file.txt")
    addNonIndexableRoots(root)

    val found = collectItems("root/sub/file.txt")

    assertThat(found.map { it.psiItem.virtualFile }).containsExactly(file)
  }

  @Test
  fun `project relative path pattern resolves a file that no search lists`(): Unit = timeoutRunBlocking {
    addNonIndexableRoots(baseDir.newVirtualDirectory("root"))
    // outside the walked roots and not in VFS, as a git-ignored file that a file search skips
    Files.writeString(Files.createDirectories(baseDir.root.toPath().resolve(".ignored")).resolve("plan.md"), "")

    val found = collectItems(".ignored/plan.md")

    assertThat(found.map { it.psiItem.virtualFile.path }).containsExactly("${project.basePath}/.ignored/plan.md")
    assertThat(found.single().item.weight()).isEqualTo(DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE)
  }

  @Test
  fun `collector rejection of the path match stops the search`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/file.txt")
    addNonIndexableRoots(root)
    val walkStarted = AtomicBoolean(false)
    registerNameSearchEngine { _, _, _ -> walkStarted.set(true) }
    var collectorCalls = 0

    collectItems(file.path) {
      collectorCalls++
      false
    }

    assertThat(collectorCalls).isEqualTo(1)
    assertThat(walkStarted.get()).isFalse()
  }

  @Test
  @Disabled("DirectorySearchEngine name search does not receive excluded subtrees")
  fun `name search excludes indexable subtrees`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val excluded = baseDir.newVirtualDirectory("root/excluded")
    val hidden = baseDir.newVirtualFile("root/excluded/hidden-file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(emptyList(), listOf(urlManager.storeAndGet(excluded.url)), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    registerNameSearchEngine { _, _, consumer -> consumer.accept(FileSearchCandidate.fromVirtualFile(hidden)) }

    assertThat(searchNonIndexableFiles("hidden-file")).isEmpty()
  }

  @Test
  fun `collector rejection stops optimal and suboptimal result delivery`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("a")
    baseDir.newVirtualFile("a/b/c/abc-first.txt")
    baseDir.newVirtualFile("a/b/c/abc-second.txt")
    addNonIndexableRoots(root)
    assertThat(searchNonIndexableFiles("abc").map { it.name })
      .containsExactlyInAnyOrder("abc-first.txt", "abc-second.txt", "c")

    val collectedNames = collectItems("abc") { false }.map { it.psiItem.name }

    assertThat(collectedNames).singleElement().isIn("abc-first.txt", "abc-second.txt")
  }

  @Test
  fun `unindexed under exclude`(): Unit = timeoutRunBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val exclude = baseDir.newVirtualDirectory("u1/exclude").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u1/exclude/u2").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/exclude/u2/f")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(emptyList(), listOf(exclude), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = searchNonIndexableFiles("u").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("u1", "u2")
  }

  @Test
  fun `2 non-indexable roots on one directory`(): Unit = timeoutRunBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/d1/f1")
    baseDir.newVirtualFile("u1/d1/f2")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = searchNonIndexableFiles("f").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("f1", "f2")
  }

  @Test
  fun `the current file scope includes only one file of two`(): Unit = timeoutRunBlocking {
    val roots = arrayOf(baseDir.newVirtualDirectory("u1"), baseDir.newVirtualDirectory("u2"))
    baseDir.newVirtualFile("u1/d1/f1")
    val f2 = baseDir.newVirtualFile("u2/d2/f2")
    addNonIndexableRoots(*roots)
    val f2PsiFile = readAction { f2.getPsiFile(project) }
    val currentFileScopeId = SeScopeById.generateScopeId(IdeBundle.message("scope.current.file"))

    val names = searchNonIndexableFiles("f", filesTabFilter(currentFileScopeId), psiContext = f2PsiFile).map { it.name }

    assertThat(names).containsExactlyInAnyOrder("f2")
  }

  @Test
  @RegistryKey("lookup.non.indexable.content.in.project.scope", "false")
  fun `the project scope finds nothing when the registry turns off its search`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    baseDir.newVirtualFile("root/needle.txt")
    addNonIndexableRoots(root)
    val projectScopeId = SeScopeById.generateScopeId(GlobalSearchScope.projectScope(project).displayName)
    val everywhereScopeId = SeScopeById.generateScopeId(GlobalSearchScope.everythingScope(project).displayName)

    val inProject = searchNonIndexableFiles("needle", filesTabFilter(projectScopeId)).map { it.name }
    val everywhere = searchNonIndexableFiles("needle", filesTabFilter(everywhereScopeId)).map { it.name }

    assertThat(inProject).isEmpty()
    assertThat(everywhere).containsExactly("needle.txt")
  }

  @Test
  fun `unindexed under content`(): Unit = timeoutRunBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val content = baseDir.newVirtualDirectory("u1/content").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u1/content/u2").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/content/u2/f")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(listOf(content), emptyList(), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = searchNonIndexableFiles("u").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("u1") // `u2` is excluded because it's under content root
  }

  @Test
  fun `unindexed under unindexed`(): Unit = timeoutRunBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualDirectory("u1/justDir").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u1/justDir/u2").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/justDir/u2/f")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = searchNonIndexableFiles("u").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("u1", "u2", "justDir")
  }

  @Test
  fun `indexable non-recursive file set inside non-indexable`(): Unit = timeoutRunBlocking {
    val nonIndexable = baseDir.newVirtualDirectory("non-indexable").toVirtualFileUrl(urlManager)
    val indexableNonRecursive = baseDir.newVirtualDirectory("non-indexable/indexable-non-recursive").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("non-indexable/indexable-non-recursive/non-indexable-file.txt")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(nonIndexable, NonPersistentEntitySource))
      storage.addEntity(NonRecursiveTestEntity(indexableNonRecursive, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = searchNonIndexableFiles("non-indexable").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("non-indexable-file.txt", "non-indexable")
  }

  @Test
  fun `unindexed and non-recursive file set at the same level`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root-file").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("root-file/file.txt")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(root, NonPersistentEntitySource))
      storage.addEntity(NonRecursiveTestEntity(root, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = searchNonIndexableFiles("file").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("file.txt") // `root` is excluded because it's under non-recursive content root
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `symlink to file`(): Unit = timeoutRunBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val f = baseDir.newVirtualFile("u1/d2/f3-1").toNioPath()
    Files.createSymbolicLink(baseDir.rootPath.resolve("u1/d2/f3-2"), f)
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = searchNonIndexableFiles("f").map { it.name }
    assertThat(names).contains("f3-1", "f3-2")
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `circular symlinks`(): Unit = timeoutRunBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u2").toVirtualFileUrl(urlManager)
    Files.createSymbolicLink(baseDir.rootPath.resolve("u1/u-link-1"), unindexed2.toPath())
    Files.createSymbolicLink(baseDir.rootPath.resolve("u2/u-link-2"), unindexed1.toPath())

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = searchNonIndexableFiles("u").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("u1", "u2", "u-link-1", "u-link-1", "u-link-2", "u-link-2")
  }

  @Test
  fun `the All tab finds a file`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("dir1")
    baseDir.newVirtualFile("dir1/file1")
    addNonIndexableRoots(root)
    val allTabFilter = SeEverywhereFilterImpl(isAllTab = true, isEverywhere = true, disabledProviderIds = emptyList()).toState()

    val names = searchNonIndexableFiles("file1", allTabFilter).map { it.name }

    assertThat(names).containsExactlyInAnyOrder("file1")
  }

  @Test
  fun `the Files tab hides a file of a hidden type`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("dir1")
    val file1 = baseDir.newVirtualFile("dir1/file1")
    addNonIndexableRoots(root)
    val hiddenTypeName = readAction { FileTypeRef.forFileType(file1.fileType).displayName }

    val visible = searchNonIndexableFiles("file1").map { it.name }
    val hidden = searchNonIndexableFiles("file1", filesTabFilter(hiddenTypes = listOf(hiddenTypeName)))

    assertThat(visible).containsExactly("file1")
    assertThat(hidden).isEmpty()
  }

  @Test
  fun `a filter with the everywhere flag keeps its hidden types`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("dir1")
    val file1 = baseDir.newVirtualFile("dir1/file1")
    addNonIndexableRoots(root)
    val hiddenTypeName = readAction { FileTypeRef.forFileType(file1.fileType).displayName }
    // The filter of a product that sets the flag in the Files tab, see `SeTabsCustomizer.customizeFilterEditor`.
    val everywhere = mapOf(SeEverywhereFilterImpl.KEY_IS_EVERYWHERE to listOf(true.toString()))

    val visible = searchNonIndexableFiles("file1", filesTabFilter().plus(everywhere)).map { it.name }
    val hidden = searchNonIndexableFiles("file1", filesTabFilter(hiddenTypes = listOf(hiddenTypeName)).plus(everywhere))

    assertThat(visible).containsExactly("file1")
    assertThat(hidden).isEmpty()
  }

  @Test
  fun `search everywhere with slashes`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("dir1")
    baseDir.newVirtualFile("dir1/file1")
    addNonIndexableRoots(root)

    val names = searchNonIndexableFiles("dir1/file1").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("file1")
  }

  @Test
  fun `search everywhere with slashes, inner dir in pattern`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("dir1")
    baseDir.newVirtualFile("dir1/folder/file1")
    addNonIndexableRoots(root)

    val names = searchNonIndexableFiles("folder/file1").map { it.name }
    assertThat(names).containsExactlyInAnyOrder("file1")
  }

  private suspend fun addNonIndexableRoots(vararg roots: VirtualFile) {
    workspaceModel.update { storage ->
      for (root in roots) {
        storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
      }
    }
    VfsTestUtil.syncRefresh()
  }

  private fun registerNameSearchEngine(
    getWeight: (VirtualFile) -> Int = { if (it.isDirectory) 1 else -1 },
    searchNames: (VirtualFile, String, Consumer<FileSearchCandidate>) -> Unit,
  ) {
    val engine = TestDirectorySearchEngine(getWeight, searchNames)
    ExtensionTestUtil.maskExtensions(DirectorySearchEngine.EP_NAME, listOf(engine), disposable)
  }

  private suspend fun searchNonIndexableFiles(
    pattern: String,
    filter: SeFilterState = filesTabFilter(),
    psiContext: PsiElement? = null,
  ): Set<PsiFileSystemItem> =
    collectItems(pattern, filter, psiContext).mapTo(HashSet()) { it.psiItem }

  /**
   * Collects the items of the provider for [pattern]. [collector] returns `false` to stop the search.
   */
  private suspend fun collectItems(
    pattern: String,
    filter: SeFilterState = filesTabFilter(),
    psiContext: PsiElement? = null,
    collector: (SeItem) -> Boolean = { true },
  ): List<CollectedItem> {
    val psiContextPointer = psiContext?.let { readAction { SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it) } }
    val provider = SeNonIndexableFilesProvider(project, psiContextPointer)
    try {
      val collected = mutableListOf<CollectedItem>()
      provider.collectItems(SeParams(pattern, filter)) { item ->
        collected.add(CollectedItem(item, provider.getPsiElementForItem(item) as PsiFileSystemItem))
        collector(item)
      }
      return collected
    }
    finally {
      Disposer.dispose(provider)
    }
  }

  private class CollectedItem(val item: SeItem, val psiItem: PsiFileSystemItem)
}

/** This state with [keys] on top. */
private fun SeFilterState.plus(keys: Map<String, List<String>>): SeFilterState =
  SeFilterState.Data(this.keys.associateWith { get(it).orEmpty() } + keys)

private fun filesTabFilter(scopeId: String? = null, hiddenTypes: List<String> = emptyList()): SeFilterState =
  SeTargetsFilter(scopeId, isAutoTogglePossible = false, mapOf(SeTypeFilterKeys.DEFAULT to hiddenTypes)).toState()

private class TestDirectorySearchEngine(
  private val weight: (VirtualFile) -> Int,
  private val nameSearch: (VirtualFile, String, Consumer<FileSearchCandidate>) -> Unit,
) : DirectorySearchEngine {
  override fun canSearch(findModel: FindModel): Boolean = false

  override fun canSearchNames(): Boolean = true

  override fun getWeight(directory: VirtualFile): Int = weight(directory)

  override fun searchDirectory(
    directory: VirtualFile,
    findModel: FindModel,
    consumer: Consumer<in Collection<VirtualFile>>,
  ) = error("Content search is not supported")

  override fun searchNames(directory: VirtualFile, pathPattern: String, consumer: Consumer<FileSearchCandidate>) {
    nameSearch(directory, pathPattern, consumer)
  }
}
