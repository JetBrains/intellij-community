// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.RemoteFilePath
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.platform.vcs.impl.shared.rpc.UnversionedFilesDto
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.vcs.MockChangeListManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@TestApplication
internal class ChangeListsApiImplTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()
  private val fileSystem get() = LocalFileSystem.getInstance()
  private lateinit var manager: TestChangeListManager

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    manager = TestChangeListManager()
    project.registerOrReplaceServiceInstance(ChangeListManager::class.java, manager, disposable)
  }

  @Test
  fun `uncached unversioned paths are sent without resolving virtual files`(@TempDir directory: Path) {
    checkUncachedPaths(FileList.UNVERSIONED, directory)
  }

  @Test
  fun `uncached ignored paths are sent without resolving virtual files`(@TempDir directory: Path) {
    checkUncachedPaths(FileList.IGNORED, directory)
  }

  @Test
  fun `cached unversioned paths retain no virtual file IDs`(@TempDir directory: Path) {
    checkNoVirtualFilePaths(FileList.UNVERSIONED, directory)
  }

  @Test
  fun `cached ignored paths retain no virtual file IDs`(@TempDir directory: Path) {
    checkNoVirtualFilePaths(FileList.IGNORED, directory)
  }

  @Test
  fun `nonlocal unversioned paths do not acquire a local file ID`(@TempDir directory: Path) {
    checkNonlocalPath(FileList.UNVERSIONED, directory)
  }

  @Test
  fun `nonlocal ignored paths do not acquire a local file ID`(@TempDir directory: Path) {
    checkNonlocalPath(FileList.IGNORED, directory)
  }

  @Test
  fun `updated unversioned paths are sent without resolving virtual files`(@TempDir directory: Path) {
    checkUpdatedPaths(FileList.UNVERSIONED, directory)
  }

  @Test
  fun `updated ignored paths are sent without resolving virtual files`(@TempDir directory: Path) {
    checkUpdatedPaths(FileList.IGNORED, directory)
  }

  private fun checkUncachedPaths(list: FileList, directory: Path): Unit =
    timeoutRunBlocking {
      val paths = listOf(
        unresolvedPath(Files.createFile(directory.resolve("file.txt"))),
        unresolvedPath(Files.createDirectory(directory.resolve("folder")), isDirectory = true),
      )
      paths.forEach { assertNull(fileSystem.findFileByPathIfCached(it.path)) }
      manager.setPaths(list, paths)

      val dtos = observe(list).first()

      assertEquals(paths.size, dtos.size)
      paths.zip(dtos).forEach { (path, dto) ->
        assertDto(path, dto, virtualFileId = null)
        assertNull(fileSystem.findFileByPathIfCached(path.path))
      }
    }

  @Test
  fun `large unversioned lists send only counts without reading paths`(): Unit = timeoutRunBlocking {
    manager.setPaths(FileList.UNVERSIONED, listOf(countedPath(false), countedPath(true), countedPath(false), countedPath(true)))

    val dto = ChangeListsApiImpl().getUnversionedFiles(project.projectId(), 2).first()

    assertEquals(UnversionedFilesDto.Counts(2, 2), dto)
    val serialized = Json.encodeToString(UnversionedFilesDto.serializer(), dto)
    assertEquals(dto, Json.decodeFromString(UnversionedFilesDto.serializer(), serialized))
  }

  @Test
  fun `unversioned lists at the limit still send paths`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    val paths = listOf(unresolvedPath(directory.resolve("first.txt")), unresolvedPath(directory.resolve("second.txt")))
    manager.setPaths(FileList.UNVERSIONED, paths)

    val dto = ChangeListsApiImpl().getUnversionedFiles(project.projectId(), 2).first()

    val files = assertInstanceOf(UnversionedFilesDto.Files::class.java, dto).files
    assertEquals(paths.size, files.size)
    paths.zip(files).forEach { (path, file) -> assertDto(path, file, null) }
  }

  @Test
  fun `unversioned updates switch between counts paths and an empty list`(@TempDir directory: Path): Unit = timeoutRunBlocking {
    val large = listOf(countedPath(false), countedPath(true), countedPath(false))
    val small = listOf(unresolvedPath(directory.resolve("file.txt")))
    val snapshots = listOf(large, small, emptyList(), listOf(countedPath(true), countedPath(true), countedPath(true)))
    manager.setPaths(FileList.UNVERSIONED, snapshots.first())
    val remainingSnapshots = snapshots.drop(1).iterator()

    val updates = ChangeListsApiImpl().getUnversionedFiles(project.projectId(), 2).onEach {
      if (remainingSnapshots.hasNext()) {
        manager.setPaths(FileList.UNVERSIONED, remainingSnapshots.next())
        manager.listener!!.unchangedFileStatusChanged(true)
      }
    }.take(snapshots.size).toList()

    assertEquals(UnversionedFilesDto.Counts(2, 1), updates[0])
    assertDto(small.single(), assertInstanceOf(UnversionedFilesDto.Files::class.java, updates[1]).files.single(), null)
    assertEquals(UnversionedFilesDto.Files(emptyList()), updates[2])
    assertEquals(UnversionedFilesDto.Counts(0, 3), updates[3])
  }

  @Test
  fun `changed unversioned paths with the same counts send no update`(): Unit = timeoutRunBlocking {
    manager.setPaths(FileList.UNVERSIONED, listOf(countedPath(false), countedPath(true), countedPath(false)))

    val updates = ChangeListsApiImpl().getUnversionedFiles(project.projectId(), 2).onEach {
      if (manager.unversionedReads == 1) {
        manager.setPaths(FileList.UNVERSIONED, listOf(countedPath(true), countedPath(false), countedPath(false)))
        manager.listener!!.unchangedFileStatusChanged(true)
        while (manager.unversionedReads < 2) yield()
        manager.setPaths(FileList.UNVERSIONED, listOf(countedPath(true), countedPath(false), countedPath(true)))
        manager.listener!!.unchangedFileStatusChanged(true)
      }
    }.take(2).toList()

    assertEquals(listOf(
      UnversionedFilesDto.Counts(2, 1),
      UnversionedFilesDto.Counts(1, 2)
    ), updates)
  }

  private fun countedPath(isDirectory: Boolean): FilePath = object : FilePath by LocalFilePath("/counted", isDirectory) {
    override fun getPath(): String = error("A count must not read the path")
    override fun getVirtualFile(): VirtualFile = error("A count must not resolve a virtual file")
  }

  private fun checkNoVirtualFilePaths(list: FileList, directory: Path): Unit =
    timeoutRunBlocking {
      val paths = listOf(
        unresolvedPath(Files.createFile(directory.resolve("file.txt"))),
        unresolvedPath(Files.createDirectory(directory.resolve("folder")), isDirectory = true),
      )
      manager.setPaths(list, paths)

      val dtos = observe(list).first()

      assertEquals(paths.size, dtos.size)
      paths.indices.forEach { index ->
        assertDto(paths[index], dtos[index], null)
      }
    }

  private fun checkNonlocalPath(list: FileList, directory: Path): Unit =
    timeoutRunBlocking {
      val file = Files.createFile(directory.resolve("file.txt"))
      val path = RemoteFilePath(file.invariantSeparatorsPathString, false)
      assertNotNull(fileSystem.refreshAndFindFileByPath(path.path))
      assertNotNull(fileSystem.findFileByPathIfCached(path.path))
      manager.setPaths(list, listOf(path))

      assertDto(path, observe(list).first().single(), virtualFileId = null)
    }

  private fun checkUpdatedPaths(list: FileList, directory: Path): Unit =
    timeoutRunBlocking {
      val initial = unresolvedPath(Files.createFile(directory.resolve("initial.txt")))
      val added = unresolvedPath(Files.createFile(directory.resolve("added.txt")))
      manager.setPaths(list, listOf(initial))

      val updates = observe(list).onEach {
        if (it.size == 1) {
          manager.setPaths(list, listOf(initial, added))
          manager.listener!!.unchangedFileStatusChanged(true)
        }
      }.take(2).toList()

      assertEquals(listOf(1, 2), updates.map { it.size })
      assertDto(initial, updates[0].single(), virtualFileId = null)
      assertDto(initial, updates[1][0], virtualFileId = null)
      assertDto(added, updates[1][1], virtualFileId = null)
      assertNull(fileSystem.findFileByPathIfCached(initial.path))
      assertNull(fileSystem.findFileByPathIfCached(added.path))
      assertNull(manager.listener)
    }

  private suspend fun observe(list: FileList): Flow<List<FilePathDto>> {
    val api = ChangeListsApiImpl()
    return when (list) {
      FileList.UNVERSIONED -> api.getUnversionedFiles(project.projectId(), Int.MAX_VALUE).map { assertInstanceOf(UnversionedFilesDto.Files::class.java, it).files }
      FileList.IGNORED -> api.getIgnoredFiles(project.projectId())
    }
  }

  private fun unresolvedPath(path: Path, isDirectory: Boolean = false): FilePath =
    object : LocalFilePath(path.invariantSeparatorsPathString, isDirectory) {
      override fun findFile(path: String): VirtualFile = error("The conversion must not resolve a virtual file: $path")
    }

  private fun assertDto(path: FilePath, dto: FilePathDto, virtualFileId: VirtualFileId?) {
    assertSame(path, dto.filePath)
    assertEquals(FilePathDto(virtualFileId, path.path, path.isDirectory, path), dto)
    val serialized = Json.encodeToString(FilePathDto.serializer(), dto)
    val deserialized = Json.decodeFromString(FilePathDto.serializer(), serialized)
    val deserializedId = virtualFileId?.let {
      Json.decodeFromString(VirtualFileId.serializer(), Json.encodeToString(VirtualFileId.serializer(), it))
    }
    assertEquals(FilePathDto(deserializedId, path.path, path.isDirectory), deserialized)
  }

  private enum class FileList { UNVERSIONED, IGNORED }

  private class TestChangeListManager : MockChangeListManager() {
    private var unversionedPaths: List<FilePath> = emptyList()
    private var ignoredPaths: List<FilePath> = emptyList()
    var listener: ChangeListListener? = null
    @Volatile
    var unversionedReads: Int = 0
      private set

    fun setPaths(list: FileList, paths: List<FilePath>) {
      when (list) {
        FileList.UNVERSIONED -> unversionedPaths = paths
        FileList.IGNORED -> ignoredPaths = paths
      }
    }

    override fun getUnversionedFilesPaths(): List<FilePath> {
      unversionedReads++
      return unversionedPaths
    }

    override fun getIgnoredFilePaths(): List<FilePath> = ignoredPaths

    override fun addChangeListListener(listener: ChangeListListener, disposable: Disposable) {
      this.listener = listener
      Disposer.register(disposable) { this.listener = null }
    }
  }
}
