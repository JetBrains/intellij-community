// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.ProjectBulkFileListener
import com.intellij.openapi.vfs.ProjectBulkFileListenerRegistry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.useProject
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

@StressTestApplication
@PerformanceUnitTest
internal class ProjectBulkFileListenerPerformanceTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @Test
  fun `one project expands a large create plan and shares the after list`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot(projectModel.project, projectModel.createModule())
    root.children
    val createdPath = createTreeOnDisk(root)

    val firstAfter = AtomicReference<List<VFileEvent>>()
    val secondAfter = AtomicReference<List<VFileEvent>>()
    val afterCallbacks = CountDownLatch(2)
    registerCapturingListener(projectModel.project, disposable, firstAfter, afterCallbacks)
    registerCapturingListener(projectModel.project, disposable, secondAfter, afterCallbacks)

    val recordsBefore = FSRecords.getInstance().connection().records().maxAllocatedID()
    Benchmark.newBenchmark("ProjectBulkFileListener one-project refresh of $NODE_COUNT files") {
      root.refresh(false, true)
    }.warmupIterations(0).attempts(1).start()
    assertTrue(afterCallbacks.await(120, TimeUnit.SECONDS))

    val afterEvents = checkNotNull(firstAfter.get())
    assertEquals(EXPECTED_EVENT_COUNT, afterEvents.size)
    assertSame(afterEvents, secondAfter.get())
    assertTrue(FSRecords.getInstance().connection().records().maxAllocatedID() >= recordsBefore)
    assertEquals(NODE_COUNT, checkNotNull(VfsUtil.findFile(createdPath, false)).children.size)
  }

  @Test
  fun `one project expands a large delete plan and shares the after list`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot(projectModel.project, projectModel.createModule())
    val deletedDirectory = createCachedTree(root, "deleted")
    val listeners = captureListeners(projectModel.project, disposable)

    Benchmark.newBenchmark("ProjectBulkFileListener one-project delete of $NODE_COUNT files") {
      runWriteActionAndWait {
        deletedDirectory.delete(this)
      }
    }.warmupIterations(0).attempts(1).start()
    assertTrue(listeners.afterCallbacks.await(120, TimeUnit.SECONDS))

    val afterEvents = checkNotNull(listeners.firstAfter.get())
    assertEquals(EXPECTED_EVENT_COUNT, afterEvents.size)
    assertTrue(afterEvents.all { it is VFileDeleteEvent })
    assertSame(afterEvents, listeners.secondAfter.get())
  }

  @Test
  fun `one project expands a large move plan and shares the after list`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot(projectModel.project, projectModel.createModule())
    val movedDirectory = createCachedTree(root, "moved")
    lateinit var destination: VirtualFile
    runWriteActionAndWait {
      destination = root.createChildDirectory(this, "destination")
    }
    val listeners = captureListeners(projectModel.project, disposable)

    Benchmark.newBenchmark("ProjectBulkFileListener one-project move of $NODE_COUNT files") {
      runWriteActionAndWait {
        movedDirectory.move(this, destination)
      }
    }.warmupIterations(0).attempts(1).start()
    assertTrue(listeners.afterCallbacks.await(120, TimeUnit.SECONDS))

    val afterEvents = checkNotNull(listeners.firstAfter.get())
    assertEquals(EXPECTED_EVENT_COUNT, afterEvents.size)
    assertTrue(afterEvents.first() is VFileMoveEvent)
    assertTrue(afterEvents.drop(1).all { it is VFileCreateEvent })
    assertSame(afterEvents, listeners.secondAfter.get())
  }

  @Test
  fun `one project expands a large copy plan and shares the after list`(@TestDisposable disposable: Disposable) {
    val root = addContentRoot(projectModel.project, projectModel.createModule())
    val sourceRoot = projectModel.baseProjectDir.newVirtualDirectory("source")
    val sourceDirectory = createCachedTree(sourceRoot, "source-tree")
    val listeners = captureListeners(projectModel.project, disposable)

    Benchmark.newBenchmark("ProjectBulkFileListener one-project copy of $NODE_COUNT files") {
      runWriteActionAndWait {
        (PersistentFS.getInstance() as PersistentFSImpl).copyFile(this, sourceDirectory, root, "copied")
      }
    }.warmupIterations(0).attempts(1).start()
    assertTrue(listeners.afterCallbacks.await(120, TimeUnit.SECONDS))

    val afterEvents = checkNotNull(listeners.firstAfter.get())
    assertEquals(EXPECTED_EVENT_COUNT, afterEvents.size)
    assertTrue(afterEvents.all { it is VFileCopyEvent })
    assertSame(afterEvents, listeners.secondAfter.get())
  }

  @Test
  fun `several projects receive one expanded list each`(@TestDisposable disposable: Disposable) {
    val sharedRootPath = projectModel.baseProjectDir.rootPath.resolve("shared-content")
    sharedRootPath.createDirectories()
    val sharedRoot = checkNotNull(VfsUtil.findFile(sharedRootPath, true))

    val firstProject = projectModel.project
    addContentRoot(firstProject, projectModel.createModule("first"), sharedRoot)

    val secondProjectDir = projectModel.baseProjectDir.rootPath.resolve("second-project")
    secondProjectDir.createDirectories()
    val secondProject = PlatformTestUtil.loadAndOpenProject(secondProjectDir, disposable)
    secondProject.useProject {
      val secondModule = runWriteActionAndWait {
        ModuleManager.getInstance(secondProject).newModule(
          secondProjectDir.resolve("second.iml").toString(),
          EmptyModuleType.EMPTY_MODULE,
        )
      }
      addContentRoot(secondProject, secondModule, sharedRoot)

      val thirdProjectDir = projectModel.baseProjectDir.rootPath.resolve("third-project")
      thirdProjectDir.createDirectories()
      val thirdProject = PlatformTestUtil.loadAndOpenProject(thirdProjectDir, disposable)
      thirdProject.useProject {
        val thirdModule = runWriteActionAndWait {
          ModuleManager.getInstance(thirdProject).newModule(
            thirdProjectDir.resolve("third.iml").toString(),
            EmptyModuleType.EMPTY_MODULE,
          )
        }
        addContentRoot(thirdProject, thirdModule, sharedRoot)
        // thirdProject stays open with the shared root and no listeners.

        sharedRoot.children
        createTreeOnDisk(sharedRoot)

        val participatingProjects = ConcurrentHashMap.newKeySet<Project>()
        val afterByProject = ConcurrentHashMap<Project, List<VFileEvent>>()
        val afterCallbacks = CountDownLatch(2)

        registerParticipatingListener(firstProject, disposable, participatingProjects, afterByProject, afterCallbacks)
        registerParticipatingListener(secondProject, disposable, participatingProjects, afterByProject, afterCallbacks)

        Benchmark.newBenchmark("ProjectBulkFileListener multi-project refresh of $NODE_COUNT files") {
          sharedRoot.refresh(false, true)
        }.warmupIterations(0).attempts(1).start()
        assertTrue(afterCallbacks.await(120, TimeUnit.SECONDS))

        assertEquals(2, participatingProjects.size)
        assertTrue(participatingProjects.contains(firstProject))
        assertTrue(participatingProjects.contains(secondProject))
        assertTrue(thirdProject.isOpen)
        for (project in participatingProjects) {
          val afterEvents = checkNotNull(afterByProject[project])
          assertEquals(EXPECTED_EVENT_COUNT, afterEvents.size)
        }
      }
    }
  }

  private fun registerCapturingListener(
    project: Project,
    disposable: Disposable,
    afterEvents: AtomicReference<List<VFileEvent>>,
    afterCallbacks: CountDownLatch,
  ) {
    ProjectBulkFileListenerRegistry.getInstance(project).addListener(object : ProjectBulkFileListener {
      override fun afterVfsChange(events: List<VFileEvent>, project: Project) {
        afterEvents.set(events)
        afterCallbacks.countDown()
      }
    }, disposable)
  }

  private fun captureListeners(project: Project, disposable: Disposable): CapturedListeners {
    val firstAfter = AtomicReference<List<VFileEvent>>()
    val secondAfter = AtomicReference<List<VFileEvent>>()
    val afterCallbacks = CountDownLatch(2)
    registerCapturingListener(project, disposable, firstAfter, afterCallbacks)
    registerCapturingListener(project, disposable, secondAfter, afterCallbacks)
    return CapturedListeners(firstAfter, secondAfter, afterCallbacks)
  }

  private fun registerParticipatingListener(
    project: Project,
    disposable: Disposable,
    participatingProjects: MutableSet<Project>,
    afterByProject: MutableMap<Project, List<VFileEvent>>,
    afterCallbacks: CountDownLatch,
  ) {
    ProjectBulkFileListenerRegistry.getInstance(project).addListener(object : ProjectBulkFileListener {
      override fun afterVfsChange(events: List<VFileEvent>, project: Project) {
        if (events.isNotEmpty()) {
          participatingProjects.add(project)
          afterByProject[project] = events
          afterCallbacks.countDown()
        }
      }
    }, disposable)
  }

  private fun addContentRoot(
    project: Project,
    module: Module,
    root: VirtualFile = projectModel.baseProjectDir.newVirtualDirectory("content-${project.locationHash}"),
  ): VirtualFile {
    ModuleRootModificationUtil.addContentRoot(module, root)
    return root
  }

  private fun createTreeOnDisk(root: VirtualFile): Path {
    return createTreeOnDisk(root, "created")
  }

  private fun createCachedTree(root: VirtualFile, directoryName: String): VirtualFile {
    createTreeOnDisk(root, directoryName)
    root.refresh(false, true)
    val directory = checkNotNull(root.findChild(directoryName)) {
      "$directoryName directory was not loaded into VFS"
    }
    assertEquals(NODE_COUNT, directory.children.size)
    return directory
  }

  private fun createTreeOnDisk(root: VirtualFile, directoryName: String): Path {
    val createdDirectoryPath = root.toNioPath().resolve(directoryName)
    createdDirectoryPath.createDirectories()
    repeat(NODE_COUNT) { index ->
      createdDirectoryPath.resolve("file-$index").createFile()
    }
    return createdDirectoryPath
  }

  private data class CapturedListeners(
    val firstAfter: AtomicReference<List<VFileEvent>>,
    val secondAfter: AtomicReference<List<VFileEvent>>,
    val afterCallbacks: CountDownLatch,
  )

  companion object {
    private const val NODE_COUNT = 100_000
    private const val EXPECTED_EVENT_COUNT = NODE_COUNT + 1
  }
}
