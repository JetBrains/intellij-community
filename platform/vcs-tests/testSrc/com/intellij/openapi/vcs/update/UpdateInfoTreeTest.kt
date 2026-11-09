// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * The Updated Files tree is built and painted from paths only: building it must not load anything into VFS (IJPL-257565).
 */
@TestApplication
class UpdateInfoTreeTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @JvmField
  @RegisterExtension
  val tempDir: TempDirectoryExtension = TempDirectoryExtension()

  private val project get() = projectFixture.get()

  @Test
  fun `building the tree over thousands of paths resolves nothing through VFS`() {
    val cached = (0 until 5).map { cachedFile("cached/c$it.txt") }
    val notLoaded = (0 until 3500).map { diskFile("notLoaded/sub${it % 10}/n$it.txt") }
    val missing = (0 until 1500).map { path("missing/m$it.txt") }
    assertThat(notLoaded).allMatch { !isCached(it) }

    val modified = notLoaded.take(2500) + cached
    val created = notLoaded.drop(2500)
    val files = UpdatedFiles.create()
    addAll(files, FileGroup.MODIFIED_ID, modified)
    addAll(files, FileGroup.CREATED_ID, created)
    addAll(files, FileGroup.REMOVED_FROM_REPOSITORY_ID, missing)
    files.getGroupById(FileGroup.MODIFIED_ID)!!.addError(modified[7], "boom")

    withTree(files) { tree ->
      val modifiedGroup = group(tree, FileGroup.MODIFIED_ID)
      val createdGroup = group(tree, FileGroup.CREATED_ID)
      val removedGroup = group(tree, FileGroup.REMOVED_FROM_REPOSITORY_ID)
      assertThat(modifiedGroup.name).isEqualTo(ActionInfo.UPDATE.getGroupName(files.getGroupById(FileGroup.MODIFIED_ID)))
      assertThat(createdGroup.name).isEqualTo(ActionInfo.UPDATE.getGroupName(files.getGroupById(FileGroup.CREATED_ID)))
      assertThat(removedGroup.name).isEqualTo(ActionInfo.UPDATE.getGroupName(files.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID)))

      assertThat(rowPaths(modifiedGroup)).isEqualTo(modified.sortedWith(String.CASE_INSENSITIVE_ORDER))
      assertThat(rowPaths(createdGroup)).isEqualTo(created.sortedWith(String.CASE_INSENSITIVE_ORDER))
      assertThat(rowPaths(removedGroup)).isEqualTo(missing.sortedWith(String.CASE_INSENSITIVE_ORDER))
      assertThat(fileNode(tree, modified[7]).name).isEqualTo(FileUtil.toSystemDependentName(modified[7]))
      assertThat(fileNode(tree, modified[7]).errorText).isEqualTo("boom")
      assertThat(fileNode(tree, modified[8]).errorText).isNull()
      assertThat(modifiedGroup.text).endsWith("(${VcsBundle.message("update.tree.node.size.statistics", modified.size)})")
    }

    assertThat(notLoaded.filter { isCached(it) }).describedAs("files loaded into VFS by building the tree").isEmpty()
  }

  @Test
  fun `rows are painted from the VFS cache only`() {
    val cachedModified = cachedFile("cached/modified.txt")
    val cachedRemoved = cachedFile("cached/removed.txt")
    val notLoaded = diskFile("notLoaded/n.txt")
    val missing = path("missing/m.txt")
    val files = UpdatedFiles.create()
    addAll(files, FileGroup.MODIFIED_ID, listOf(cachedModified, notLoaded))
    addAll(files, FileGroup.REMOVED_FROM_REPOSITORY_ID, listOf(cachedRemoved, missing))

    withTree(files) { tree ->
      val removedInvalid = files.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID)!!.invalidAttributes
      assertSameLook(fileNode(tree, missing).attributes, removedInvalid)
      assertSameLook(fileNode(tree, cachedRemoved).attributes, statusAttributes(cachedRemoved))
      assertSameLook(fileNode(tree, cachedModified).attributes, statusAttributes(cachedModified))
      assertSameLook(fileNode(tree, notLoaded).attributes, plainAttributes(FileStatus.NOT_CHANGED))
      assertThat(fileNode(tree, notLoaded).getIcon(false)).isNotNull()
    }
    assertThat(isCached(notLoaded)).describedAs("painting resolved a file").isFalse()
  }

  @Test
  fun `selection data resolves the selected rows`() {
    val cachedModified = cachedFile("cached/modified.txt")
    val created = cachedFile("cached/created.txt")
    val missing = path("missing/m.txt")
    val files = UpdatedFiles.create()
    addAll(files, FileGroup.MODIFIED_ID, listOf(cachedModified))
    addAll(files, FileGroup.CREATED_ID, listOf(created))
    addAll(files, FileGroup.REMOVED_FROM_REPOSITORY_ID, listOf(missing))

    withTree(files) { tree ->
      val jTree = jTree(tree)

      jTree.selectionPath = TreePath(fileNode(tree, missing).path)
      val missingData = RecordingSink().also { tree.uiDataSnapshot(it) }
      jTree.selectionPath = TreePath(fileNode(tree, cachedModified).path)
      val data = RecordingSink().also { tree.uiDataSnapshot(it) }

      assertThat(data.lazies.keys).describedAs("keys that resolve through VFS are computed off EDT")
        .contains(CommonDataKeys.NAVIGATABLE, CommonDataKeys.VIRTUAL_FILE_ARRAY, VcsDataKeys.FILE_PATHS)
      assertThat(data.values[UpdateInfoTree.UPDATE_VIEW_SELECTED_PATH]).isEqualTo(filePath(cachedModified))
      assertThat(missingData.values[UpdateInfoTree.UPDATE_VIEW_SELECTED_PATH]).isEqualTo(filePath(missing))
      @Suppress("UNCHECKED_CAST")
      val statuses = (data.values[UpdateInfoTree.UPDATE_VIEW_FILES_ITERABLE] as Iterable<Pair<FilePath, FileStatus>>)
        .associate { it.first to it.second }
      assertThat(statuses).containsExactlyInAnyOrderEntriesOf(mapOf(
        filePath(cachedModified) to FileStatus.MODIFIED,
        filePath(created) to FileStatus.ADDED,
        filePath(missing) to FileStatus.DELETED,
      ))

      offEdt {
        val navigatable = data.lazy(CommonDataKeys.NAVIGATABLE)
        assertThat(navigatable).isInstanceOf(OpenFileDescriptor::class.java)
        assertThat((navigatable as OpenFileDescriptor).file).isEqualTo(vFile(cachedModified))
        assertThat(data.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY)).containsExactly(vFile(cachedModified))
        assertThat(data.lazy(VcsDataKeys.FILE_PATHS)).isNull()

        assertThat(missingData.lazy(CommonDataKeys.NAVIGATABLE)).isNotNull().isNotInstanceOf(OpenFileDescriptor::class.java)
        assertThat(missingData.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY)).isEmpty()
        assertThat(missingData.lazy(VcsDataKeys.FILE_PATHS)).containsExactly(filePath(missing))
      }
    }
  }

  @Test
  fun `navigation to a missing file warns`() {
    val missing = path("missing/m.txt")
    val files = UpdatedFiles.create()
    addAll(files, FileGroup.REMOVED_FROM_REPOSITORY_ID, listOf(missing))

    withTree(files) { tree ->
      val notifications = ArrayList<Notification>()
      project.messageBus.connect(tree).subscribe(Notifications.TOPIC, object : Notifications {
        override fun notify(notification: Notification) {
          notifications.add(notification)
        }
      })
      jTree(tree).selectionPath = TreePath(fileNode(tree, missing).path)
      val data = RecordingSink().also { tree.uiDataSnapshot(it) }
      var navigatable: Navigatable? = null
      offEdt { navigatable = data.lazy(CommonDataKeys.NAVIGATABLE) }

      assertThat(navigatable).isNotInstanceOf(OpenFileDescriptor::class.java)
      assertThat(navigatable!!.canNavigate()).isTrue()
      navigatable!!.navigate(true)

      assertThat(notifications).singleElement().satisfies({
        assertThat(it.type).isEqualTo(NotificationType.WARNING)
        assertThat(it.content).contains(FileUtil.toSystemDependentName(missing))
      })
    }
  }

  @Test
  fun `selection data resolves the rows taken on EDT when the lazies run`() {
    val unknown = cachedFile("cached/unknown.txt")
    val missing = path("missing/m.txt")
    val files = UpdatedFiles.create()
    addAll(files, FileGroup.UNKNOWN_ID, listOf(unknown, missing))

    withTree(files) { tree ->
      val unknownGroup = group(tree, FileGroup.UNKNOWN_ID)
      jTree(tree).selectionPath = TreePath(unknownGroup.path)
      val data = RecordingSink().also { tree.uiDataSnapshot(it) }

      VfsTestUtil.deleteFile(vFile(unknown))
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertThat(rowPaths(unknownGroup)).describedAs("local changes are not tracked").containsExactly(unknown, missing)

      offEdt {
        assertThat(data.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY)).isEmpty()
        assertThat(data.lazy(VcsDataKeys.FILE_PATHS)).containsExactlyInAnyOrder(filePath(unknown), filePath(missing))
      }
    }
  }

  @Suppress("NonExtendableApiUsage")
  private class RecordingSink : DataSink {
    val values = HashMap<DataKey<*>, Any?>()
    val lazies = HashMap<DataKey<*>, (DataMap) -> Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> lazy(key: DataKey<T>): T? = lazies[key]!!.invoke(EMPTY_MAP) as T?

    override fun <T : Any> set(key: DataKey<T>, data: T?) {
      values[key] = data
    }

    override fun <T : Any> setNull(key: DataKey<T>) {
      values[key] = null
    }

    override fun <T : Any> lazyValue(key: DataKey<T>, data: (provider: DataMap) -> T?) {
      lazies[key] = data
    }

    override fun <T : Any> lazyNull(key: DataKey<T>) {
      lazies[key] = { null }
    }

    override fun uiDataSnapshot(provider: UiDataProvider) = provider.uiDataSnapshot(this)

    override fun dataSnapshot(provider: DataSnapshotProvider) = Unit

    @Deprecated("not used")
    override fun uiDataSnapshot(provider: DataProvider) = Unit

    private companion object {
      val EMPTY_MAP = object : DataMap {
        override fun <T : Any> get(key: DataKey<T>): T? = null
      }
    }
  }

  private fun withTree(files: UpdatedFiles, block: (UpdateInfoTree) -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      runInEdtAndWait {
        val contentManager = ContentFactory.getInstance().createContentManager(true, project)
        Disposer.register(disposable, contentManager)
        val tree = UpdateInfoTree(contentManager, project, files, "root", ActionInfo.UPDATE)
        Disposer.register(disposable, tree)
        block(tree)
      }
    }
    finally {
      runInEdtAndWait { Disposer.dispose(disposable) }
    }
  }

  private fun offEdt(block: () -> Unit) {
    var error: Throwable? = null
    val thread = Thread {
      try {
        runReadActionBlocking(block)
      }
      catch (e: Throwable) {
        error = e
      }
    }
    thread.start()
    while (thread.isAlive) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      thread.join(10)
    }
    error?.let { throw it }
  }

  private fun root(tree: UpdateInfoTree): UpdateRootNode = jTree(tree).model.root as UpdateRootNode

  private fun jTree(tree: UpdateInfoTree): Tree = UIUtil.findComponentsOfType(tree, Tree::class.java).first { it.model.root is UpdateRootNode }

  private fun group(tree: UpdateInfoTree, id: String): GroupTreeNode =
    nodes(root(tree)).filterIsInstance<GroupTreeNode>().single { it.fileGroupId == id }

  private fun fileNode(tree: UpdateInfoTree, path: String): FileTreeNode =
    nodes(root(tree)).filterIsInstance<FileTreeNode>().single { it.filePath.path == path }

  private fun rowPaths(group: GroupTreeNode): List<String> =
    (0 until group.childCount).map { (group.getChildAt(it) as FileTreeNode).filePath.path }

  private fun nodes(node: TreeNode): Sequence<TreeNode> =
    sequenceOf(node) + (0 until node.childCount).asSequence().flatMap { nodes(node.getChildAt(it)) }

  private fun addAll(files: UpdatedFiles, groupId: String, paths: List<String>) {
    val group = files.getGroupById(groupId)!!
    for (path in paths) {
      group.add(path, "Mock", null)
    }
  }

  private fun statusAttributes(path: String): SimpleTextAttributes =
    plainAttributes(FileStatusManager.getInstance(project).getStatus(vFile(path)))

  private fun plainAttributes(status: FileStatus): SimpleTextAttributes =
    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, status.color ?: UIUtil.getListForeground())

  private fun assertSameLook(actual: SimpleTextAttributes, expected: SimpleTextAttributes) {
    assertThat(actual.fgColor).isEqualTo(expected.fgColor)
    assertThat(actual.style).isEqualTo(expected.style)
  }

  private fun path(relativePath: String): String = FileUtil.toSystemIndependentName(tempDir.rootPath.resolve(relativePath).toString())

  private fun diskFile(relativePath: String): String {
    val file: Path = tempDir.rootPath.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(relativePath)
    return path(relativePath)
  }

  /** Loads only this file and its ancestors into VFS, so the siblings of each ancestor stay unloaded. */
  private fun cachedFile(relativePath: String): String {
    diskFile(relativePath)
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir.rootPath.resolve(relativePath))
    assertThat(file).describedAs(relativePath).isNotNull()
    return path(relativePath)
  }

  private fun isCached(path: String): Boolean = LocalFileSystem.getInstance().findFileByPathIfCached(path) != null

  private fun vFile(path: String): VirtualFile = LocalFileSystem.getInstance().findFileByPath(path)!!

  private fun filePath(path: String): FilePath = VcsUtil.getFilePath(path, false)
}
