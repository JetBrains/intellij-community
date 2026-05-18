// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.internal.visitChildrenInVfsRecursively
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.AfterEventShouldBeFiredBeforeOtherListeners
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.ChildInfo
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.indexing.testEntities.ExcludedKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.ExcludedTestEntity
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveFileSetContributor
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
class OnlyIndexableFilesAreLoadedIntoVfsOnDirectoryCreationTest {
  @JvmField
  @RegisterExtension
  val rootDir: TempDirectoryExtension = TempDirectoryExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludedKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
  }

  companion object {
    private fun VFileCreateEvent.isUnder(path: String): Boolean = this.path == path || this.path.startsWith("$path/")

    /**
     * Refresh triggers listeners, which may load extra files into VFS.
     *
     * This method collects files loaded into VFS after refresh but before listeners are invoked.
     *
     * If asserting that some file is NOT loaded, it is better to call refresh, and [visitChildrenInVfsRecursively] as usual,
     * to catch, if any listeners have loaded the file into VFS.
     */
    suspend fun collectFilesLoadedIntoVfsBeforeListenersRuns(directoryToRefresh: VirtualFile, listenerDisposable: Disposable): List<VirtualFile> {
      val directoryToRefreshPath = directoryToRefresh.toNioPath().invariantSeparatorsPathString
      val listenerResult = CompletableDeferred<Result<List<VirtualFile>>>()
      VirtualFileManager.getInstance().addAsyncFileListenerBackgroundable(
        { events ->
          if (listenerResult.isCompleted || events.none { it is VFileCreateEvent && it.isUnder(directoryToRefreshPath) }) {
            return@addAsyncFileListenerBackgroundable null
          }
          object : AsyncFileListener.ChangeApplier, AfterEventShouldBeFiredBeforeOtherListeners {
            override fun afterVfsChange() {
              listenerResult.complete(runCatching {
                visitChildrenInVfsRecursively(directoryToRefresh).toList()
              })
            }
          }
        }, listenerDisposable)

      RefreshQueue.getInstance().refresh(true, listOf(directoryToRefresh))

      return withTimeout(10.seconds) {
        listenerResult.await()
      }.getOrThrow()
    }

    private suspend fun collectCreatedDirectoryChildrenBeforeListenersRuns(
      directoryToRefresh: VirtualFile,
      createdDirectory: Path,
      listenerDisposable: Disposable,
    ): Array<ChildInfo> {
      val createdDirectoryPath = createdDirectory.invariantSeparatorsPathString
      val listenerResult = CompletableDeferred<Result<Array<ChildInfo>>>()
      VirtualFileManager.getInstance().addAsyncFileListenerBackgroundable(
        { events ->
          if (listenerResult.isCompleted) {
            return@addAsyncFileListenerBackgroundable null
          }
          val createEvent = events.filterIsInstance<VFileCreateEvent>().singleOrNull { it.path == createdDirectoryPath }
                            ?: return@addAsyncFileListenerBackgroundable null
          object : AsyncFileListener.ChangeApplier, AfterEventShouldBeFiredBeforeOtherListeners {
            override fun afterVfsChange() {
              listenerResult.complete(runCatching {
                check(createEvent.isDirectory) { "Expected directory create event for $createdDirectoryPath" }
                checkNotNull(createEvent.children) { "No scanned children for $createdDirectoryPath" }
              })
            }
          }
        }, listenerDisposable)

      RefreshQueue.getInstance().refresh(true, listOf(directoryToRefresh))

      return withTimeout(10.seconds) {
        listenerResult.await()
      }.getOrThrow()
    }
  }

  private fun findVirtualFile(path: Path): VirtualFile {
    return checkNotNull(VfsTestUtil.findFileByCaseSensitivePath(path.pathString)) {
      "VirtualFile not found for path: $path"
    }
  }

  @Test
  fun `test non-indexable files are not loaded into VFS on creation`(): Unit = runBlocking {
    rootDir.newDirectoryPath(".idea") // forces project.basePath to match project root which may affect how VFS files are refreshed
    val options = OpenProjectTask { createModule = false }
    ProjectUtil.openOrImportAsync(rootDir.rootPath, options)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later
      Assertions.assertFalse(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile) })

      rootDir.newDirectoryPath("d1/d2/d3")
      delay(1.seconds) // wait for fs events to arrive
      RefreshQueue.getInstance().refresh(true, listOf(rootVirtualFile))
      val filesInVfs = visitChildrenInVfsRecursively(rootVirtualFile).toList()
      assertThat(filesInVfs.map { it.name }).containsExactlyInAnyOrder(rootVirtualFile.name, ".idea", "d1")
    }
  }

  @Test
  fun `test indexable by non-recursive files are not loaded into VFS on creation`(): Unit = runBlocking {
    rootDir.newDirectoryPath(".idea") // forces project.basePath to match project root which may affect how VFS files are refreshed
    val options = OpenProjectTask { createModule = false }
    ProjectUtil.openOrImportAsync(rootDir.rootPath, options)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later
      Assertions.assertFalse(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile) })

      project.workspaceModel.update("Add indexable non-recursive root") {
        it.addEntity(NonRecursiveTestEntity(rootVirtualFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager()), NonPersistentEntitySource))
      }

      Assertions.assertTrue(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile) })

      rootDir.newDirectoryPath("d1/d2/d3")
      delay(1.seconds) // wait for fs events to arrive
      RefreshQueue.getInstance().refresh(true, listOf(rootVirtualFile))
      val filesInVfs = visitChildrenInVfsRecursively(rootVirtualFile).toList()
      assertThat(filesInVfs.map { it.name }).containsExactlyInAnyOrder(rootVirtualFile.name, ".idea", "d1")
    }
  }

  @Test
  fun `test content root under content root is loaded into VFS on creation`(): Unit = runBlocking {
    rootDir.newDirectoryPath(".idea") // forces project.basePath to match project root which may affect how VFS files are refreshed
    val innerContentRoot = rootDir.rootPath.resolve("innerContentRoot")
    val options = OpenProjectTask { createModule = false }
    ProjectUtil.openOrImportAsync(rootDir.rootPath, options)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later

      val urlManager = project.workspaceModel.getVirtualFileUrlManager()
      project.workspaceModel.update("Add nested content roots") {
        it.addEntity(ModuleEntity("testModule", listOf(InheritedSdkDependency, ModuleSourceDependency), NonPersistentEntitySource) {
          contentRoots = listOf(
            ContentRootEntity(rootVirtualFile.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource),
            ContentRootEntity(innerContentRoot.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource),
          )
        })
      }

      val outerFile = rootDir.newFileNio("outerFile.txt")
      outerFile.writeText("outer")
      val innerFile = rootDir.newFileNio("innerContentRoot/innerFile.txt")
      innerFile.writeText("inner")
      delay(1.seconds) // wait for fs events to arrive

      val filesInVfs = collectFilesLoadedIntoVfsBeforeListenersRuns(rootVirtualFile, disposable)
      assertThat(filesInVfs.map { it.path }).contains(outerFile.invariantSeparatorsPathString, innerFile.invariantSeparatorsPathString)
    }
  }

  @Test
  fun `test that VFS loading stops at excluded dir registered via an entity`(): Unit = runBlocking {
    rootDir.newFileNio("pom.xml").writeText(pom)

    ProjectUtil.openOrImportAsync(rootDir.rootPath)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project, 1.minutes)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later
      Assertions.assertTrue(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile) })

      val excludedUrl = rootDir.rootPath.resolve("d1/excluded").toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
      project.workspaceModel.update("Add excluded dir $excludedUrl") {
        it.addEntity(ExcludedTestEntity(excludedUrl, NonPersistentEntitySource))
      }

      // trigger WorkspaceFileIndex in the "before" event (IJPL-228634)
      VirtualFileManager.getInstance().addAsyncFileListenerBackgroundable({
        object : AsyncFileListener.ChangeApplier {
          override fun beforeVfsChange() {
            WorkspaceFileIndex.getInstance(project).isIndexable(rootVirtualFile)
          }
        }
      }, disposable)

      rootDir.newDirectoryPath("d1/excluded/d3")
      delay(1.seconds) // wait for fs events to arrive
      RefreshQueue.getInstance().refresh(true, listOf(rootVirtualFile))
      val filesInVfs = visitChildrenInVfsRecursively(rootVirtualFile).toList()
      assertThat(filesInVfs).noneMatch { it.name == "d3" }
    }
  }

  @Test
  fun `test partial excluded subtree does not hide skipped siblings`(): Unit = runBlocking {
    rootDir.newDirectoryPath(".idea")
    val options = OpenProjectTask { createModule = false }
    ProjectUtil.openOrImportAsync(rootDir.rootPath, options)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later

      val contentRoot = rootDir.rootPath.resolve("content")
      val excludedRoot = contentRoot.resolve("build")
      val nestedContentRoot = excludedRoot.resolve("generated/source/kapt/main")
      val urlManager = project.workspaceModel.getVirtualFileUrlManager()
      project.workspaceModel.update("Add content root under excluded dir") {
        it.addEntity(ModuleEntity("testModule", listOf(InheritedSdkDependency, ModuleSourceDependency), NonPersistentEntitySource) {
          contentRoots = listOf(
            ContentRootEntity(contentRoot.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource) {
              excludedUrls = listOf(ExcludeUrlEntity(excludedRoot.toVirtualFileUrl(urlManager), NonPersistentEntitySource))
            },
            ContentRootEntity(nestedContentRoot.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource),
          )
        })
      }

      val nestedFile = rootDir.newFileNio("content/build/generated/source/kapt/main/Generated.kt")
      nestedFile.writeText("class Generated")
      val skippedFile = rootDir.newFileNio("content/build/tmp/marker.txt")
      skippedFile.writeText("marker")
      delay(1.seconds) // wait for fs events to arrive

      val filesLoadedIntoVfs = collectFilesLoadedIntoVfsBeforeListenersRuns(rootVirtualFile, disposable)
      val filesLoadedByPath = filesLoadedIntoVfs.associateBy { it.toNioPath().invariantSeparatorsPathString }
      assertThat(filesLoadedByPath.keys).contains(nestedContentRoot.invariantSeparatorsPathString)
      val excludedDirectory = filesLoadedByPath.getValue(excludedRoot.invariantSeparatorsPathString) as NewVirtualFile
      assertThat(excludedDirectory.allChildrenLoaded()).isFalse()
      val generatedDirectory = filesLoadedByPath.getValue(excludedRoot.resolve("generated").invariantSeparatorsPathString) as NewVirtualFile
      assertThat(generatedDirectory.allChildrenLoaded()).isFalse()
      assertThat(filesLoadedByPath).doesNotContainKey(skippedFile.invariantSeparatorsPathString)

      val nestedVirtualFile = rootVirtualFile.findFileByRelativePath("content/build/generated/source/kapt/main/Generated.kt")
      assertThat(nestedVirtualFile).isNotNull
      assertThat(nestedVirtualFile!!.toNioPath().invariantSeparatorsPathString).isEqualTo(nestedFile.invariantSeparatorsPathString)

      val skippedVirtualFile = rootVirtualFile.findFileByRelativePath("content/build/tmp/marker.txt")
      assertThat(skippedVirtualFile).isNotNull
      assertThat(skippedVirtualFile!!.toNioPath().invariantSeparatorsPathString).isEqualTo(skippedFile.invariantSeparatorsPathString)
    }
  }

  @Test
  fun `test scanChildren keeps content root under nested excluded directory`(): Unit = runBlocking {
    rootDir.newDirectoryPath(".idea")
    val scanRoot = rootDir.rootPath.resolve("content")
    val options = OpenProjectTask { createModule = false }
    ProjectUtil.openOrImportAsync(rootDir.rootPath, options)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val rootVirtualFile = findVirtualFile(rootDir.rootPath)
      rootVirtualFile.children // load all children to trigger full sync later

      val excludedRoot = scanRoot.resolve("build")
      val nestedContentRoot = excludedRoot.resolve("generated/source/kapt/main")
      val urlManager = project.workspaceModel.getVirtualFileUrlManager()
      val excludedRootUrl = excludedRoot.toVirtualFileUrl(urlManager)
      project.workspaceModel.update("Add content root under $excludedRootUrl") {
        it.addEntity(IndexingTestEntity(
          listOf(scanRoot.toVirtualFileUrl(urlManager), nestedContentRoot.toVirtualFileUrl(urlManager)),
          listOf(excludedRootUrl),
          NonPersistentEntitySource,
        ))
      }

      rootDir.newFileNio("content/build/generated/source/kapt/main/Generated.kt").writeText("class Generated")
      rootDir.newFileNio("content/build/tmp/marker.txt").writeText("marker")
      delay(1.seconds) // wait for fs events to arrive

      val scannedChildren = collectCreatedDirectoryChildrenBeforeListenersRuns(rootVirtualFile, scanRoot, disposable)
      val excludedInfo = childInfo(scannedChildren, "build")
      assertThat(excludedInfo.isAllChildren).isFalse()
      assertThat(childNames(excludedInfo.children)).containsExactly("generated")
      val generatedInfo = childInfo(excludedInfo.children, "generated")
      val sourceInfo = childInfo(generatedInfo.children, "source")
      val kaptInfo = childInfo(sourceInfo.children, "kapt")
      val mainInfo = childInfo(kaptInfo.children, "main")
      assertThat(mainInfo.isAllChildren).isTrue()
      assertThat(childNames(mainInfo.children)).containsExactly("Generated.kt")
    }
  }

  private fun childNames(children: Array<ChildInfo>?): List<String> = children?.map { it.name.toString() } ?: emptyList()

  private fun childInfo(children: Array<ChildInfo>?, name: String): ChildInfo =
    children?.singleOrNull { it.name.toString() == name } ?: error("$name wasn't loaded")

  private val pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                    "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                    "    <modelVersion>4.0.0</modelVersion>\n" +
                    "\n" +
                    "    <groupId>org.example</groupId>\n" +
                    "    <artifactId>main</artifactId>\n" +
                    "    <version>1.0-SNAPSHOT</version>\n" +
                    "</project>"
}
