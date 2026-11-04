// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.vcsUtil.VcsUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * After a status check (any action whose [ActionInfo.canChangeFileStatus] is true; Update Project's is false), the listed files are marked
 * dirty by path: resolving each one through VFS is a disk stat per file (IJPL-257565).
 */
@TestApplication
class VcsUpdateTaskTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @JvmField
  @RegisterExtension
  val tempDir: TempDirectoryExtension = TempDirectoryExtension()

  private val project get() = projectFixture.get()

  @Test
  fun `updated files are marked dirty as paths without resolving VFS`() {
    val cached = (0 until 3).map { cachedFile("cached/c$it.txt") }
    val notLoaded = (0 until 200).map { diskFile("notLoaded/sub${it % 5}/n$it.txt") }
    val missing = (0 until 50).map { path("missing/m$it.txt") }
    val cachedDirectory = cachedDirectory("cachedDir/d") // e.g. svn reports a property change on a directory
    assertThat(notLoaded).allMatch { !isCached(it) }

    val modified = notLoaded.take(150) + cached + cachedDirectory
    val created = notLoaded.drop(150)
    val dirtyScopeManager = RecordingDirtyScopeManager()
    val disposable = Disposer.newDisposable()
    try {
      project.replaceService(VcsDirtyScopeManager::class.java, dirtyScopeManager, disposable)
      val vcs = MockAbstractVcs(project)
      val environment = FillingUpdateEnvironment(mapOf(
        FileGroup.MODIFIED_ID to modified,
        FileGroup.CREATED_ID to created,
        FileGroup.REMOVED_FROM_REPOSITORY_ID to missing,
      ))
      val root = VcsUtil.getFilePath(tempDir.rootPath, true)
      timeoutRunBlocking {
        VcsUpdateProcess.update(project, arrayOf(root), listOf(VcsUpdateSpecification(vcs, environment, listOf(root))), ActionInfo.STATUS,
                                "Check Status")
      }
    }
    finally {
      Disposer.dispose(disposable)
    }

    val rootPrefix = path("")
    val dirtyPaths = dirtyScopeManager.filePaths.filter { FileUtil.startsWith(it.path, rootPrefix) }
    assertThat(dirtyPaths.map { it.path }).containsExactlyInAnyOrderElementsOf(modified + created + missing)
    assertThat(dirtyPaths.filter { it.isDirectory }.map { it.path }).describedAs("paths marked dirty as directories")
      .containsExactly(cachedDirectory)
    assertThat(dirtyScopeManager.virtualFiles.filter { FileUtil.startsWith(it.path, rootPrefix) })
      .describedAs("files marked dirty through VirtualFile").isEmpty()
    assertThat(notLoaded.filter { isCached(it) }).describedAs("files loaded into VFS by marking them dirty").isEmpty()
  }

  private class FillingUpdateEnvironment(private val groups: Map<String, List<String>>) : UpdateEnvironment {
    override fun fillGroups(updatedFiles: UpdatedFiles) {}

    override fun updateDirectories(
      contentRoots: Array<FilePath>,
      updatedFiles: UpdatedFiles,
      progressIndicator: ProgressIndicator,
      context: Ref<SequentialUpdatesContext>,
    ): UpdateSession {
      for ((groupId, paths) in groups) {
        val group = updatedFiles.getGroupById(groupId)!!
        for (path in paths) {
          group.add(path, "Mock", null)
        }
      }
      return object : UpdateSession {
        override fun getExceptions(): List<VcsException> = emptyList()
        override fun onRefreshFilesCompleted() {}
        override fun isCanceled(): Boolean = false
      }
    }

    override fun createConfigurable(files: Collection<FilePath>): Configurable? = null

    override fun validateOptions(roots: Collection<FilePath>): Boolean = true
  }

  private class RecordingDirtyScopeManager : VcsDirtyScopeManager() {
    val filePaths = ConcurrentLinkedQueue<FilePath>()
    val virtualFiles = ConcurrentLinkedQueue<VirtualFile>()

    override fun filePathsDirty(filesDirty: Collection<FilePath>?, dirsRecursivelyDirty: Collection<FilePath>?) {
      filesDirty?.let(filePaths::addAll)
      dirsRecursivelyDirty?.let(filePaths::addAll)
    }

    override fun filesDirty(filesDirty: Collection<VirtualFile>?, dirsRecursivelyDirty: Collection<VirtualFile>?) {
      filesDirty?.let(virtualFiles::addAll)
      dirsRecursivelyDirty?.let(virtualFiles::addAll)
    }

    override fun fileDirty(file: VirtualFile) {
      virtualFiles.add(file)
    }

    override fun fileDirty(file: FilePath) {
      filePaths.add(file)
    }

    override fun dirDirtyRecursively(dir: VirtualFile) {
      virtualFiles.add(dir)
    }

    override fun dirDirtyRecursively(path: FilePath) {
      filePaths.add(path)
    }

    override fun rootDirty(root: VirtualFile) {
      virtualFiles.add(root)
    }

    override fun markEverythingDirty() {}

    override fun whatFilesDirty(files: Collection<FilePath>): Collection<FilePath> = emptyList()
  }

  private fun path(relativePath: String): String = FileUtil.toSystemIndependentName(tempDir.rootPath.resolve(relativePath).toString())

  private fun diskFile(relativePath: String): String {
    val file: Path = tempDir.rootPath.resolve(relativePath)
    file.parent.createDirectories()
    file.writeText(relativePath)
    return path(relativePath)
  }

  private fun cachedFile(relativePath: String): String {
    diskFile(relativePath)
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir.rootPath.resolve(relativePath))
    assertThat(file).describedAs(relativePath).isNotNull()
    return path(relativePath)
  }

  private fun cachedDirectory(relativePath: String): String {
    val directory = tempDir.rootPath.resolve(relativePath).createDirectories()
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)
    assertThat(file).describedAs(relativePath).isNotNull()
    return path(relativePath)
  }

  private fun isCached(path: String): Boolean = LocalFileSystem.getInstance().findFileByPathIfCached(path) != null
}
